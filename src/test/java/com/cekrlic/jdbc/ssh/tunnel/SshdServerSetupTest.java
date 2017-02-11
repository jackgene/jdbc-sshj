package com.cekrlic.jdbc.ssh.tunnel;

import com.m11n.jdbc.ssh.util.BogusPasswordAuthenticator;
import com.m11n.jdbc.ssh.util.TestCachingPublicKeyAuthenticator;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.ForwardingFilter;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.SshdSocketAddress;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import static com.cekrlic.jdbc.ssh.tunnel.SshNativeTunnel.LOGIN_MESSAGE;

/**
 * @author boky
 * @created 11/02/2017 15:00
 */
@Test
public class SshdServerSetupTest {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SshdServerSetupTest.class);

	private static SshServer sshd;
	static String sshHost;
	static int sshPort;

	@BeforeSuite
	protected static void setUpSshd() throws Exception {
		if (sshd == null) {

			final Properties p = new Properties();
			p.load(SshJDriver.class.getClassLoader().getResourceAsStream("ssh.properties"));
			sshPort = Integer.valueOf(p.getProperty("jdbc.ssh.port"));
			sshHost = p.getProperty("jdbc.ssh.host");

			sshd = SshServer.setUpDefaultServer();
			sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("target/hostkey.rsa", "RSA"));
			sshd.setPasswordAuthenticator(new BogusPasswordAuthenticator());
			sshd.setPublickeyAuthenticator(new TestCachingPublicKeyAuthenticator());
			sshd.setCommandFactory(new CommandFactory() {
				public Command createCommand(String command) {
					return new DemoCommand(command);
				}
			});
			sshd.setHost(sshHost);
			sshd.setPort(sshPort);
			sshd.setTcpipForwardingFilter(new ForwardingFilter() {
				@Override
				public boolean canForwardAgent(Session session) {
					return true;
				}

				@Override
				public boolean canForwardX11(Session session) {
					return true;
				}

				@Override
				public boolean canListen(SshdSocketAddress address, Session session) {
					return true;
				}

				@Override
				public boolean canConnect(SshdSocketAddress address, Session session) {
					return true;
				}
			});

			sshd.start();
			logger.info("Started SSH server on port {}:{}", sshHost, sshPort);
		}
	}

	@AfterSuite
	protected static void shutDownSshd() throws Exception {
		if (sshd != null && !sshd.isClosed()) {
			sshd.stop();
		}
	}

	private static class DemoCommand implements Command {
		private String command;
		private InputStream in;
		private OutputStream out;
		private OutputStream err;
		private ExitCallback callback;

		public DemoCommand(String command) {
			this.command = command;
		}

		public void setInputStream(InputStream in) {
			this.in = in;
		}

		public void setOutputStream(OutputStream out) {
			this.out = out;
		}

		public void setErrorStream(OutputStream err) {
			this.err = err;
		}

		public void setExitCallback(ExitCallback callback) {
			this.callback = callback;
		}

		public void start(Environment env) throws IOException {
			out.write(LOGIN_MESSAGE.getBytes());
			out.flush();

			err.write(("Unknown command: " + command + "\n").getBytes());
			err.flush();
			if (callback != null) {
				callback.onExit(1, "Unknown command: " + command);
			}
		}

		public void destroy() {
		}
	}
}
