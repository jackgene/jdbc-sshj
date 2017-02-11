package com.cekrlic.jdbc.ssh.tunnel;

import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

public class H2SshJDriverTest extends JdbcSshDriverTest {
	private static final Logger logger = LoggerFactory.getLogger(H2SshJDriverTest.class);

	private Server dbServerH2;

	@BeforeClass
	public void init() throws Exception {
		startupH2();
		setUp();
	}

	@AfterClass
	public void cleanup() throws Exception {
		dbServerH2.shutdown();
	}

	public void setUp() throws Exception {
		final String db2Url = dbServerH2.getURL();
		final int portIdx = db2Url.lastIndexOf(":");
		String srv = db2Url.substring(0, portIdx); // tcp://a.b.c.d
		srv = srv.replaceFirst("^[a-z]+://", "");


		String port = db2Url.substring(portIdx + 1);


		sshUrl = "jdbc:sshj://" + SshdServerSetupTest.sshHost + ":" + SshdServerSetupTest.sshPort + "?remote=" + srv + ":" + port + "&username=test&password=test&verify_hosts=off;;;jdbc:h2:{{host}}:{{port}}/test";
		sshNativeUrl = "jdbc:sshj-native://" + SshdServerSetupTest.sshHost + " -o StrictHostKeyChecking=no -p " + SshdServerSetupTest.sshPort + "?remote=" + srv + ":" + port + "&username=test&password=test&verify_hosts=off;;;jdbc:h2:{{host}}:{{port}}/test";
		realUrl = "jdbc:h2:" + db2Url + "/test";

		sql = "SELECT 1";
	}

	private void startupH2() throws Exception {
		System.setProperty("h2.baseDir", "./target/h2");

		dbServerH2 = Server.createTcpServer("-tcpPort", "8092", "-tcpDaemon").start();
		logger.info("Database server status: u = {} - s = {} ({})", dbServerH2.getURL(), dbServerH2.getStatus(), dbServerH2.isRunning(true));
	}
}
