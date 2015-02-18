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

import com.google.common.base.Preconditions;
import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.netty.handler.stream.roadrunner.DecodingExceptionHandler;
import com.salesforce.keystone.coyote.netty.roadrunner.NettyInboundRoadRunnerMessage;
import com.salesforce.keystone.roadrunner.exception.MessageNotSupportedException;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

/**
 * Decode the body of the com.salesforce.keystone.roadrunner.roadrunner message from a byte stream
 */
public class RoadRunnerMessageBodyDecoder extends BaseRoadRunnerSubscriber {
  private static final Log LOG = LogFactory.getLog(RoadRunnerMessageBodyDecoder.class);
  private final RoadRunnerHeader header;
  private final BodyCompleteCallback callback;

  public RoadRunnerMessageBodyDecoder(ByteBufAllocator alloc, MessageMapper mapper,
      RoadRunnerHeader header,
      BodyCompleteCallback callback, DecodingExceptionHandler handler) {
    super(alloc, mapper, handler, header.messageLength);
    this.header = header;
    this.callback = callback;
  }

  @Override
  public void doNext(ByteBuf buffer) throws IOException {
    Message message = readMessage(header, buffer);
    NettyInboundRoadRunnerMessage current = new NettyInboundRoadRunnerMessage(header, message);
    LOG.trace("Finished decoding message");
    this.subscription.cancel();
    callback.bodyComplete(current);
  }

  @Override
  public String decodingErrorMessage() {
    return "Error while reading message body";
  }

  private Message readMessage(RoadRunnerHeader header, ByteBuf buff)
      throws IOException {
    Preconditions.checkNotNull(header, "Should not be decoding the message without a header!");

    Message.Builder b = mapper.newInstance(header.msgId);
    if (b == null) {
      // this shouldn't happen unless the mapper returns different results between
      // newInstance() and supportsMsgId()
      if (LOG.isErrorEnabled()) {
        LOG.error("Mapper " + mapper + " returned inconsistent results between " +
            "newInstance() & supportsMsgId() this is a bug in the mapper implementation");
        throw new MessageNotSupportedException(header.msgId);
      }
    }

    b.clear();
    ByteBufInputStream is = new ByteBufInputStream(buff, header.messageLength);

    // actually read in the object
    if (mapper.hasExtensionRegistry()) {
      b.mergeFrom(is, mapper.getExtensionRegistry());
    } else {
      b.mergeFrom(is);
    }

    return b.build();
  }

  /**
   * Callback notified when the body of the message has been read
   */
  public interface BodyCompleteCallback {
    public void bodyComplete(NettyInboundRoadRunnerMessage message);
  }
}
