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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.rpc.client.ClientChannelInitializer;
import com.salesforce.keystone.coyote.rpc.client.request.RequestManager;
import com.salesforce.keystone.coyote.rpc.client.util.ExposedListenableFuture;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceTranslator;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Channels returned from this factory are <b>NOT Thread Safe</b>. They require external
 * synchronization around messages sent in the channel. Otherwise, interleaved messages will be
 * returned to the wrong caller.
 */
public class ClientChannelFactory<T extends Message> implements AutoCloseable {

  private static final Log LOG = LogFactory.getLog(ClientChannelFactory.class);

  private final ClientChannelInitializer init;
  private final List<ProtobufServiceTranslator> services;
  private final RequestManager requestManager;
  private EventLoopGroup group = new NioEventLoopGroup(0, new DefaultThreadFactory
      ("ClientChannel", false, Thread.NORM_PRIORITY));
  private List<Channel> channels = new ArrayList<>();

  public ClientChannelFactory(List<ProtobufServiceTranslator> services,
      ClientChannelInitializer initializer, RequestManager requestManager) {
    this.services = services;
    this.init = initializer;
    this.requestManager = requestManager;
  }

  @Override
  public void close() {
    for (Channel ch : channels) {
      if (ch.isOpen()) {
        ch.close();
      }
    }
    group.shutdownGracefully();
  }

  public ListenableFuture<RpcChannel> createChannel(String server, int port) {
    Bootstrap b = new Bootstrap();
    b.group(group)
        .channel(NioSocketChannel.class)
        .handler(init);

    // Make a new connection.
    final ChannelFuture channel = b.connect(server, port);

    final ExposedListenableFuture<RpcChannel> onDone = new ExposedListenableFuture<>();

    channel.addListener(new GenericFutureListener<Future<Void>>() {
      @Override
      public void operationComplete(Future<Void> future) throws Exception {
        if (!future.isSuccess()) {
          Throwable error = future.cause();
          onDone.setException(error);
          return;
        }
        Channel ch = channel.channel();

        // keep track of the channel so we can close it when we are done
        channels.add(ch);

        // wrap the channel to convert to/from protobuf
        onDone.set(new RpcChannel(ch, services, requestManager));
      }
    });

    return onDone;
  }

}