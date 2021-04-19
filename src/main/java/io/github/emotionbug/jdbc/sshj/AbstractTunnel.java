package io.github.emotionbug.jdbc.sshj;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author boky
 * @created 11/02/2017 18:10
 */
@SuppressWarnings("WeakerAccess")
public abstract class AbstractTunnel {

  public static final String REMOTE = "remote";
  public static final String DRIVERS = "drivers";
  private static final Logger log = LoggerFactory.getLogger(AbstractTunnel.class);
  protected AtomicInteger connectionCount = new AtomicInteger(0);
  protected AtomicInteger localPort = new AtomicInteger(
      20000 + new java.util.Random().nextInt(500));
  protected String localHost;

  public AbstractTunnel() {
    localHost = determineLocalHostIp();
  }

  protected static Map<String, String> splitQuery(URI url) throws UnsupportedEncodingException {
    return splitQuery(url.getQuery());
  }

  protected static Map<String, String> splitQuery(String query)
      throws UnsupportedEncodingException {
    Map<String, String> query_pairs = new LinkedHashMap<>();
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

  protected static void loadDrivers(Map<String, String> queryParameters) {
    String drv = queryParameters.get(DRIVERS);
    if (drv == null) {
      // Convenience method for people such as me that don't read the documentation thorougly
      drv = queryParameters.get("driver");
    }
    if (drv != null && drv.length() > 0) {
      final String[] drivers = drv.split(",");
      for (final String driver : drivers) {
        try {
          Class.forName(driver);
          log.debug("Loaded JDBC driver: {}", driver);
        } catch (ClassNotFoundException e) {
          log.warn("Failed loading class " + driver + "! Skipping class.", e);
        }
      }
    }
  }

  protected static boolean isPortOpen(String ip, int port) {
    Socket socket = new Socket();
    try {
      socket.connect(new InetSocketAddress(ip, port), 1000);
      return true;
    } catch (Exception ex) {
      return false;
    } finally {
      try {
        socket.close();
      } catch (IOException e) {
        // Ignore
      }
    }
  }

  /**
   * Retrieve the local port at which the SSH tunnel is listening.
   *
   * @return The local port, e.g. 22014.
   */
  public String getLocalPort() {
    return Integer.toString(localPort.get());
  }

  /**
   * Retrieve the local IP at which the SSH tunnel is listening.
   *
   * @return The local IP, e.g. 127.0.2.214.
   */
  public String getLocalHost() {
    return localHost;
  }

  protected String determineLocalHostIp() {
    String localHost;
    // Determine if it's mac:
    String os = System.getProperty("os.name").toUpperCase();
    if (os.contains("OS X") || os.contains("MACOS") || os.contains("MAC ")
        || System.getProperty("CIRCLE_TEST_REPORTS") != null) {
      localHost = "127.0.0.1";
    } else {
      localHost = "127.0.1." + (2 + new java.util.Random().nextInt(201));
    }
    return localHost;
  }

  /**
   * Start the tunnel.
   */
  abstract void start() throws SQLException;

  public boolean isListening() {
    return isPortOpen(localHost, localPort.get());
  }

  protected void determineLocalPort() {
    int nextPort = localPort.incrementAndGet();

    // NOTE: scan max next 10 ports
    for (int i = 0; i < 10; i++) {
      if (!isPortOpen(localHost, nextPort)) {
        break;
      }

      nextPort = localPort.incrementAndGet();
    }
  }

  /**
   * Stop the tunnel
   */

  abstract void stop(String reason);

  abstract void ensureStarted() throws SQLException;

  abstract boolean isStopped();

  public void remove(SshConnection sshConnection) {
    if (connectionCount.decrementAndGet() == 0) {
      this.stop("No connections remaining open.");
    }
  }

  public void add(SshConnection sshConnection) {
    connectionCount.incrementAndGet();
  }
}
