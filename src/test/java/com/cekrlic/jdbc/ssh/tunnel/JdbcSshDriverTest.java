package com.cekrlic.jdbc.ssh.tunnel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.sql.*;
import java.util.Enumeration;

import static org.testng.Assert.assertTrue;

public abstract class JdbcSshDriverTest {
	private static final Logger logger = LoggerFactory.getLogger(JdbcSshDriverTest.class);

	String sshUrl;

	String realUrl;

	String sql;

	@Test
	public void testDriverRegistration() throws SQLException {
		boolean found = false;

		for (Enumeration<Driver> drivers = DriverManager.getDrivers(); drivers.hasMoreElements(); ) {
			Driver driver = drivers.nextElement();

			if (driver.getClass().equals(SshJDriver.class)) {
				found = true;
				break;
			}
		}

		assertTrue(found);
	}

	private void dbConnectionTest(String url) throws SQLException {
		logger.debug("Connection: {}", url);
		try (Connection connection = DriverManager.getConnection(url)) {
			logger.debug("Info: {}", connection.getClientInfo());

			Statement s = connection.createStatement();
			s.execute(sql);

			DatabaseMetaData metadata = connection.getMetaData();

			logger.debug("{} - {}", metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion());
		}
	}

	@Test
	public void testSshDriver() throws Exception {
		dbConnectionTest(this.sshUrl);
		LoggerFactory.getLogger(this.getClass()).info("Connected successfully through SSH!");
	}

	@Test
	public void testRealDriver() throws Exception {
		dbConnectionTest(this.realUrl);
		LoggerFactory.getLogger(this.getClass()).info("Connected successfully directly!");
	}
}
