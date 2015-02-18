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

package com.salesforce.keystone.coyote.rpc.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.BindException;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of the RPC server backed by netty but using road-runner framed messages
 */
public class NettyRpcServer {

  public static final int RANDOM_PORT = -1;
  private static final Log LOG = LogFactory.getLog(NettyRpcServer.class);
  private static final int MAX_CONNECT_ATTEMPTS = 10;
  /* One boss thread to handle incoming requests as pass them off to workers */
  public static final int DEFAULT_BOSS_THREADS = 1;
  /**
   * Unlimited number of worker threads
   */
  public static final int DEFAULT_WORKER_THREADS = 0;

  private int port;
  private final EventLoopGroup bossGroup;
  private final EventLoopGroup workerGroup;
  /**
   * maximum number of times that the server should attempt to connect to the port,
   * before giving up
   */
  private final int connectAttempts;
  private ChannelFuture closeFuture;

  public NettyRpcServer(){
    this(RANDOM_PORT);
  }

  public NettyRpcServer(int port) {
    this(port, MAX_CONNECT_ATTEMPTS, DEFAULT_BOSS_THREADS, DEFAULT_WORKER_THREADS);
  }

  public NettyRpcServer(int port, int maxConnectAttempts, int bossThreads, int workerThreads) {
    this.port = port;
    this.connectAttempts = maxConnectAttempts;
    bossGroup = new NioEventLoopGroup(bossThreads, new DefaultThreadFactory
        ("Server-BossChannel", false, Thread.NORM_PRIORITY));
    workerGroup = new NioEventLoopGroup(workerThreads, new DefaultThreadFactory
        ("Server-WorkerChannel", false, Thread.NORM_PRIORITY));
  }

  /**
   * Attempt to start the RPC server
   *
   * @throws IOException if the server cannot be started, likely because the port is already in
   * use
   * @throws InterruptedException we were interrupted while waiting for the channel to become
   * active, which is probably an external request to shutdown, so we return before the channel
   * finishes completing the bind
   */
  public void start(ChannelInitializer<SocketChannel> initializer)
      throws IOException, InterruptedException {
    io.netty.bootstrap.ServerBootstrap b = new io.netty.bootstrap.ServerBootstrap();
    b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .handler(new io.netty.handler.logging.LoggingHandler(
            io.netty.handler.logging.LogLevel.INFO))
        .childHandler(initializer)
            // incomming connection options
        .option(ChannelOption.SO_BACKLOG, 128)
            // channel options for children created by incomming requests
        .childOption(ChannelOption.SO_KEEPALIVE, true)
        .childOption(ChannelOption.TCP_NODELAY, true);
    bind(b);
  }

  /**
   * Bind and start to accept incoming connections.
   */
  private void bind(io.netty.bootstrap.ServerBootstrap bootstrap) throws BindException {
    boolean randomPort = port < 0;
    for (int attempts = 0; attempts < connectAttempts; attempts++) {
      // pick a random port
      if (randomPort) {
        port = 49152 + new Random().nextInt(16000);
      }

      ChannelFuture future = null;
      try {
        future = bootstrap.bind(port);
        Channel channel = future.sync().channel();
        this.closeFuture = channel.closeFuture();
        LOG.info("Started RPC on port: " + port);
        return;
      } catch (InterruptedException e) {
        LOG.error("Interrupted while starting rpc server, most likely an external attempt to " +
            "shutdown before starting", e);
        future.cancel(true);
      } catch (Exception e) {
        // java.net.BindException is only ever thrown, but we have to catch all of them because
        LOG.error("Failed to create RpcServer", e);
        if (!randomPort || attempts > MAX_CONNECT_ATTEMPTS) {
          throw e;
        }
      }
    }
  }

  public void join() throws ExecutionException, InterruptedException {
    this.closeFuture.sync();
  }

  /**
   * Wait for the server to shutdown for the given amount of time
   *
   * @param i amount to wait
   * @param timeUnit unit for the time to wait
   * @return <tt>true</tt> if we shutdown in the specified amount of time
   */
  public boolean await(int timeout, TimeUnit timeUnit) throws InterruptedException {
    return this.closeFuture.await(timeout, timeUnit);
  }

  /**
   * Stop the server and request handlers.
   * <p>
   * Blocks until the server is shutdown
   * </p>
   *
   * @return list of futures to wait on for the server to complete shutdown
   */
  public void stop() {
    shutdownGroup(bossGroup, "bosses");
    shutdownGroup(workerGroup, "workers");
  }

  /**
   * Attempt to hard shutdown the server. At best, attempts to interrupt the running threads.
   */
  public void shutdownNow() {
    bossGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
    workerGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
  }

  private void shutdownGroup(EventLoopGroup group, String type) {
    try {
      Future<?> future = group.shutdownGracefully();
      future.get();
    } catch (CancellationException | InterruptedException | ExecutionException e) {
      LOG.error("Exception while waiting for the " + type + " group to shutdown, ignoring", e);
    }
  }

  public int getPort() {
    return port;
  }
}