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

import com.google.common.base.Preconditions;
import com.salesforce.keystone.coyote.netty.handler.stream.NettyRoadRunnerMessage.TrailerReader;
import io.netty.buffer.ByteBuf;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CountDownLatch;

/**
 * A {@link TrailerAccessorInterface} that is backed by a Netty stream
 */
public class NettyTrailerAccessor implements TrailerAccessorInterface {

  private static final Log LOG = LogFactory.getLog(NettyTrailerAccessor.class);

  private final NettyRoadRunnerMessage message;
  private final int chunkSize;

  public NettyTrailerAccessor(NettyRoadRunnerMessage receivedMessage, int chunkSize) {
    this.message = receivedMessage;
    this.chunkSize = chunkSize;
  }

  @Override
  public long getTrailerLength() {
    return message.getTrailerLen();
  }

  @Override
  public byte[] writeOut(int length) throws IOException, InterruptedException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(length);
    writeOut(out);
    return out.toByteArray();
  }

  @Override
  public void writeOut(OutputStream out) throws IOException, InterruptedException {
    readExtentData(out);
  }

  /**
   * Read extent data from the channel.
   * <p>
   * The extent data is written to the supplied {@link WritableByteChannel}. The channel will not
   * be closed when the call returns.
   * <p>
   * This should only be called if extent data is pending.
   * <p>
   * This is a blocking call.
   * <p>
   *
   * @param outputStream a {@link WritableByteChannel} where the extent data will be written
   * @throws IOException if an error occurs while reading the extent data from the network or
   * writing it to the channel
   */
  private void readExtentData(OutputStream outputStream) throws IOException,
      InterruptedException {
    Preconditions.checkNotNull(outputStream);
    Preconditions.checkState(this.message.getTrailerLen() > 0);

    readTrailerIntoChannel(outputStream);
  }

  /**
   * Read part of the extent data from the RR message into the channel.
   * <p>
   * The extent data is written to the supplied {@link WritableByteChannel}. The channel will not
   * be closed when the call returns.
   * <p>
   * This should only be called if extent data is pending.
   * <p>
   * This is a blocking call.
   * <p>
   *
   * @param extentChannel a {@link WritableByteChannel} where the extent data will be written
   * @param bytes the amount of the extent data to read
   * @return true if there is more data available
   * @throws IOException if an error occurs while reading the extent data from the network or
   * writing it to the channel
   */
  private void readTrailerIntoChannel(final OutputStream trailer) throws
      InterruptedException {
    final CountDownLatch completed = new CountDownLatch(1);
    message.readTrailer(chunkSize, new TrailerReader() {
      @Override
      public void read(ByteBuf bytes, boolean isLast) {
        try {
          bytes.readBytes(trailer, bytes.writableBytes());
          if (isLast) {
            completed.countDown();
          }
        } catch (IOException exc) {
          LOG.error("Got an exception writing trailer!");
              throw new RuntimeException(exc);
        }
      }
    });
    completed.await();
  }
}