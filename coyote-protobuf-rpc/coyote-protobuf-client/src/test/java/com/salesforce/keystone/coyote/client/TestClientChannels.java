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

package com.salesforce.keystone.coyote.client;

import com.google.common.util.concurrent.Futures;
import com.salesforce.keystone.coyote.rpc.client.ClientChannels;
import com.salesforce.keystone.coyote.rpc.client.channel.ChannelManager;
import com.salesforce.keystone.coyote.rpc.client.channel.RpcChannel;
import com.salesforce.keystone.coyote.rpc.client.connection.ServerName;
import com.salesforce.keystone.coyote.rpc.client.request.RequestManager;
import com.salesforce.keystone.coyote.rpc.client.util.ExposedListenableFuture;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceTranslator;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Test that we create the connections that we expect
 */
public class TestClientChannels {

  @Test
  public void testTryAllServers() throws Exception {
    List<ServerName> servers = new ArrayList<>(2);
    servers.add(new ServerName("a", 1));
    servers.add(new ServerName("b", 2));

    ChannelManager channelManager = Mockito.mock(ChannelManager.class);
    ClientChannels channels =
        new ClientChannels(ChannelTestUtil.getLocationManager(servers), channelManager,
            false);

    RpcChannel channel = Mockito.mock(RpcChannel.class);
    Mockito.when(channelManager.connect(Mockito.any(ServerName.class)))
        .thenReturn(Futures.immediateFuture(channel));

    // get the first server
    channels.getChannel().get();
    // get the second server
    channels.getChannel().get();

    // verify that we try all the channels exactly once
    Mockito.verify(channelManager).connect(servers.get(0));
    Mockito.verify(channelManager).connect(servers.get(1));
  }

  /**
   * Should allow a connection to a server that is not explicitly in the 'known' list of servers,
   * if we got a server name from somewhere else
   *
   * @throws Exception on failure
   */
  @Test
  public void testConnectToUnknownServer() throws Exception {
    // create some 'known' servers
    List<ServerName> servers = new ArrayList<>(2);
    servers.add(new ServerName("a", 1));
    servers.add(new ServerName("b", 2));

    ChannelManager channelManager = Mockito.mock(ChannelManager.class);
    ClientChannels channels =
        new ClientChannels(ChannelTestUtil.getLocationManager(servers), channelManager, true);

    ServerName other = new ServerName("c", 3);

    RpcChannel channel = Mockito.mock(RpcChannel.class);
    Mockito.when(channelManager.connect(other)).thenReturn(Futures.immediateFuture(channel));

    channels.getChannel(other).get();

    // and then disallow unknown servers
    channels =
        new ClientChannels(ChannelTestUtil.getLocationManager(servers), channelManager, false);

    try {
      Futures.get(channels.getChannel(other), IOException.class);
      fail("Should not allow unknown server connection when disabled");
    } catch (IllegalArgumentException e) {
      //expected
    }
  }

  @Test
  public void testTryAllServersUnderFailure() throws Exception {
    List<ServerName> servers = new ArrayList<>(2);
    servers.add(new ServerName("a", 1));
    servers.add(new ServerName("b", 2));

    ChannelManager channelManager = Mockito.mock(ChannelManager.class);
    // setup the roundrobin to only allow one connection attempt per server before giving up
    ClientChannels channels =
        new ClientChannels(ChannelTestUtil.getLocationManager(servers, 0, 0, 0), channelManager,
            false);

    // future that just throws an exception immediately
    ExposedListenableFuture<RpcChannel> future = new ExposedListenableFuture<>();
    future.setException(new IOException("test channel connection failure"));
    Mockito.when(channelManager.connect(Mockito.any(ServerName.class))).thenReturn(future);

    try {
      Futures.get(channels.getChannel(), IOException.class);
      fail("Should not have been able to connect");
    } catch (IOException e) {
      //expected
    }

    // verify that we try all the channels exactly once
    Mockito.verify(channelManager).connect(servers.get(0));
    Mockito.verify(channelManager).connect(servers.get(1));
  }

  /**
   * Client should not hang forever if there is no server available
   */
  @Test
  public void startNoServer() throws Exception {
    ClientChannels channels = new ClientChannels(Arrays.asList("localhost"), 12345,
        Mockito.mock(MessageMapper.class), new ArrayList<ProtobufServiceTranslator>(),
        new RequestManager());

    try {
      Futures.get(channels.getChannel(), IOException.class);
      fail("Should have failed to get a channel to a server that doesn't exist with " + channels);
    } catch (IOException e) {
      //expected
    }
  }
}