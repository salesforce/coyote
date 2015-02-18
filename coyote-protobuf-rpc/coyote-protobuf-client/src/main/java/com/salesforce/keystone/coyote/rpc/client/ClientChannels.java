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

package com.salesforce.keystone.coyote.rpc.client;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.salesforce.keystone.coyote.rpc.client.channel.ChannelManager;
import com.salesforce.keystone.coyote.rpc.client.channel.ClientChannelFactory;
import com.salesforce.keystone.coyote.rpc.client.channel.RpcChannel;
import com.salesforce.keystone.coyote.rpc.client.connection.RoundRobin;
import com.salesforce.keystone.coyote.rpc.client.connection.ServerLocationManager;
import com.salesforce.keystone.coyote.rpc.client.connection.ServerName;
import com.salesforce.keystone.coyote.rpc.client.connection.StaticLocationFinder;
import com.salesforce.keystone.coyote.rpc.client.request.RequestManager;
import com.salesforce.keystone.coyote.rpc.client.util.ExposedListenableFuture;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceTranslator;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Create and manage async channels used by the client
 */
public class ClientChannels implements AutoCloseable {

  private static final Log LOG = LogFactory.getLog(ClientChannels.class);

  private final ChannelManager connections;
  private final boolean allowUnknownServers;
  private RoundRobin<ServerName> servers;

  /**
   *
   * @param servers
   * @param locations
   * @param channelManager
   * @param allowUnknownServers
   */
  public ClientChannels(ServerLocationManager servers, ChannelManager channelManager,
      boolean allowUnknownServers) {
    this.servers = servers;
    this.connections = channelManager;
    this.allowUnknownServers = allowUnknownServers;
  }

  public ClientChannels(List<String> servers, int port, MessageMapper mapper,
      List<ProtobufServiceTranslator> services, RequestManager requestManager) {
    this(new ServerLocationManager(new StaticLocationFinder(asServerNames(servers, port)),
            ServerLocationManager.NEVER),
        new ChannelManager(new ClientChannelFactory<>(services, new ClientChannelInitializer
            (mapper, requestManager), requestManager)), true);
  }

  protected static List<ServerName> asServerNames(List<String> servers, int port) {
    List<ServerName> sns = new ArrayList<>(servers.size());
    for (String server : servers) {
      sns.add(new ServerName(server, port));
    }
    return sns;
  }

  /**
   * Connect to a specific server. Future returns success when it finishes making a connection to
   * the remote server, or an error when it fails
   *
   * @param server server to attempt to connect to
   * @return Future that will be notified when the channel has be connected
   * @throws IllegalArgumentException if the server is not in the known list and we are only allowing know
   * servers
   */
  public ListenableFuture<RpcChannel> getChannel(ServerName server) {
    // if we don't allow unknown servers, then we have to check if its in the known list
    if (!allowUnknownServers) {
      if (!servers.getOriginalElements().contains(server)) {
        throw new IllegalArgumentException("Server " + server
            + " is not in the known list of servers and not allowing unknown server connections");
      }
    }
    return connections.connect(server);
  }

  /**
   * Get a connection to any known server. Connection returned from the future is connected to
   * a remote server
   *
   * @return channel to write/read messages to any server
   */
  public ListenableFuture<RpcChannel> getChannel() {

    // wrapper around the generic channel future so we can properly handle errors
    final ExposedListenableFuture<RpcChannel> done = new ExposedListenableFuture<>();

    // set the result on the future, when we have one.
    updateResult(done);

    return done;
  }

  private void updateResult(final ExposedListenableFuture<RpcChannel> done) {
    try {
      final RoundRobin<ServerName>.Element next = servers.next();
      ListenableFuture<RpcChannel> channel = getChannel(next.get());

      Futures.addCallback(channel, new FutureCallback<RpcChannel>() {
        @Override
        public void onSuccess(RpcChannel result) {
          next.markValid();
          done.set(result);
        }

        @Override
        public void onFailure(Throwable t) {
          if (next == null) {
            assert false : "Somehow got an exception but never specified a 'next' server";
          } else {
            LOG.debug("Unable to create channel to " + next.get() + ", trying next channel", t);
            // mark the element as invalid
            next.markInvalid();
          }

          // attempt to update the next result
          updateResult(done);
        }
      });
    } catch (NoSuchElementException e) {
      done.setException(
          new IOException("Attempted to connect to all servers, no more known servers after " +
              "exhausting round-robin retries."));
      return;
    }
  }

  @Override
  public void close() {
    this.connections.close();
  }

  public String toString() {
    return "Channel connection to " + servers;
  }
}