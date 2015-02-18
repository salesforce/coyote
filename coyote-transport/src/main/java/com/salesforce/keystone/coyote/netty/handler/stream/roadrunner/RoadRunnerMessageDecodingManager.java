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

package com.salesforce.keystone.coyote.netty.handler.stream.roadrunner;

import com.salesforce.keystone.coyote.netty.handler.stream.BytePublisher;
import com.salesforce.keystone.coyote.netty.handler.stream.subscriber.RoadRunnerHeaderDecoder;
import com.salesforce.keystone.coyote.netty.handler.stream.subscriber.RoadRunnerHeaderDecoder.HeaderCompleteCallback;
import com.salesforce.keystone.coyote.netty.handler.stream.subscriber.RoadRunnerMessageBodyDecoder;
import com.salesforce.keystone.coyote.netty.roadrunner.NettyInboundRoadRunnerMessage;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import com.salesforce.keystone.roadrunner.options.ReadOptions;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Handle orchestrating sending messages to the next step in the deserialization of the
 * com.salesforce.keystone.roadrunner.roadrunner message
 * <p>
 * Each stage is handled by a different subscriber. Each subscriber has a single job and then
 * signals <tt>this</tt> when its complete so it can transition to the next subscriber (state)
 * </p>
 */
// Basically, this is a simple state machine mechanism. We could look to replacing this later
// with something more robust, but this is simple enough
public class RoadRunnerMessageDecodingManager
    implements HeaderCompleteCallback,
    RoadRunnerMessageBodyDecoder.BodyCompleteCallback,
    NettyInboundRoadRunnerMessage.TrailerCompleteCallback,
    DecodingExceptionHandler {

  private static final Log LOG = LogFactory.getLog(RoadRunnerMessageDecodingManager.class);
  private final MessageMapper mapper;
  private final ByteBufAllocator alloc;
  private ChannelHandlerContext ctx;

  enum ReaderState {
    READ_HEADER,
    READ_MESSAGE,
    READ_TRAILER;
  }

  private final ReadOptions options;
  private BytePublisher publisher;
  private ReaderState state;

  public RoadRunnerMessageDecodingManager(ReadOptions opts, ByteBufAllocator alloc,
      MessageMapper mapper) {
    this.options = opts;
    this.mapper = mapper;
    this.state = ReaderState.READ_HEADER;
    this.alloc = alloc;
  }

  public void initialize(BytePublisher publisher, ChannelHandlerContext ctx) {
    this.publisher = publisher;
    subscribeToHeader();
    this.ctx = ctx;
  }

  @Override
  public void headerComplete(RoadRunnerHeader header) {
    this.state = ReaderState.READ_MESSAGE;
    this.publisher.subscribe(new RoadRunnerMessageBodyDecoder(alloc, mapper, header, this, this));
  }

  @Override
  public void bodyComplete(NettyInboundRoadRunnerMessage message) {
    if (message.getTrailerLen() > 0) {
      this.state = ReaderState.READ_TRAILER;
      message.setReadReady(this, this);
      this.publisher.subscribe(message);
    } else {
      readHeader();
    }

    // continue the message along the pipeline
    ctx.fireChannelRead(message);
  }

  @Override
  public void trailerComplete() {
    readHeader();
  }

  private void readHeader() {
    this.state = ReaderState.READ_HEADER;
    subscribeToHeader();
  }

  private void subscribeToHeader() {
    if (state != ReaderState.READ_HEADER) {
      throw new IllegalStateException("Tried to read header when not in ReadHeader state");
    }

    publisher.subscribe(new RoadRunnerHeaderDecoder(alloc, options, mapper,
        this, this));
  }

  @Override
  public void decodingException(String message, Throwable cause) {
    LOG.error(message, cause);
    // reset the context back to the header - we don't care if there is more to read or not
    readHeader();
    // write the error down the channel
    ctx.fireExceptionCaught(cause);
  }
}
