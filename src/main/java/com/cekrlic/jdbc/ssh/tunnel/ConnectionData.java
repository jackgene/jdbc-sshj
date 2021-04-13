package com.cekrlic.jdbc.ssh.tunnel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author boky
 * @created 11/02/2017 17:47
 */
public class ConnectionData {

  private static final Logger log = LoggerFactory.getLogger(ConnectionData.class);

  final String ourUrl;
  final String forwardingUrl;

  public ConnectionData(String ourUrl, String forwardingUrl) {
    this.ourUrl = ourUrl;
    this.forwardingUrl = forwardingUrl;
  }

  public String getOurUrl() {
    return ourUrl;
  }

  public String getForwardingUrl() {
    return forwardingUrl;
  }
}
