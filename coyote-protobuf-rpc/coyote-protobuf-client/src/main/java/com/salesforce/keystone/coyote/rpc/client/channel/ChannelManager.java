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

package com.salesforce.keystone.coyote.rpc.client.channel;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.salesforce.keystone.coyote.rpc.client.connection.ServerName;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe management of connections on a per-server/port basis
 */
public class ChannelManager {

  private final ClientChannelFactory channels;
  private Map<String, RpcChannel> connections = new HashMap<>();

  public ChannelManager(ClientChannelFactory channelFactory) {
    this.channels = channelFactory;
  }

  public ListenableFuture<RpcChannel> connect(ServerName server){
    final String key = getKey(server.hostname, server.port);
    RpcChannel channel = connections.get(key);
    if (channel != null) {
      return Futures.immediateFuture(channel);
    }

    synchronized (connections) {
      channel = connections.get(key);
      if (channel != null) {
        // may have a channel, but it may be closing, so we need to check to see if its open
        // before returning it for use
        if (channel.isOpen()) {
          return Futures.immediateFuture(channel);
        } else {
          // cleanup closed connections so we don't look them up later
          connections.remove(key);
        }
      }

      // create a new channel
      final ListenableFuture<RpcChannel> future = channels.createChannel(server.hostname,
          server.port);
      // when we create the channel, add it to the list of connections that we care about
      Futures.addCallback(future, new FutureCallback<RpcChannel>() {
        @Override
        public void onSuccess(RpcChannel result) {
          connections.put(key, result);
        }

        @Override
        public void onFailure(Throwable t) {
          // noop - error is handled at a higher level
        }
      });

      return future;
    }
  }

  private String getKey(String server, int port) {
    return server + "@" + port;
  }

  public void close() {
    this.channels.close();
  }
}