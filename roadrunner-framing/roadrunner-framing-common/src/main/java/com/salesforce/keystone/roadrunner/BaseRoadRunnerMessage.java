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
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;

import java.nio.ByteBuffer;

/**
 * Base class to handle simple actions for a {@link RoadRunnerMessage}
 */
public abstract class BaseRoadRunnerMessage implements RoadRunnerMessage{

  private final RoadRunnerHeader header;
  private final Message message;

  public BaseRoadRunnerMessage(byte messageId, long trailerLen, Message m) {
    this(new RoadRunnerHeader(RoadRunnerMessage.CURRENT_VERSION, messageId, m.getSerializedSize(),
        trailerLen), m);
  }

  public BaseRoadRunnerMessage(RoadRunnerHeader header, Message message){
    this.header = header;
    this.message = message;
  }

  @Override
  public byte getVersion() {
    return header.version;
  }

  @Override
  public byte getMsgId() {
    return header.msgId;
  }

  @Override
  public int getMsgLen() {
    return header.messageLength;
  }

  @Override
  public Message getMessage() {
    return message;
  }

  @Override
  public long getTrailerLen() {
    return header.trailerLength;
  }

  @Override
  public boolean hasTrailer() {
    return getTrailerLen() > 0;
  }

  @Override
  public void wroteTrailer(ByteBuffer b) {
    // noop
  }

  @Override
  public String toString() {
    return "RoadRunner message:[\n"+header+"]\n[message:"+message+"]";
  }
}