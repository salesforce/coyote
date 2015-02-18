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

package com.salesforce.keystone.roadrunner.header;

import com.salesforce.keystone.roadrunner.RoadRunnerMessage;
import com.salesforce.keystone.roadrunner.bytes.ByteAccessor;
import com.salesforce.keystone.roadrunner.bytes.ByteConverter;
import com.salesforce.keystone.roadrunner.exception.MessageNotSupportedException;
import com.salesforce.keystone.roadrunner.exception.MsgBodyTooLargeException;
import com.salesforce.keystone.roadrunner.exception.MsgTrailerTooLargeException;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import com.salesforce.keystone.roadrunner.options.ReadOptions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Encode/Decode a {@link RoadRunnerHeader} to/from a {@link ByteBuffer}
 */
public class RoadRunnerHeaderCodec<T> {

  private static final Log LOG = LogFactory.getLog(RoadRunnerHeaderCodec.class);
  private final ReadOptions options;
  private final MessageMapper handler;
  private final ByteConverter<T> converter;

  public RoadRunnerHeaderCodec(ReadOptions options, MessageMapper handler,
      ByteConverter<T> converter) {
    this.options = options;
    this.handler = handler;
    this.converter = converter;
  }

  /**
   * writes the header information from RoadRunnermessage to a frame header in the ByteBuffer.
   * <p> This leaves the position of the buffer after the end of the frame header,
   * making it read for writing the message data</b>
   */
  public static ByteAccessor serializeHeader(RoadRunnerMessage m, ByteAccessor b) {
    b.put(RoadRunnerMessage.CURRENT_VERSION);
    b.put(m.getMsgId());
    b.put((byte) 0);
    b.put((byte) 0);
    b.putInt(m.getMsgLen());
    b.putLong(m.getTrailerLen());
    return b;
  }

  /**
   * Decode and validate the header from the specified {@link ByteBuffer}
   * <p>Does NOT move the read index on the byte buffer. This is, at the end of calling this
   * method, the read index will be at the same place as it started. Reading occurs from the
   * start index of the buffer.</p>
   *
   * @param h header data to parse
   * @return a header object with the necessary information
   * @throws IOException if there is an issue reading the data or if the header contains
   * unsupported data (e.g. incorrect version, message type, etc).
   */
  public RoadRunnerHeader decode(T h) throws IOException {
    ByteAccessor bytes = converter.convert(h);
    int offset = bytes.position();
    byte v = bytes.get(offset + RoadRunnerHeader.OFFSET_VERSION);
    byte msgId = bytes.get(offset + RoadRunnerHeader.OFFSET_MESSAGE_ID);
    int msgLen = bytes.getInt(offset + RoadRunnerHeader.OFFSET_MESSAGE_LEN);
    long tLen = bytes.getLong(offset + RoadRunnerHeader.OFFSET_TRAILER_LEN);

    // create the simple header
    RoadRunnerHeader header = new RoadRunnerHeader(v, msgId, msgLen, tLen);

    if (LOG.isTraceEnabled()) {
      LOG.debug("Received header: " + header);
    }

    validate(header);

    return header;
  }

  protected void validate(RoadRunnerHeader header) throws IOException {
    if (header.version != RoadRunnerMessage.CURRENT_VERSION)
      throw new IOException("Read message with unexpected version# of " + header.version);

    if (!handler.supportsMessageId(header.msgId)) {
      throw new MessageNotSupportedException(header.msgId);
    }

    if (header.messageLength > options.getMaxProtoBufMessageSize()) {
      throw new MsgBodyTooLargeException(header.messageLength, options.getMaxProtoBufMessageSize());
    }

    if (header.trailerLength > options.getMaxTrailerSize()) {
      throw new MsgTrailerTooLargeException(header.trailerLength, options.getMaxTrailerSize());
    }
  }
}