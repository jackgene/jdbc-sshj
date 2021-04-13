package com.cekrlic.jdbc.ssh.tunnel;

import static com.cekrlic.jdbc.ssh.tunnel.SshNativeTunnel.LOGIN_MESSAGE;
import static org.testng.Assert.assertTrue;

import com.m11n.jdbc.ssh.util.BogusPasswordAuthenticator;
import com.m11n.jdbc.ssh.util.Slf4jDerbyBridge;
import com.m11n.jdbc.ssh.util.Slf4jOutputStream;
import com.m11n.jdbc.ssh.util.TestCachingPublicKeyAuthenticator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Properties;
import org.apache.derby.drda.NetworkServerControl;
import org.apache.sshd.common.Factory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.forward.ForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class JdbcSshDriverTest {

  static final int DERBY_PORT = 31527;
  private static final Logger logger = LoggerFactory.getLogger(JdbcSshDriverTest.class);
  private static final String DERBY_SQL = "SELECT 1 FROM SYSIBM.SYSDUMMY1";
  private static final String H2_SQL = "SELECT 1";
  String sshHost;
  int sshPort;
  private NetworkServerControl dbServerDerby;
  private Server dbServerH2;
  private SshServer sshd;

  @BeforeSuite
  protected void setup() throws Exception {
    setUpSshd();
    startupDerby();
    startupH2();
  }

  @AfterSuite
  protected void teardown() throws Exception {
    shutdownH2();
    shutdownDerby();
    shutdownSshd();
  }

  private void setUpSshd() throws Exception {
    // Due to a bug in Apache SSHD, processing stops when using UTF-8: https://issues.apache.org/jira/browse/SSHD-679
    // So we reset it to ISO-8859-1. Note that this shouldn't be an issue in production, though.
    System.setProperty("file.encoding", "ISO-8859-1");
    System.setProperty("LC_ALL", "en_US.ISO-8859-1");
    System.setProperty("LC_CTYPE", "ISO-8859-1");
    System.setProperty("LANG", "en_US.ISO-8859-1");

    if (sshd == null) {

      final CommandFactory commandFactory = new CommandFactory();
      final Properties p = new Properties();
      p.load(SshJDriver.class.getClassLoader().getResourceAsStream("ssh.properties"));
      sshPort = Integer.parseInt(p.getProperty("jdbc.ssh.port"));
      sshHost = p.getProperty("jdbc.ssh.host");

      sshd = SshServer.setUpDefaultServer();
      sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get("target/hostkey.rsa")));
      sshd.setPasswordAuthenticator(new BogusPasswordAuthenticator());
      sshd.setPublickeyAuthenticator(new TestCachingPublicKeyAuthenticator());
      sshd.setCommandFactory(commandFactory);
      sshd.setHost(sshHost);
      sshd.setPort(sshPort);
      sshd.setForwardingFilter(new ForwardingFilter() {
        @Override
        public boolean canForwardAgent(Session session, String requestType) {
          return true;
        }

        @Override
        public boolean canForwardX11(Session session, String requestType) {
          return true;
        }

        @Override
        public boolean canListen(SshdSocketAddress address, Session session) {
          return true;
        }

        @Override
        public boolean canConnect(Type type, SshdSocketAddress address, Session session) {
          return true;
        }
      });

      sshd.start();
      logger.info("Started SSH server on port {}:{}", sshHost, sshPort);
    }
  }

  private void shutdownSshd() throws Exception {
    if (sshd != null && !sshd.isClosed()) {
      sshd.stop();
      logger.info("Stopped SSH server on port {}:{}", sshHost, sshPort);
    }
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

    dbServerDerby = new NetworkServerControl(
        InetAddress.getByAddress(null, new byte[]{127, 0, 0, 1}), DERBY_PORT);
    dbServerDerby.start(new PrintWriter(new Slf4jOutputStream(logger), true));

    for (int i = 0; i < 10; ++i) {
      try {
        dbServerDerby.ping();
        break;
      } catch (Exception e) {
        logger.warn(e.getMessage());
      }
      Thread.sleep(10);
    }

    logger.info("JDBC Runtime Info:\n{}", dbServerDerby.getRuntimeInfo());
  }

  private void shutdownDerby() throws Exception {
    logger.info("*** Shutting down Derby from Test.");
    dbServerDerby.shutdown();
  }

  private void startupH2() throws Exception {
    System.setProperty("h2.baseDir", "./target/h2");
    dbServerH2 = Server.createTcpServer("-tcpPort", "8092", "-tcpDaemon", "-ifNotExists").start();
    logger.info("Database server status: u = {} - s = {} ({})", dbServerH2.getURL(),
        dbServerH2.getStatus(), dbServerH2.isRunning(true));
  }

  private void shutdownH2() {
    logger.info("*** Shutting down H2 from Test.");
    dbServerH2.shutdown();
  }

  @DataProvider(name = "drivers")
  public Object[][] getDrivers() {
    return new Class[][]{
        {SshJDriver.class},
        {SshJNativeDriver.class},
    };
  }

  @Test(dataProvider = "drivers")
  public void testDriverRegistration(Class clazz) throws SQLException {
    boolean found = false;

    for (Enumeration<Driver> drivers = DriverManager.getDrivers(); drivers.hasMoreElements(); ) {
      Driver driver = drivers.nextElement();

      if (driver.getClass().equals(clazz)) {
        found = true;
        break;
      }
    }
    assertTrue(found);
  }

  @DataProvider(name = "connections")
  public Object[][] createConnections() {
    final String db2Url = dbServerH2.getURL();
    final int portIdx = db2Url.lastIndexOf(":");
    String srv = db2Url.substring(0, portIdx); // tcp://a.b.c.d
    srv = srv.replaceFirst("^[a-z]+://", "");
    String port = db2Url.substring(portIdx + 1);

    return new Object[][]{
        // Derby
        {DERBY_SQL, "jdbc:derby://127.0.0.1:" + DERBY_PORT + "/target/test;create=true"}, // Direct
        // Can't figure out why this won't work. H2 connects successfuly, but this ends up with "Error connecting to server 127.0.0.1 on port 20,081 with message Connection refused."
        // {DERBY_SQL, "jdbc:sshj-native://" + sshHost + " -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -p " + sshPort + "?remote=127.0.0.1:" + DERBY_PORT + ";;;jdbc:derby://{{host}}:{{port}}/target/test;create=true"}, // Native
        {DERBY_SQL, "jdbc:sshj://" + sshHost + ":" + sshPort + "?remote=127.0.0.1:" + DERBY_PORT
            + "&username=test&password=test&verify_hosts=off;;;jdbc:derby://{{host}}:{{port}}/target/test;create=true"},
        // SSHJ
        // H2
        {H2_SQL, "jdbc:h2:" + db2Url + "/test"}, // Direct
        {H2_SQL, "jdbc:sshj-native://" + sshHost
            + " -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -p " + sshPort
            + "?remote=" + srv + ":" + port + ";;;jdbc:h2:{{host}}:{{port}}/test"}, // Native
        {H2_SQL, "jdbc:sshj://" + sshHost + ":" + sshPort + "?remote=" + srv + ":" + port
            + "&username=test&password=test&verify_hosts=off;;;jdbc:h2:{{host}}:{{port}}/test"},
        // SSHJ
    };
  }

  @Test(dependsOnMethods = "testDriverRegistration", dataProvider = "connections")
  public void testConnection(String sql, String url) throws Exception {
    logger.debug("Connection: {}", url);
    try (Connection connection = DriverManager.getConnection(url)) {
      logger.debug("Info: {}", connection.getClientInfo());

      Statement s = connection.createStatement();
      s.execute(sql);

      DatabaseMetaData metadata = connection.getMetaData();

      logger.debug("{} - {}", metadata.getDatabaseProductName(),
          metadata.getDatabaseProductVersion());
    }
  }

  private static class DemoCommand implements Command {

    private final String command;
    private OutputStream out;

    public DemoCommand(String command) {
      this.command = command;
    }

    @Override
    public void setInputStream(InputStream in) {
    }

    @Override
    public void setOutputStream(OutputStream out) {
      this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
    }

    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
      logger.debug("Executing command: {}", command);
      out.write((LOGIN_MESSAGE + "\r\n").getBytes());
      out.flush();
    }

    @Override
    public void destroy(ChannelSession channel) {

    }
  }

  private static class CommandFactory implements org.apache.sshd.server.command.CommandFactory,
      Factory<Command> {

    @Override
    public Command createCommand(ChannelSession channel, String command) {
      logger.debug("Creating command: {}", command);
      return new DemoCommand(command);
    }

    @Override
    public Command create() {
      logger.debug("Creating shell command");
      return new DemoCommand(null);
    }
  }
}
