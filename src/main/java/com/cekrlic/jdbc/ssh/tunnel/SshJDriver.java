package com.cekrlic.jdbc.ssh.tunnel;

import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

public class SshJDriver implements Driver {
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(SshJDriver.class);

	public static final String DRIVER_PREFIX = "jdbc:sshj:";

	private static final int VERSION_MAJOR = 1;
	private static final int VERSION_MINOR = 0;

	private SshTunnel tunnel;

	public SshJDriver() throws SQLException {
		log.trace("SSHJDriver initialized");
	}

	static {
		try {
			java.sql.DriverManager.registerDriver(new SshJDriver());
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return (url != null && url.startsWith(DRIVER_PREFIX));
	}


	/*
		URL syntax:
			jdbc:ssh://<server>&username=;;;<original-jdbc-url>
	 */

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		if (url == null) {
			throw new SQLException("URL is required");
		}

		if (!acceptsURL(url)) {
			return null;
		}

		final String[] split = url.split(";;;", 2);
		if(split.length != 2) {
			throw new SQLException("Please provide the original database URL after the SSH settings by including three semi-colons (;;;) and the original URL.");
		}
		final String ourURL = split[0];
		String realUrl = split[1];
		if(!realUrl.toLowerCase().startsWith("jdbc:")) {
			throw new SQLException("You need to supply the actual URL to connect to!");
		}

		// SshTunnel will also load 3rd-party driver, if needed.
		tunnel = new SshTunnel(ourURL);
		tunnel.start();

		realUrl = realUrl.replaceAll("\\{\\{[pP][oO][rR][tT]\\}\\}", tunnel.getLocalPort());
		realUrl = realUrl.replaceAll("\\{\\{[hH][oO][sS][tT]\\}\\}", tunnel.getLocalHost());

		log.info("Proxying connection to: {}", realUrl);
		Driver driver = findDriver(realUrl);
		return driver.connect(realUrl, info);
	}

	private Driver findDriver(String url) throws SQLException {
		Driver realDriver = null;

		for (Enumeration<Driver> drivers = DriverManager.getDrivers(); drivers.hasMoreElements(); ) {
			try {
				Driver driver = drivers.nextElement();

				if (driver.acceptsURL(url)) {
					realDriver = driver;
					break;
				}
			} catch (SQLException e) {
				// Ignore, this is fine
			}
		}

		if (realDriver == null) {
			throw new SQLException("Unable to find a driver that accepts " + url);
		}

		return realDriver;
	}

	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return findDriver(url).getPropertyInfo(url, info);
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
	public boolean jdbcCompliant() {
		return true;
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		throw new SQLFeatureNotSupportedException("Feature not supported");
	}
}
