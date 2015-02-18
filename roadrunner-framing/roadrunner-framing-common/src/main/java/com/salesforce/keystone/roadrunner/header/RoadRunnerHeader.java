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

/**
 * Description of a parsed RoadRunner header
 */
public class RoadRunnerHeader {
  // the location of the trailer and ByteBuffer size of long
  public static final int HEADER_LENGTH = 16;
  public static final int OFFSET_VERSION = 0;
  public static final int OFFSET_MESSAGE_ID = 1;
  public static final int OFFSET_MESSAGE_LEN = 4;
  public static final int OFFSET_TRAILER_LEN = 8;

  public final byte version;
  public final byte msgId;
  public final int messageLength;
  public final long trailerLength;

  public RoadRunnerHeader(byte version, byte msgId, int messageLength, long trailerLength) {
    this.version = version;
    this.msgId = msgId;
    this.messageLength = messageLength;
    this.trailerLength = trailerLength;
  }

  @Override
  public String toString() {
    return "RoadRunner header with v=" + version + ", msgId=" + msgId + ", " +
        "msgLen=" + messageLength + ", " + "trailerLen=" + trailerLength;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RoadRunnerHeader that = (RoadRunnerHeader) o;

    if (messageLength != that.messageLength) return false;
    if (msgId != that.msgId) return false;
    if (trailerLength != that.trailerLength) return false;
    if (version != that.version) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (int) version;
    result = 31 * result + (int) msgId;
    result = 31 * result + messageLength;
    result = 31 * result + (int) (trailerLength ^ (trailerLength >>> 32));
    return result;
  }
}