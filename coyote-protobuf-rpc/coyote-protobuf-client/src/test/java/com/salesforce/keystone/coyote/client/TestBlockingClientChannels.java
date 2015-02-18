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

import com.google.common.base.Function;
import com.google.protobuf.BlockingRpcChannel;
import com.salesforce.keystone.coyote.rpc.client.channel.ChannelManager;
import com.salesforce.keystone.coyote.rpc.client.channel.RpcChannel;
import com.salesforce.keystone.coyote.rpc.client.connection.ServerName;
import com.salesforce.keystone.coyote.rpc.client.sync.BlockingClientChannels;
import com.salesforce.keystone.coyote.rpc.client.util.ExposedListenableFuture;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test that we create the connections that we expect when we wrap the async channels into
 * blocking channels
 */
public class TestBlockingClientChannels {

  private static final Log LOG = LogFactory.getLog(TestBlockingClientChannels.class);

  /**
   * Test that we connect to a specific channel only when the future returns
   */
  @Test
  public void testConnectToAnyChannel() throws Exception {
    final List<ServerName> servers = new ArrayList<>(2);
    servers.add(new ServerName("a", 1));
    servers.add(new ServerName("b", 2));

    ChannelManager channelManager = Mockito.mock(ChannelManager.class);
    final BlockingClientChannels channels =
        new BlockingClientChannels(ChannelTestUtil.getLocationManager(servers), channelManager,
            false);

    getAndVerifyChannel(servers.get(0), channelManager, new GetAnyServerChannel(channels));
    getAndVerifyChannel(servers.get(1), channelManager, new GetAnyServerChannel(channels));

    // verify that we try all the channels exactly once
    Mockito.verify(channelManager).connect(servers.get(0));
    Mockito.verify(channelManager).connect(servers.get(1));
  }

  private void getAndVerifyChannel(final ServerName server, ChannelManager channelManager,
      final Function<ServerName,
          BlockingRpcChannel> getChannel) throws
      InterruptedException {
    ExposedListenableFuture<RpcChannel> future = new ExposedListenableFuture<>();
    RpcChannel channel = Mockito.mock(RpcChannel.class);
    Mockito.when(channelManager.connect(Mockito.any(ServerName.class))).thenReturn(future);

    // get the first server
    final CountDownLatch done = new CountDownLatch(1);
    final BlockingRpcChannel[] holder = new BlockingRpcChannel[1];
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          holder[0] = getChannel.apply(server);
        } catch (Exception e) {
          LOG.error("Got an exception while trying to get channel", e);
        } finally {
          done.countDown();
        }
      }
    };

    // get the channel
    t.start();

    // make sure it hasn't been found yet
    assertNull(holder[0]);

    // connected to the channel
    future.set(channel);

    // wait for the blocking channel to get passed
    done.await();

    // make sure we got the channel and not an exception
    assertNotNull(holder[0]);
  }

  @Test
  public void testConnectToSpecificChannel() throws Exception {
    final List<ServerName> servers = new ArrayList<>(2);
    servers.add(new ServerName("a", 1));
    servers.add(new ServerName("b", 2));

    ChannelManager channelManager = Mockito.mock(ChannelManager.class);
    final BlockingClientChannels channels =
        new BlockingClientChannels(ChannelTestUtil.getLocationManager(servers), channelManager,
            false);

    getAndVerifyChannel(servers.get(0), channelManager, new GetSpecificServerChannel(channels));

    // verify that we try all the channels exactly once
    Mockito.verify(channelManager).connect(servers.get(0));
  }

  private class GetSpecificServerChannel implements Function<ServerName, BlockingRpcChannel> {
    private final BlockingClientChannels channels;

    public GetSpecificServerChannel(BlockingClientChannels channels) {
      this.channels = channels;
    }

    @Override
    public BlockingRpcChannel apply(ServerName input) {
      try {
        return channels.getBlockingChannel(input);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private class GetAnyServerChannel implements Function<ServerName, BlockingRpcChannel> {
    private final BlockingClientChannels channels;

    public GetAnyServerChannel(BlockingClientChannels channels) {
      this.channels = channels;
    }

    @Override
    public BlockingRpcChannel apply(ServerName input) {
      try {
        return channels.getBlockingChannel();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}