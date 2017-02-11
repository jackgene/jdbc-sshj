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


		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				logger.info("Shutting down tunnel {}:{} for ", localHost, localPort.get(), arguments);
				SshNativeTunnel.this.stop();
			}
		});
	}

	@Override
	public void start() throws SQLException {
		determineLocalPort();
		int localPort = this.localPort.get();

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
		logger.info("Executing: {}", pb.command());

		final boolean debug = logger.isDebugEnabled();

		runnable = new Thread(new Runnable() {
			@Override
			public void run() {
				started = false;
				Process p;
				try {
					p = pb.start();
				} catch (IOException e) {
					synchronized (mutex) {
						ioe = e;
						mutex.notify();
					}
					return;
				}
				InputStream is = p.getInputStream();
				Reader r = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(r);
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
							p.destroy();
							synchronized (mutex) {
								started = false;
								mutex.notify();
							}
						}
						line = br.readLine();
					}
				} catch (IOException e) {
					synchronized (mutex) {
						started = false;
						ioe = e;
						p.destroy();
						mutex.notify();
					}
				}

			}
		});
		runnable.setPriority(Thread.MIN_PRIORITY);
		runnable.setDaemon(true);
		runnable.start();

		if(ioe!=null) {
			throw new SQLException(ioe);
		}

		if(started) {
			return;
		}

		try {
			synchronized (mutex) {
				mutex.wait(10000);
			}
		} catch (InterruptedException e) {
			throw new SQLException(e);
		}

	}

	@Override
	public void stop() {
		if(runnable!=null) {
			runnable.interrupt();
			runnable = null;
		}
	}
}
