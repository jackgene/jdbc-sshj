package com.cekrlic.jdbc.ssh.tunnel;

import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class SshJDriver extends AbstractSshJDriver {
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(SshJDriver.class);

	public static final String DRIVER_PREFIX = "jdbc:sshj:";

	private static final int VERSION_MAJOR = 1;
	private static final int VERSION_MINOR = 0;

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
	String getDriverPrefix() {
		return DRIVER_PREFIX;
	}

	@Override
	public Connection connect(String url, Properties info) throws SQLException {
		ConnectionData d = verifyConnection(url);
		// Not our connection URL, skip further integration
		if(d == null) {
			return null;
		}

		// SshTunnel will also load 3rd-party driver, if needed.
		tunnel = new SshTunnel(d.getOurUrl());
		tunnel.start();

		return getRealConnection(info, d.getForwardingUrl(), tunnel.getLocalHost(), tunnel.getLocalPort());
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
