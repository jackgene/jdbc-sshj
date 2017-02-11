package com.cekrlic.jdbc.ssh.tunnel;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider;
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile;
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile;
import net.schmizz.sshj.userauth.method.AuthMethod;
import net.schmizz.sshj.userauth.method.AuthPassword;
import net.schmizz.sshj.userauth.method.AuthPublickey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SshTunnel {
	private static final Logger logger = LoggerFactory.getLogger(SshTunnel.class);
	static final String USERNAME = "username";
	static final String PASSWORD = "password";
	static final String PRIVATE_KEY = "private.key.file";
	static final String PRIVATE_KEY_PASSWORD = "private.key.password";
	static final String PRIVATE_KEY_FILE_FORMAT = "private.key.file.format";
	static final String VERIFY_HOSTS = "verify_hosts";
	static final String REMOTE = "remote";

	private final URI sshUrl;
	private final Map<String, String> queryParameters;
	private final String host;
	private final int port;

	private SSHClient client = null;
	private ServerSocket ss = null;

	private enum KeyFileFormat {
		PUTTY,
		OPENSSH;
	}

	private Thread runnable;
	private final Object mutex = new Object();
	IOException ioe = null;

	private AtomicInteger localPort = new AtomicInteger(20000 + new java.util.Random().nextInt(100));
	private String localHost;

	public SshTunnel(String sshUrl) throws SQLException {
		// Determine if it's mac:
		String os = System.getProperty("os.name").toUpperCase();
		if(os.contains("OS X") || os.contains("MACOS") || os.contains("MAC ")) {
			localHost = "127.0.0.1";
		} else {
			localHost = "127.0.1." + (2 + new java.util.Random().nextInt(201));
		}

		try {
			this.sshUrl = new URI(sshUrl.replaceFirst("jdbc:", ""));
			this.queryParameters = splitQuery(this.sshUrl);
			this.host = this.sshUrl.getHost();
			this.port = this.sshUrl.getPort();
		} catch (UnsupportedEncodingException | URISyntaxException e) {
			throw new SQLException(e);
		}

		logger.info("Automatic local port assignment starts at: {}:{}", localHost, localPort.get());

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				logger.info("Shutting down tunnel...");
				SshTunnel.this.stop();
			}
		});
	}

	private static Map<String, String> splitQuery(URI url) throws UnsupportedEncodingException {
		Map<String, String> query_pairs = new LinkedHashMap<>();
		String query = url.getQuery();
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			if (idx > 0) {
				query_pairs.put(
						URLDecoder.decode(pair.substring(0, idx), "UTF-8").toLowerCase(),
						URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
				);
			} else {
				query_pairs.put(URLDecoder.decode(pair, "UTF-8"), null);
			}
		}
		return query_pairs;
	}


	public void start() throws SQLException {
		try {
			client = new SSHClient();
			client.loadKnownHosts();

			boolean verifyHosts = true;
			if(queryParameters.containsKey(VERIFY_HOSTS)) {
				String key = queryParameters.get(VERIFY_HOSTS).toLowerCase();
				if("false".equals(key) || "0".equals(key) || "off".equals(key)) {
					verifyHosts = false;
				}

				if(!verifyHosts) {
					client.addHostKeyVerifier(new PromiscuousVerifier());
				}
			}

			if (this.port > 0) {
				try {
					client.connect(this.host, this.port);
				} catch (java.net.ConnectException e) {
					throw new IOException("Could not connect to SSH on " + this.host + ":" + this.port + "!", e);
				}
			} else {
				try {
					client.connect(this.host);
				} catch (IOException e) {
					throw new IOException("Could not connect to SSH on " + this.host + ":22 (default port)!", e);
				}
			}

			final List<AuthMethod> methods = new ArrayList<>();

			// Add password authentication method
			if (queryParameters.containsKey(PASSWORD)) {
				methods.add(new AuthPassword(new PlainPasswordFinder(queryParameters.get(PASSWORD))));
			}

			// Private key authentication
			if (queryParameters.containsKey(PRIVATE_KEY) && queryParameters.get(PRIVATE_KEY).length() > 0) {
				String keyFile = queryParameters.get(PRIVATE_KEY);
				String keyPassword = queryParameters.get(PRIVATE_KEY_PASSWORD);
				KeyFileFormat keyFileFormat;
				if (queryParameters.get(PRIVATE_KEY_FILE_FORMAT) == null || queryParameters.get(PRIVATE_KEY_FILE_FORMAT).length() == 0) {
					if (keyFile.toLowerCase().endsWith(".ppk")) {
						keyFileFormat = KeyFileFormat.PUTTY;
					} else {
						keyFileFormat = KeyFileFormat.OPENSSH;
					}
				} else {
					keyFileFormat = KeyFileFormat.valueOf(queryParameters.get(PRIVATE_KEY_FILE_FORMAT).toUpperCase().trim());
				}

				FileKeyProvider o;
				if (keyFileFormat == KeyFileFormat.PUTTY) {
					o = new PuTTYKeyFile();
				} else {
					o = new OpenSSHKeyFile();
				}

				if (keyPassword != null) {
					o.init(new File(keyFile), new PlainPasswordFinder(keyPassword));
				} else {
					o.init(new File(keyFile));
				}
				methods.add(new AuthPublickey(o));
			}

			client.auth(queryParameters.get(USERNAME), methods);
			client.getConnection().getKeepAlive().setKeepAliveInterval(30);

			int nextPort = localPort.incrementAndGet();

			// NOTE: scan max next 10 ports
			for (int i = 0; i < 10; i++) {
				if (isPortOpen(localHost, nextPort)) {
					break;
				}

				nextPort = localPort.incrementAndGet();
			}

			final LocalPortForwarder.Parameters params;
			final String[] remotes = queryParameters.get(REMOTE).split(":", 2);

			params = new LocalPortForwarder.Parameters(localHost, nextPort, remotes[0], Integer.parseInt(remotes[1]));
			logger.debug("Forwarding {}:{} to {}:{}", localHost, nextPort, remotes[0], remotes[1]);

			ss = new ServerSocket();
			ss.setReuseAddress(true);
			try {
				ss.bind(new InetSocketAddress(params.getLocalHost(), params.getLocalPort()));
			} catch (BindException e) {
				throw new IOException("Binding to " + params.getLocalHost() + ":" + params.getLocalPort() + " failed!");
			}

			final LocalPortForwarder lpf = client.newLocalPortForwarder(params, ss);

			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						lpf.listen();
					} catch (IOException e) {
						ioe = e;
						synchronized (mutex) {
							mutex.notify();
						}
					}
				}
			});
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);

			t.start();
			synchronized (mutex) {
				mutex.wait(1000);
			}
			if(ioe != null) {
				throw ioe;
			}

			logger.info("Listening for remote connections.");

		} catch (Exception e) {
			logger.error(e.toString(), e);
			throw new SQLException(e);
		}
	}

	private void stop() {
		if(ss != null) {
			try {
				ss.close();
			} catch (IOException e) {
				logger.error("Failed to close socket " + this.ss, e);
			} finally {
				ss = null;
			}
		}

		if (client != null) {
			try {
				client.disconnect();

				if (logger.isDebugEnabled()) {
					logger.debug("Disconnected.");
				}
			} catch (IOException e) {
				logger.error("Failed to disconnect from " + this.host);
			} finally {
				ss = null;
			}
		}
	}

	public String getLocalPort() {
		return Integer.toString(localPort.get());
	}

	public String getLocalHost() {
		return localHost;
	}

	static private boolean isPortOpen(String ip, int port) {
		try {
			Socket socket = new Socket();
			socket.connect(new InetSocketAddress(ip, port), 1000);
			socket.close();
			return false;
		} catch (Exception ex) {
			return true;
		}
	}
}
