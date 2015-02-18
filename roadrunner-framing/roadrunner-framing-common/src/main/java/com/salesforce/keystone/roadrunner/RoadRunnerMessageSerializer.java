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

import com.salesforce.keystone.roadrunner.bytes.ByteAccessor;
import com.salesforce.keystone.roadrunner.bytes.ByteConverter;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeaderCodec;
import com.salesforce.keystone.roadrunner.memory.BytesManager;
import com.salesforce.keystone.roadrunner.memory.ConvertingByteManager;
import com.salesforce.keystone.roadrunner.nio.streams.ByteBufferOutputStream;

import java.io.IOException;

/**
 * Helper class to encode a {@link RoadRunnerMessage} to bytes
 */
public class RoadRunnerMessageSerializer<T> {

  private final BytesManager<ByteAccessor> manager;
  private final ByteConverter<T> converter;

  public RoadRunnerMessageSerializer(ByteConverter<T> converter, BytesManager<T> manager) {
    this.converter = converter;
    this.manager = new ConvertingByteManager<>(converter, manager);
  }

  /**
   * writes the frame header & message (but not the trailer) to a new buffer,
   * and flips the buffer so it is ready for writing
   */
  public T serialize(RoadRunnerMessage message) throws IOException {
    ByteAccessor b = manager.allocate(message.getMsgLen() + RoadRunnerHeader.HEADER_LENGTH);
    serializeTo(message, b);
    //flip it so we can write from the the buffer immediately
    b.flip();
    return converter.unwrap(b);
  }

  /**
   * writes the serialized frame header & protocol buffer message to the buffer,
   * does <b>NOT</b> flip it
   */
  ByteAccessor serializeTo(RoadRunnerMessage m, ByteAccessor b) throws IOException {
    RoadRunnerHeaderCodec.serializeHeader(m, b);
    m.getMessage().writeTo(converter.toOutputStream(b));
    return b;
  }
}