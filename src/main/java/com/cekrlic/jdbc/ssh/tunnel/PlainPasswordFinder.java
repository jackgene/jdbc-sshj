package com.cekrlic.jdbc.ssh.tunnel;

import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;

/**
 * @author boky
 * @created 08/02/2017 15:01
 */
public class PlainPasswordFinder implements PasswordFinder {
	private final char[] password;

	public PlainPasswordFinder(String password) {
		this.password = password.toCharArray();
	}

	@Override
	public char[] reqPassword(Resource<?> resource) {
		return password;
	}

	@Override
	public boolean shouldRetry(Resource<?> resource) {
		return false;
	}
}
