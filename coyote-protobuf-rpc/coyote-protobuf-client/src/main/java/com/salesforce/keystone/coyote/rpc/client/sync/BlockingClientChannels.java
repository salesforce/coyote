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

package com.salesforce.keystone.coyote.rpc.client.sync;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.BlockingRpcChannel;
import com.salesforce.keystone.coyote.rpc.client.ClientChannels;
import com.salesforce.keystone.coyote.rpc.client.channel.ChannelManager;
import com.salesforce.keystone.coyote.rpc.client.channel.RpcChannel;
import com.salesforce.keystone.coyote.rpc.client.connection.ServerLocationManager;
import com.salesforce.keystone.coyote.rpc.client.connection.ServerName;
import com.salesforce.keystone.coyote.rpc.client.request.RequestManager;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceTranslator;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;

import java.io.IOException;
import java.util.List;

/**
 * Create and manage the channels used by the client
 */
public class BlockingClientChannels extends ClientChannels {

  public BlockingClientChannels(ServerLocationManager servers, ChannelManager channelManager,
      boolean allowUnknownServers) {
    super(servers, channelManager, allowUnknownServers);
  }

  public BlockingClientChannels(List<String> servers, int port,
      MessageMapper mapper, List<ProtobufServiceTranslator> services) {
    super(servers, port, mapper, services, new RequestManager());
  }

  /**
   * Connect to a specific server
   *
   * @param server server to attempt to connect to
   * @return connection to the specified remote server
   * @throws IOException if the server is not in the known list and we are only allowing know
   * servers
   * @throws InterruptedException if interrupted while attempting to connect to a server
   */
  public BlockingRpcChannel getBlockingChannel(ServerName server)
      throws InterruptedException, IOException {
    return toBlockingChannel(super.getChannel(server));
  }

  private BlockingRpcChannel toBlockingChannel(ListenableFuture<RpcChannel> channel)
      throws IOException, InterruptedException {
    RpcChannel rpcChannel= Futures.get(channel, IOException.class);
    return BlockingRpcUtils.asBlockingChannel(rpcChannel);
  }

  /**
   * Connect to any available server
   *
   * @return channel to write/read messages to any server
   * @throws InterruptedException if interrupted while attempting to connect to a server
   */
  public BlockingRpcChannel getBlockingChannel() throws InterruptedException, IOException {
    return toBlockingChannel(super.getChannel());
  }
}