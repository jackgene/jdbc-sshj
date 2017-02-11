package com.cekrlic.jdbc.ssh.tunnel;

import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

public abstract class AbstractSshJDriver implements Driver {
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(AbstractSshJDriver.class);

	protected AbstractTunnel tunnel;

	abstract String getDriverPrefix();

	@Override
	public boolean acceptsURL(String url) throws SQLException {
		return (url != null && url.startsWith(getDriverPrefix()));
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		return null;
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

	protected Connection getRealConnection(Properties info, String originalUrl, String newHost, String newPort) throws SQLException {
		originalUrl = originalUrl.replaceAll("\\{\\{[hH][oO][sS][tT]\\}\\}", newHost);
		originalUrl = originalUrl.replaceAll("\\{\\{[pP][oO][rR][tT]\\}\\}", newPort);

		log.info("Proxying connection {}:{}: {}",  tunnel.getLocalHost(), tunnel.getLocalPort(), originalUrl);
		Driver driver = findDriver(originalUrl);
		return driver.connect(originalUrl, info);
	}

	protected ConnectionData verifyConnection(String url) throws SQLException {
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
		final String ourUrl = split[0];
		String forwardingUrl = split[1];
		if(ourUrl.length()==0) {
			throw new SQLException("Missing SSHJ URL!");
		}

		if(!forwardingUrl.toLowerCase().startsWith("jdbc:")) {
			throw new SQLException("You need to supply the actual URL to connect to!");
		}

		return new ConnectionData(
				ourUrl,
				forwardingUrl
		);
	}


	@Override
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
		return findDriver(url).getPropertyInfo(url, info);
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
