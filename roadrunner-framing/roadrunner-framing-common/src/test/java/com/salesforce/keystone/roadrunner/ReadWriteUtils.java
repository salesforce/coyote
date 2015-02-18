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
import com.salesforce.keystone.coyote.protobuf.generated.TestMessage;
import com.salesforce.keystone.roadrunner.bytes.ByteAccessor;
import com.salesforce.keystone.roadrunner.bytes.ByteBufferConverter;
import com.salesforce.keystone.roadrunner.bytes.ByteConverter;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeaderCodec;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapperImpl;
import com.salesforce.keystone.roadrunner.options.ReadOptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class ReadWriteUtils {
  private ReadWriteUtils() {
    // private ctor util class
  }

  private static Message protoMessage = TestMessage.Test.newBuilder().setVar1(false).build();
  public static final int messageId = 11;
  public static final MessageMapper mapper;

  static {
    Map<Class<? extends Message>, Integer> t = new HashMap<>();
    t.put(TestMessage.Test.class, messageId);
    mapper = new MessageMapperImpl(t);
  }

  public static RoadRunnerHeader getHeader() {
    return new RoadRunnerHeader(RoadRunnerMessage.CURRENT_VERSION, (byte) messageId,
        protoMessage.getSerializedSize(), 0);
  }

  public static RoadRunnerMessage getMessage(RoadRunnerHeader header) {
    // create a simple message
    RoadRunnerMessage msg = new BaseRoadRunnerMessage(header, protoMessage) {
      @Override
      public Iterator<ByteBuffer> trailerDataForWriting() {
        return null;
      }

      @Override
      public void wroteTrailer(ByteBuffer b) {
      }
    };

    return msg;
  }

  public static <T> RoadRunnerHeader readAndVerifyHeader(ByteConverter<T> converter, T bytes,
      RoadRunnerHeader header) throws IOException {
    ReadOptions options = new ReadOptions.Builder().build();
    ByteAccessor accessor = converter.convert(bytes);
    int startPosition = accessor.position();

    RoadRunnerHeaderCodec<T> codec = new RoadRunnerHeaderCodec(options, mapper, converter);

    RoadRunnerHeader receivedHeader = codec.decode(bytes);
    // check the header
    assertEquals("Read header doesn't match original!", header, receivedHeader);
    // ensure the read index is has not been modified
    assertEquals("Buffer position changed while reading the header!", startPosition,
        accessor.position());

    return receivedHeader;
  }

  public static RoadRunnerMessageSerializer<ByteBuffer> getNativeEncoder() {
    return new RoadRunnerMessageSerializer(ByteBufferConverter.INSTANCE,
        new SimpleMemoryManager());
  }

  public static <T> void readAndVerifyMessage(RoadRunnerMessageDeserializer<T> deserializer,
      T buf, RoadRunnerHeader header, Message msg) throws IOException {
    Message receivedMessage = deserializer.deserialize(header, buf);
    assertEquals(msg, receivedMessage);
  }

  public static void readAndVerifyNative(ByteBuffer buf, RoadRunnerHeader header,
      RoadRunnerMessage msg) throws IOException {
    // read in the header from the buffer
    RoadRunnerHeader receivedHeader = ReadWriteUtils.readAndVerifyHeader(
        ByteBufferConverter.INSTANCE, buf, header);

    // verify the message
    buf.position(RoadRunnerHeader.HEADER_LENGTH);
    RoadRunnerMessageDeserializer<ByteBuffer> deserializer = new RoadRunnerMessageDeserializer
        (ByteBufferConverter.INSTANCE, ReadWriteUtils.mapper);
    ReadWriteUtils.readAndVerifyMessage(deserializer, buf, receivedHeader, msg.getMessage());
  }
}