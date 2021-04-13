package com.m11n.jdbc.ssh.util;

import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.session.ServerSession;

public class BogusPasswordAuthenticator implements PasswordAuthenticator {

  @Override
  public boolean authenticate(String username, String password, ServerSession session)
      throws PasswordChangeRequiredException, AsyncAuthException {
    return true;
  }
}
