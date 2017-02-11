package com.cekrlic.jdbc.ssh.tunnel;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class SshJNativeDriver extends AbstractSshJDriver {
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(SshJNativeDriver.class);

	public static final String DRIVER_PREFIX = "jdbc:sshj-native:";

	private static final int VERSION_MAJOR = 1;
	private static final int VERSION_MINOR = 0;

	public SshJNativeDriver() throws SQLException {
		log.trace("SSHJNativeDriver initialized");
	}

	static {
		try {
			DriverManager.registerDriver(new SshJNativeDriver());
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

		try {
			// SshTunnel will also load 3rd-party driver, if needed.
			tunnel = new SshNativeTunnel(d.getOurUrl());
			tunnel.start();
		} catch (IOException e) {
			throw new SQLException(e);
		}

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
