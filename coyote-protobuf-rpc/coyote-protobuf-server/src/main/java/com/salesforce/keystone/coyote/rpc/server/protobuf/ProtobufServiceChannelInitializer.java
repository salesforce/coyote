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

package com.salesforce.keystone.coyote.rpc.server.protobuf;

import com.salesforce.keystone.coyote.netty.handler.stream.BytePublisher;
import com.salesforce.keystone.coyote.netty.handler.stream.roadrunner.RoadRunnerMessageDecodingManager;
import com.salesforce.keystone.coyote.netty.handler.stream.roadrunner.RoadRunnerMessageDeserializer;
import com.salesforce.keystone.coyote.rpc.RoadRunnerMessageEncoder;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceInitializer;
import com.salesforce.keystone.coyote.rpc.server.ServerExceptionHandler;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import com.salesforce.keystone.roadrunner.options.ReadOptions;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.util.concurrent.ExecutorService;

/**
 * Initialize the actual RPC channel when it is opened to hand off requests to the protobuf
 * services configured in the ProtobufServiceInitializer
 */
public class ProtobufServiceChannelInitializer extends ChannelInitializer<SocketChannel> {

  private final RoadRunnerMessageEncoder encoder;
  private final ExecutorService handlers;
  private final ProtobufServiceInitializer services;
  private final MessageMapper msgMapper;

  public ProtobufServiceChannelInitializer(ExecutorService handlerPool, ProtobufServiceInitializer
      initializer, MessageMapper mapper) {
    this.handlers = handlerPool;
    this.services = initializer;

    this.msgMapper = mapper;
    //TODO make this configurable
    this.encoder = new RoadRunnerMessageEncoder(true, mapper);
  }

  @Override
  protected void initChannel(SocketChannel socketChannel) throws Exception {
    ChannelPipeline pipeline = socketChannel.pipeline();
    // ----- Outbound handlers ------
    // visited in reverse order on the way back out

    // stream the data in chunks, if necessary
    pipeline.addLast("outbound-streamer", new ChunkedWriteHandler());

    // take the result and convert it back into a rr message
    pipeline.addLast("outbound-messageEncoder", encoder);

    // ----- Inbound handlers ------
    // Visited in order, as the message works its way in
    // They must be after the outbound so we wind back through those on the way back
    // out with the response

    // deserializing the message, with standard read options
    RoadRunnerMessageDecodingManager decoder =
        new RoadRunnerMessageDecodingManager(new ReadOptions.Builder().build(),
            socketChannel.alloc(), msgMapper);
    // setup the publisher which manages streaming the actual bytes to the decoder
    BytePublisher publisher =
        new RoadRunnerMessageDeserializer(decoder);
    pipeline.addLast("inbound-messageDecoder", publisher);

    // then dispatch the message to the appropriate rpc handler
    pipeline.addLast("inbound-messageHandler",
        new ProtobufServiceHandler(this.services, this.handlers));

    // some error handling so we can manage any exceptions that arise from the message handling
    pipeline.addLast("exceptionHandler", new ServerExceptionHandler());

  }
}