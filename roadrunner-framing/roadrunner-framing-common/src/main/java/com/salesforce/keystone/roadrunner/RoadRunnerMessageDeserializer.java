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

package com.salesforce.keystone.roadrunner;

import com.google.protobuf.Message;
import com.salesforce.keystone.roadrunner.bytes.ByteConverter;
import com.salesforce.keystone.roadrunner.exception.MessageNotSupportedException;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import com.salesforce.keystone.roadrunner.nio.streams.ByteBufferInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serialize a road runner message from the
 */
public class RoadRunnerMessageDeserializer<T> {

  private static final Log LOG = LogFactory.getLog(RoadRunnerMessageDeserializer.class);

  private final ByteConverter<T> converter;
  private final MessageMapper mapper;

  public RoadRunnerMessageDeserializer(ByteConverter<T> converter, MessageMapper mapper) {
    this.converter = converter;
    this.mapper = mapper;
  }

  /**
   * Deserialize the protobuf message in the message body
   *
   * @param header information about the message
   * @param bytes bytes to read. assumed to be in the correct position to read the message body
   * (e.g. skipped past the header)
   * @return the deserialized message from the bytes, with the bytes incremented past the header
   * @throws IOException if the message cannot be read
   */
  public Message deserialize(RoadRunnerHeader header, T bytes)
      throws IOException {
    InputStream is = converter.toInputStream(bytes);
    Message.Builder b = mapper.newInstance(header.msgId);
    if (b == null) {
      // this shouldn't happen unless the mapper returns different results between
      // newInstance() and supportsMsgId()
      if (LOG.isErrorEnabled()) {
        LOG.error("Mapper " + mapper + " returned inconsistent results between " +
            "newInstance() & supportsMsgId() this is a bug in the mapper implementation");
      }
      throw new MessageNotSupportedException(header.msgId);
    }
    b.clear();
    if (mapper.hasExtensionRegistry()) {
      b.mergeFrom(is, mapper.getExtensionRegistry());
    } else {
      b.mergeFrom(is);
    }

    Message current = b.build();

    if (LOG.isDebugEnabled()) {
      LOG.info("received msgId=" + header.msgId + ", type=" + current.getClass()
          .getSimpleName() + ", trailerLen=" + header.trailerLength);
    }
    return current;
  }
}