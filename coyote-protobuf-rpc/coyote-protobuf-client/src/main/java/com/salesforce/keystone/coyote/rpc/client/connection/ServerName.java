/*
 * Copyright (c) 2014, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 *  Neither the name of Salesforce.com nor the names of its contributors may be used to endorse
 * or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.keystone.coyote.rpc.client.connection;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Simple holder for the hostname and port of a server, along with utility functions for parsing
 * the name from a simple {@link String}
 */
public class ServerName {

  public String hostname;
  public int port;

  public ServerName(String hostname, int port) {
    this.hostname = hostname;
    this.port = port;
  }

  public static ServerName parse(String server) {
    return parse(server, -1);
  }

  public static ServerName parse(String hostAndPort, int defaultPort) {
    String hostname;
    int port;
    try {
      hostname = parseHostname(hostAndPort);
      port = parsePort(hostAndPort);
    } catch (IllegalArgumentException e) {
      hostname = hostAndPort;
      port = defaultPort;
    }
    return new ServerName(hostname, port);
  }

  public InetSocketAddress asInetSocketAddress() {
    return new InetSocketAddress(hostname, port);
  }

  public String toString() {
    return createHostAndPortStr(hostname, port);
  }

  public static Collection<ServerName> split(Collection<String> servers) {
    List<ServerName> split = new ArrayList<>(servers.size());
    for (String server : servers) {
      split.add(parse(server));
    }
    return split;
  }

  public static final String VALID_PORT_REGEX = "[\\d]+";
  public static final String HOSTNAME_PORT_SEPARATOR = ":";

  /**
   * @param hostAndPort Formatted as <code>&lt;hostname></code> and optionally followed by
   *          <code>':' &lt;port></code>
   * @param defaultPort port to use if the hostAndPort is missing the port
   * @return An InetSocketInstance
   */
  public static InetSocketAddress createInetSocketAddressFromHostAndPortStr(
      final String hostAndPort, final int defaultPort) {
    return parse(hostAndPort, defaultPort).asInetSocketAddress();
  }

  /**
   * @param hostname Server hostname
   * @param port Server port
   * @return Returns a concatenation of <code>hostname</code> and <code>port</code> in following
   *         form: <code>&lt;hostname> ':' &lt;port></code>. For example, if hostname is
   *         <code>example.org</code> and port is 1234, this method will return
   *         <code>example.org:1234</code>
   */
  public static String createHostAndPortStr(final String hostname, final int port) {
    return hostname + HOSTNAME_PORT_SEPARATOR + port;
  }

  /**
   * @param hostAndPort Formatted as <code>&lt;hostname> ':' &lt;port></code>
   * @return The hostname portion of <code>hostAndPort</code>
   */
  public static String parseHostname(final String hostAndPort) {
    int colonIndex = hostAndPort.lastIndexOf(HOSTNAME_PORT_SEPARATOR);
    if (colonIndex < 0) {
      throw new IllegalArgumentException("Not a host:port pair: " + hostAndPort);
    }
    return hostAndPort.substring(0, colonIndex);
  }

  /**
   * @param hostAndPort Formatted as <code>&lt;hostname> ':' &lt;port></code>
   * @return The port portion of <code>hostAndPort</code>
   */
  public static int parsePort(final String hostAndPort) {
    int colonIndex = hostAndPort.lastIndexOf(HOSTNAME_PORT_SEPARATOR);
    if (colonIndex < 0) {
      throw new IllegalArgumentException("Not a host:port pair: " + hostAndPort);
    }
    return Integer.parseInt(hostAndPort.substring(colonIndex + 1));
  }
}
