package com.m11n.jdbc.ssh.util;

import org.apache.sshd.server.auth.pubkey.CachingPublicKeyAuthenticator;

public class TestCachingPublicKeyAuthenticator extends CachingPublicKeyAuthenticator {

  public TestCachingPublicKeyAuthenticator() {
    super((s, publicKey, serverSession) -> true);
  }
}
