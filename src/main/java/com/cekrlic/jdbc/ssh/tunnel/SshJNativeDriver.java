package com.cekrlic.jdbc.ssh.tunnel;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.LoggerFactory;

public class SshJNativeDriver extends AbstractSshJDriver {

  public static final String DRIVER_PREFIX = "jdbc:sshj-native:";
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(SshJNativeDriver.class);
  private static final int VERSION_MAJOR = 1;
  private static final int VERSION_MINOR = 0;

  static {
    try {
      DriverManager.registerDriver(new SshJNativeDriver());
    } catch (SQLException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public SshJNativeDriver() throws SQLException {
    log.trace("SSHJNativeDriver initialized");
  }

  @Override
  String getDriverPrefix() {
    return DRIVER_PREFIX;
  }


  protected AbstractTunnel newTunnel(ConnectionData d) throws IOException, SQLException {
    SshNativeTunnel n = new SshNativeTunnel(d.getOurUrl());
    // SshTunnel will also load 3rd-party driver, if needed.
    n.start();
    return n;
  }

  @Override
  public int getMajorVersion() {
    return VERSION_MAJOR;
  }

  @Override
  public int getMinorVersion() {
    return VERSION_MINOR;
  }

}
