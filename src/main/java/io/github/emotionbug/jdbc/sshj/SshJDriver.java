package io.github.emotionbug.jdbc.sshj;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import org.slf4j.LoggerFactory;

public class SshJDriver extends AbstractSshJDriver {

  public static final String DRIVER_PREFIX = "jdbc:sshj:";
  public static final String QUERY_PREFIX = "ssh.";
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(SshJDriver.class);
  private static final int VERSION_MAJOR = 1;
  private static final int VERSION_MINOR = 0;

  static {
    try {
      java.sql.DriverManager.registerDriver(new SshJDriver());
    } catch (SQLException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public SshJDriver() throws SQLException {
    log.trace("SSHJDriver initialized");
  }

  @Override
  String getDriverPrefix() {
    return DRIVER_PREFIX;
  }

  @Override
  public int getMajorVersion() {
    return VERSION_MAJOR;
  }

  @Override
  public int getMinorVersion() {
    return VERSION_MINOR;
  }

  @Override
  protected ConnectionData verifyConnection(String url) throws SQLException {
    if (url == null) {
      throw new SQLException("URL is required");
    }

    if (!acceptsURL(url)) {
      return null;
    }

    // Legacy/SSH-centric URL
    if (url.startsWith(DRIVER_PREFIX + "//")) {
      return super.verifyConnection(url);
    }

    // Database-centric URL
    // jdbc:sshj:postgresql://db.acme.com:5432/db?ssl=true&ssh.username=&ssh.password=&ssh.host=...&ssh.port=...
    final int schemeSepIdx = url.indexOf("//");
    final String scheme = url.substring(0, schemeSepIdx);
    try {
      final URI uri = new URI(url.substring(schemeSepIdx));
      String sshHost = null;
      String sshPort = "22";
      StringBuilder ourQuery = new StringBuilder();
      StringBuilder forwardingQuery = new StringBuilder();
      for (String queryKv : uri.getQuery().split("&")) {
        if (queryKv.startsWith("ssh.host=") && queryKv.length() > 9) {
          sshHost = queryKv.substring(9);
          continue;
        }
        if (queryKv.startsWith("ssh.port=") && queryKv.length() > 9) {
          sshPort = queryKv.substring(9);
          continue;
        }

        if (queryKv.startsWith(QUERY_PREFIX)) {
          if (ourQuery.length() > 0) {
            ourQuery.append("&");
          }
          ourQuery.append(queryKv.substring(QUERY_PREFIX.length()));
        } else {
          if (forwardingQuery.length() > 0) {
            forwardingQuery.append("&");
          }
          forwardingQuery.append(queryKv);
        }
      }
      if (sshHost == null) {
        throw new SQLException("ssh.host must be provided");
      }

      final String ourUrl = "jdbc:sshj://" + sshHost + ":" + sshPort +
          "?remote=" + uri.getHost() + ":" + uri.getPort() + "&" + ourQuery;

      final String forwardingUrl = "jdbc:" + scheme.substring(DRIVER_PREFIX.length()) +
          "//{{host}}:{{port}}" + uri.getPath() + "?" + forwardingQuery;

      return new ConnectionData(
        ourUrl,
        forwardingUrl
      );
    } catch (URISyntaxException e) {
      throw new SQLException("Unable to parse URI", e);
    }
  }

  protected AbstractTunnel newTunnel(ConnectionData d) throws SQLException {
    SshTunnel n = new SshTunnel(d.getOurUrl());
    // SshTunnel will also load 3rd-party driver, if needed.
    n.start();
    return n;
  }


}
