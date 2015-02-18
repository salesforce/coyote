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
import com.salesforce.keystone.roadrunner.bytes.ByteBufferConverter;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeaderCodec;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapperImpl;
import com.salesforce.keystone.roadrunner.options.ReadOptions;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Test that messages can be read/written correctly
 */
public class TestReadWriteMessage {

  @Test
  public void simpleReadWriteHeaderAndMessage() throws Exception {
    // Create a simple RR message without a trailer
    RoadRunnerHeader header = ReadWriteUtils.getHeader();
    RoadRunnerMessage msg = ReadWriteUtils.getMessage(header);

    // write the message to a byte buffer
    RoadRunnerMessageSerializer<ByteBuffer> encoder = ReadWriteUtils.getNativeEncoder();
    ByteBuffer buf = encoder.serialize(msg);

    ReadWriteUtils.readAndVerifyNative(buf, header, msg);
  }
}