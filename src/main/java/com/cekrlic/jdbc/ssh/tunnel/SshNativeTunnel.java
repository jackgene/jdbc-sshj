package com.cekrlic.jdbc.ssh.tunnel;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author boky
 * @created 11/02/2017 17:50
 */
public class SshNativeTunnel extends AbstractTunnel {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SshNativeTunnel.class);

	public static final String LOGIN_MESSAGE = "============ " + SshNativeTunnel.class.getName() + " login completed. ============";
	private final String arguments;
	private final Map<String, String> queryParameters;
	private Thread runnable;
	private IOException ioe = null;
	private boolean started = false;
	private boolean stopped = false;
	private final Object mutex = new Object();


	public SshNativeTunnel(String url) throws IOException {
		url = url.replaceFirst("^" + SshJNativeDriver.DRIVER_PREFIX + "//", ""); // get rid of "jdbc:sshj-native://'"
		int questionMark = url.lastIndexOf("?");

		if (questionMark > 0) {
			arguments = url.substring(0, questionMark);
			queryParameters = splitQuery(url.substring(questionMark + 1));
		} else {
			arguments = url;
			queryParameters = new HashMap<>();
		}

		loadDrivers(queryParameters);
		logger.info("Automatic local port assignment starts at: {}:{}", localHost, localPort.get());

	}

	@Override
	public void start() throws SQLException {
		determineLocalPort();
		final int localPort = this.localPort.get();

		List<String> commandList = new ArrayList<>();
		commandList.add("ssh");

		Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(arguments);
		while (m.find()) {
			commandList.add(m.group(1).replace("(^\"|$\")", ""));
		}
		commandList.add("-t");
		commandList.add("-t"); // not an error, multiple invocations force the allocation of pseudo terminal.
		commandList.add("-L");
		commandList.add(localHost + ":" + localPort + ":" + queryParameters.get(REMOTE));
		commandList.add("echo \"" + LOGIN_MESSAGE + "\"; ping -i 10 127.0.0.1");

		final ProcessBuilder pb = new ProcessBuilder(commandList);
		final Map<String, String> environment = pb.environment();

		// Attach locale-specific variables to environment, if found in System.properties
		// This is basically needed to make our test cases work, as Apache's SSHD implementation
		// ignores opcode 42 (IUTF8) and stops further processing. Hence we switch back to
		// ISO-8895-1 in our tests.
		@SuppressWarnings("unchecked")
		final Map<String, String> properties = (Map) System.getProperties();
		for(final Map.Entry<String, String> e: properties.entrySet()) {
			if(e.getKey().startsWith("LC_") || e.getKey().startsWith("LANG")) {
				environment.put(e.getKey(), e.getValue());
			}
		}

		logger.info("Executing: {}", pb.command());
		logger.debug("Environment: {}", environment);

		final boolean debug = logger.isDebugEnabled();

		runnable = new Thread(new Runnable() {
			@Override
			public void run() {
				started = false;
				stopped = false;
				final Process p;
				try {
					p = pb.start();
				} catch (IOException e) {
					synchronized (mutex) {
						ioe = e;
						mutex.notify();
					}
					return;
				}

				final Thread errorStream = new Thread(new Runnable() {
					@Override
					public void run() {
						final InputStream is = p.getErrorStream();
						final Reader r = new InputStreamReader(is);
						final BufferedReader br = new BufferedReader(r);
						String line;
						try {
							line = br.readLine();
							while(line != null) {
								logger.error(line);
								if(Thread.currentThread().isInterrupted()) {
									break;
								}
								line = br.readLine();
							}
						} catch (IOException ioe) {
							logger.error("Could not read error stream!", ioe);
						}
					}
				});
				errorStream.setPriority(Thread.MIN_PRIORITY);
				errorStream.setDaemon(true);
				errorStream.start();

				final InputStream is = p.getInputStream();
				final Reader r = new InputStreamReader(is);
				final BufferedReader br = new BufferedReader(r);
				String line;
				try {
					line = br.readLine();
					while(line != null) {
						if(debug) {
							logger.debug(line);
						}

						if(LOGIN_MESSAGE.equals(line)) {
							synchronized (mutex) {
								started = true;
								ioe = null;
								mutex.notify();
							}
						}
						if(Thread.currentThread().isInterrupted()) {
							errorStream.interrupt();
							p.destroy();
							synchronized (mutex) {
								started = false;
								stopped = true;
								mutex.notify();
							}
						}
						line = br.readLine();
					}
				} catch (IOException e) {
					synchronized (mutex) {
						started = false;
						stopped = true;
						ioe = e;
						p.destroy();
						mutex.notify();
					}
				}

			}
		});

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				SshNativeTunnel.this.stop("Shutdown hook.");
			}
		});

		runnable.setPriority(Thread.MIN_PRIORITY);
		runnable.setDaemon(true);
		runnable.start();

		ensureStarted();
	}

	public void ensureStarted() throws SQLException {
		try {
			while(!started || stopped || ioe != null) {
				synchronized (mutex) {
					mutex.wait(1000);
				}
			}
		} catch (InterruptedException e) {
			throw new SQLException(e);
		}

		if(ioe!=null) {
			throw new SQLException(ioe);
		}

		if(stopped) {
			throw new SQLException("Service is stopped.");
		}
	}

	@Override
	boolean isStopped() {
		return stopped;
	}

	@Override
	public void stop(String reason) {
		if(runnable!=null) {
			runnable.interrupt();
			runnable = null;
			logger.info("Shutting down tunnel {}:{} due to: {}", localHost, localPort, reason);
		}
	}
}
