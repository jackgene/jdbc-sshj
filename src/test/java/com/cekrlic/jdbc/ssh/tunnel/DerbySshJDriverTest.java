package com.cekrlic.jdbc.ssh.tunnel;

import com.m11n.jdbc.ssh.util.Slf4jDerbyBridge;
import com.m11n.jdbc.ssh.util.Slf4jOutputStream;
import org.apache.derby.drda.NetworkServerControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.io.PrintWriter;
import java.net.InetAddress;

public class DerbySshJDriverTest extends JdbcSshDriverTest {
	private static final Logger logger = LoggerFactory.getLogger(DerbySshJDriverTest.class);

	private NetworkServerControl dbServerDerby;

	@BeforeClass
	public void init() throws Exception {
		startupDerby();
		setUp();
	}

	@AfterClass
	public void cleanup() throws Exception {
		dbServerDerby.shutdown();
	}

	private void startupDerby() throws Exception {
		System.setProperty("derby.drda.startNetworkServer", "true");

		Slf4jDerbyBridge.setLogger(logger);
		System.setProperty("derby.stream.error.method", Slf4jDerbyBridge.class.getName() + ".bridge");

		if (logger.isTraceEnabled()) {
			// see here for more options: http://wiki.apache.org/db-derby/DebugPropertiesTmpl
			System.setProperty("derby.drda.logConnections", "true");
			System.setProperty("derby.language.logStatementText", "true");
			System.setProperty("derby.language.logQueryPlan", "true");
			System.setProperty("derby.locks.deadlockTrace", "true");
		}

		dbServerDerby = new NetworkServerControl(InetAddress.getByName("localhost"), 1527);
		dbServerDerby.start(new PrintWriter(new Slf4jOutputStream(logger), true));

		for (int i = 0; i < 10; ++i) {
			try {
				logger.info("Attempting to ping...");
				dbServerDerby.ping();
				break;
			} catch (Exception e) {
				logger.warn(e.getMessage());
			}
			Thread.sleep(10);
		}

		logger.info("JDBC Runtime Info:\n{}", dbServerDerby.getRuntimeInfo());
	}

	public void setUp() throws Exception {
		sshUrl = System.getProperty("url") != null ? System.getProperty("url") : "jdbc:sshj://" + SshdServerSetupTest.sshHost + ":" + SshdServerSetupTest.sshPort + "?remote=127.0.0.1:1527&username=test&password=test&verify_hosts=off;;;jdbc:derby://{{host}}:{{port}}/target/test;create=true";
		realUrl = System.getProperty("realUrl") != null ? System.getProperty("realUrl") : "jdbc:derby://127.0.0.1:1527/target/test;create=true";

		sql = "SELECT 1 FROM SYSIBM.SYSDUMMY1";

	}
}
