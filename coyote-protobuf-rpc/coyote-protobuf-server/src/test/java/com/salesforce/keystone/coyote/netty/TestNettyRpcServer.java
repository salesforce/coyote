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

package com.salesforce.keystone.coyote.netty;

import com.salesforce.keystone.coyote.rpc.server.NettyRpcServer;
import io.netty.channel.ChannelInitializer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

/**
 * Test simple start/stop of the rpc server
 */
public class TestNettyRpcServer {

  private static final Log LOG = LogFactory.getLog(TestNettyRpcServer.class);

  @Test
  public void testStartStop() throws Exception {
    final NettyRpcServer server = startServer();

    final CountDownLatch latch = new CountDownLatch(1);
    new Thread() {
      @Override
      public void run() {
        try {
          server.join();
        } catch (Exception e) {
          LOG.error("failed to join successfully", e);
        }
        latch.countDown();
      }
    }.start();

    server.stop();
    latch.await(10, TimeUnit.SECONDS);
  }

  private NettyRpcServer startServer() throws IOException, InterruptedException {
    final NettyRpcServer server = new NettyRpcServer();
    ChannelInitializer init = Mockito.mock(ChannelInitializer.class);
    server.start(init);
    return server;
  }

  /**
   * Two servers shouldn't be able to start on the same port
   *
   * @throws Exception on failure
   */
  @Test
  public void startTwoServersOnSamePort() throws Exception {
    final NettyRpcServer server = new NettyRpcServer();
    ChannelInitializer init = Mockito.mock(ChannelInitializer.class);

    server.start(init);

    // start the second server on the same port
    int port = server.getPort();
    NettyRpcServer server1 = new NettyRpcServer(port, 1,
        NettyRpcServer.DEFAULT_BOSS_THREADS, NettyRpcServer.DEFAULT_WORKER_THREADS);
    try {
      server1.start(init);
      fail("Second server shouldn't have been able to start on the same port");
    } catch (IOException e) {
      //expected
    }

    server.stop();
    server.join();
  }

  /**
   * Two RPC servers should be able to start at the same time if they are specified to run on
   * random ports
   *
   * @throws Exception on failure
   */
  @Test
  public void startTwoServersOnRandomPorts() throws Exception {
    final NettyRpcServer server = startServer();
    NettyRpcServer server2 = startServer();
    // wait for the servers to stop
    server.stop();
    server.join();

    server2.stop();
    server2.join();
  }
}