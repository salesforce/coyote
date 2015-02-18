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

package com.salesforce.keystone.coyote.rpc;

import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.ByteBufMemoryManager;
import com.salesforce.keystone.coyote.ByteBufToAccessor;
import com.salesforce.keystone.coyote.DirectByteBufMemoryManager;
import com.salesforce.keystone.coyote.HeapByteBufMemoryManger;
import com.salesforce.keystone.coyote.netty.roadrunner.NettyOutboundRoadRunnerMessage;
import com.salesforce.keystone.coyote.rpc.protobuf.RpcRequestResponse;
import com.salesforce.keystone.roadrunner.RoadRunnerMessageSerializer;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.stream.ChunkedStream;

import java.net.SocketAddress;

/**
 * Convert a RpcMessage to a RoadRunner message
 */
@ChannelHandler.Sharable
public class RoadRunnerMessageEncoder<T extends Message, B> extends ChannelOutboundHandlerAdapter {

  private final MessageMapper mapper;
  private final boolean preferDirect;
  private RoadRunnerMessageSerializer<ByteBuf> serializer;
  private ByteBufMemoryManager memoryManager;

  public RoadRunnerMessageEncoder(boolean preferDirect, MessageMapper mapper) {
    this.preferDirect = preferDirect;
    this.mapper = mapper;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    createSeralizer(ctx);
    super.handlerAdded(ctx);
  }

  private void createSeralizer(ChannelHandlerContext ctx){
    // create the bytebuffer memory manager based on whether or not we prefer a direct buffer
    if (preferDirect) {
      this.memoryManager = new DirectByteBufMemoryManager(ctx.alloc());
    } else {
      this.memoryManager = new HeapByteBufMemoryManger(ctx.alloc());
    }

    this.serializer =
        new RoadRunnerMessageSerializer(ByteBufToAccessor.INSTANCE, this.memoryManager);
  }


  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
      throws Exception {
    // pass along if we don't want to handle the message
    if (!(msg instanceof RpcRequestResponse)) {
      ctx.write(msg, promise);
      return;
    }

    RpcRequestResponse response = (RpcRequestResponse) msg;
    writeHeader(ctx, response);

    // write the rest of the stream to be handled by the chunked writer
    if (response.getData() != null) {
      //TODO support setting a chunking parameter
      ctx.write(new ChunkedStream(response.getData()), ctx.voidPromise());
    }

    // flush the channel
    ctx.flush();
  }

  private void writeHeader(final ChannelHandlerContext ctx, RpcRequestResponse response)
      throws Exception {
    // build the message
    NettyOutboundRoadRunnerMessage msg = build(response, mapper);
    // serialize the header + proto message to the buffer. We don't need to free it later - that
    // is handled by Netty
    final ByteBuf out = serializer.serialize(msg);
    // write the bytes
    ChannelFuture future = ctx.write(out);

    // cleanup the header buffer once its written
    future.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (!future.isSuccess()) {
          //TODO support writing the exception to the client
          // close the channel and rethrow the error - this just logs it. The exception isn't
          // propagated out since its in the write side, so this is the best we can do for right now
          ctx.close();
          Throwable t = future.cause();
          if (t instanceof Exception) {
            throw (Exception) t;
          } else {
            throw new Exception(t);
          }
        }
      }
    });
  }

  private NettyOutboundRoadRunnerMessage build(RpcRequestResponse response, MessageMapper mapper) {
    // convert into a simple RR message
    Message message = response.getMessage();
    long dataSize = response.getData() == null ? 0 : response.getDataSize();
    return new NettyOutboundRoadRunnerMessage(mapper.messageIdOf(response.getMessage(), false),
        message, dataSize);
  }
}