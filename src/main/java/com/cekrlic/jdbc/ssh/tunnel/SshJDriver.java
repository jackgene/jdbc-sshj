package com.cekrlic.jdbc.ssh.tunnel;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

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
	public int getMajorVersion() {
		return VERSION_MAJOR;
	}

	@Override
	public int getMinorVersion() {
		return VERSION_MINOR;
	}

	protected AbstractTunnel newTunnel(ConnectionData d) throws IOException, SQLException {
		SshTunnel n = new SshTunnel(d.getOurUrl());
		// SshTunnel will also load 3rd-party driver, if needed.
		n.start();
		return n;
	}


}
