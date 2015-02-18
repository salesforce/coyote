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

package com.salesforce.keystone.coyote;

import com.salesforce.keystone.roadrunner.ReadWriteUtils;
import com.salesforce.keystone.roadrunner.RoadRunnerMessage;
import com.salesforce.keystone.roadrunner.RoadRunnerMessageDeserializer;
import com.salesforce.keystone.roadrunner.RoadRunnerMessageSerializer;
import com.salesforce.keystone.roadrunner.SimpleMemoryManager;
import com.salesforce.keystone.roadrunner.bytes.ByteBufferConverter;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;
import com.salesforce.keystone.roadrunner.memory.BytesManager;
import com.salesforce.keystone.roadrunner.nio.streams.ByteBufferOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * Test that both netty-netty and netty-native operations work correctly
 */
public class TestReadWriteNettyMessage {

  @Test
  public void testNettyToNetty() throws Exception {
    // Create a simple RR message without a trailer
    RoadRunnerHeader header = ReadWriteUtils.getHeader();
    RoadRunnerMessage msg = ReadWriteUtils.getMessage(header);

    // write the message to a byte buffer
    RoadRunnerMessageSerializer<ByteBuf> encoder = getNettyEncoder();
    ByteBuf buf = encoder.serialize(msg);

    readAndVerifyNetty(buf, header, msg);
  }

  @Test
  public void testNettyToNative() throws Exception {
    // Create a simple RR message without a trailer
    RoadRunnerHeader header = ReadWriteUtils.getHeader();
    RoadRunnerMessage msg = ReadWriteUtils.getMessage(header);

    // write the message to a netty output
    RoadRunnerMessageSerializer<ByteBuf> encoder = getNettyEncoder();
    ByteBuf buf = encoder.serialize(msg);

    // write it out to a byte buffer
    SimpleMemoryManager memoryManager = new SimpleMemoryManager();
    ByteBuffer buffer = memoryManager.allocate(buf.readableBytes());
    ByteBufferOutputStream out = new ByteBufferOutputStream(buffer);
    buf.readBytes(out, buf.readableBytes());

    // verify the native bytes
    buffer.flip();
    ReadWriteUtils.readAndVerifyNative(buffer, header, msg);
  }

  @Test
  public void testNativeToNetty() throws Exception {
    // Create a simple RR message without a trailer
    RoadRunnerHeader header = ReadWriteUtils.getHeader();
    RoadRunnerMessage msg = ReadWriteUtils.getMessage(header);

    // write the message to a byte buffer
    RoadRunnerMessageSerializer<ByteBuffer> encoder = new RoadRunnerMessageSerializer<ByteBuffer>
        (ByteBufferConverter.INSTANCE, new SimpleMemoryManager());
    ByteBuffer buffer = encoder.serialize(msg);

    // write the buffer into a netty buf
    BytesManager<ByteBuf> memoryManager = getNettyMemoryManager();
    ByteBuf buf = memoryManager.allocate(buffer.remaining());
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    buf.writeBytes(bytes);

   readAndVerifyNetty(buf, header, msg);
  }

  private void readAndVerifyNetty(ByteBuf buf, RoadRunnerHeader header,
      RoadRunnerMessage msg) throws Exception{
    // read in the header from the buffer
    RoadRunnerHeader receivedHeader = ReadWriteUtils.readAndVerifyHeader(
        ByteBufToAccessor.INSTANCE, buf, header);
    // read in the message body
    buf.readerIndex(RoadRunnerHeader.HEADER_LENGTH);
    RoadRunnerMessageDeserializer<ByteBuf> deserializer = getNettyDeserializer();
    ReadWriteUtils.readAndVerifyMessage(deserializer, buf, receivedHeader, msg.getMessage());
  }

  private RoadRunnerMessageSerializer<ByteBuf> getNettyEncoder() {
    return new RoadRunnerMessageSerializer<ByteBuf>(ByteBufToAccessor.INSTANCE,
        getNettyMemoryManager());
  }

  private RoadRunnerMessageDeserializer<ByteBuf> getNettyDeserializer(){
    return new RoadRunnerMessageDeserializer(ByteBufToAccessor.INSTANCE, ReadWriteUtils.mapper);
  }

  private BytesManager<ByteBuf> getNettyMemoryManager(){
    return new HeapByteBufMemoryManger(new UnpooledByteBufAllocator(false));
  }
}