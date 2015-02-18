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

package com.salesforce.keystone.coyote.netty.handler.stream.subscriber;

import com.salesforce.keystone.coyote.ByteBufToAccessor;
import com.salesforce.keystone.coyote.netty.handler.stream.roadrunner.DecodingExceptionHandler;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeaderCodec;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import com.salesforce.keystone.roadrunner.options.ReadOptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Decodes the header from a stream of bytes
 */
public class RoadRunnerHeaderDecoder extends BaseRoadRunnerSubscriber {
  private static final Log LOG = LogFactory.getLog(RoadRunnerHeaderDecoder.class);
  private final HeaderCompleteCallback callback;
  private final RoadRunnerHeaderCodec<ByteBuf> decoder;

  public RoadRunnerHeaderDecoder(ByteBufAllocator alloc, ReadOptions options,
      MessageMapper mapper, HeaderCompleteCallback callback, DecodingExceptionHandler handler) {
    super(alloc, mapper, handler, RoadRunnerHeader.HEADER_LENGTH);
    this.decoder = new RoadRunnerHeaderCodec(options, mapper, ByteBufToAccessor.INSTANCE);
    this.callback = callback;
  }

  @Override
  public void doNext(ByteBuf buffer) throws Exception {
    // decode the message
    RoadRunnerHeader header = decoder.decode(buffer);
    // move the pointer forward on the buffer
    buffer.readerIndex(RoadRunnerHeader.HEADER_LENGTH);

    LOG.trace("Finished decoding header");
    this.subscription.cancel();
    this.callback.headerComplete(header);
  }

  @Override
  public String decodingErrorMessage() {
    return "Failed to decode header correctly";
  }

  public interface HeaderCompleteCallback {
    public void headerComplete(RoadRunnerHeader header);
  }
}
