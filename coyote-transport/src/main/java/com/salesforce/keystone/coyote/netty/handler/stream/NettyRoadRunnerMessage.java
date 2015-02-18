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

package com.salesforce.keystone.coyote.netty.handler.stream;

import com.salesforce.keystone.roadrunner.RoadRunnerMessage;
import io.netty.buffer.ByteBuf;

/**
 *
 */
public interface NettyRoadRunnerMessage extends RoadRunnerMessage {

  public int getChunkSize();

  /**
   * Read the trailer into the callback asynchronously. Uses the default chunk size for the
   * message.
   *
   * @param readFully <tt>true</tt> if this should wait until the entire trailer is in memory
   * before sending it to the callback
   * @param callback to be notified when data is available. If readFully is <tt>true</tt> it is
   * only notified when the entire trailer is available
   */
  public void readTrailer(boolean readFully, TrailerReader callback);

  /**
   * Read the trailer into the callback asynchronously. By default readFully is <tt>false</tt>.
   *
   * @param chunkSize number of bytes per chunk read. This can be less than the full trailer
   * size, even if readFully is <tt>true</tt>, it just decreases the number of bytes read in each
   * back to build up the full buffer
   * @param callback to be notified when data is available. If readFully is <tt>true</tt> it is
   * only notified when the entire trailer is available
   */
  public void readTrailer(int chunkSize, TrailerReader callback);

  /**
   * Read trailer data in chunks
   */
  interface TrailerReader {
    /**
     * Chunk of the trailer to read
     *
     * @param trailer to read
     * @param isLast <tt>true</tt> if this is the last chunk of the trailer
     */
    public void read(ByteBuf trailer, boolean isLast);
  }
}
