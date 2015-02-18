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

package com.salesforce.keystone.coyote.netty.stream;

import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.netty.handler.stream.BytePublisher;
import com.salesforce.keystone.coyote.netty.handler.stream.NettyRoadRunnerMessage.TrailerReader;
import com.salesforce.keystone.coyote.netty.handler.stream.roadrunner.DecodingExceptionHandler;
import com.salesforce.keystone.coyote.netty.roadrunner.NettyInboundRoadRunnerMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufProcessor;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.mockito.Mockito;
import org.reactivestreams.Subscriber;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test that we correctly manage streaming the trailer into the NettyInboundRoadRunnerMessage
 */
public class TestTrailerStreams {

  private static final Log LOG = LogFactory.getLog(TestTrailerStreams.class);

  private static final ByteBufAllocator alloc = new UnpooledByteBufAllocator(false);

  /**
   * Possible that we setup the trailer appender and have yet to get any data sent to in until
   * the msg requests some data
   */
  @Test
  public void testStreamNoDataPassedBeforeMessageRequest() throws Exception {
    int trailerLength = 1200;

    Message rpc = Mockito.mock(Message.class);
    NettyInboundRoadRunnerMessage msg =
        new NettyInboundRoadRunnerMessage((byte) 1, rpc, (long) trailerLength);
    // prepare the message for reading
    NettyInboundRoadRunnerMessage.TrailerCompleteCallback complete = Mockito.mock(
        NettyInboundRoadRunnerMessage.TrailerCompleteCallback.class);
    DecodingExceptionHandler handler = Mockito.mock(DecodingExceptionHandler.class);
    msg.setReadReady(complete, handler);

    // setup the appender
    BytePublisher appender = create(msg);

    // setup the callback to verify the data
    final boolean[] done = new boolean[] { false };
    final ByteBuf[] buf = new ByteBuf[1];
    final int[] nextSegment = new int[] { 946 };
    TrailerReader reader = new TrailerReader() {
     @Override
     public void read(ByteBuf trailer, boolean isLast) {
       assertEquals(done[0], isLast);
       assertEquals(nextSegment[0], trailer.readableBytes());
       // verify the actual data
       for (int i = 0; i < trailer.readableBytes(); i++) {
         assertEquals("Mismatch at " + i, buf[0].getByte(i), trailer.readByte());
       }
     }
   };

    // read the trailer from the message
    TrailerReader spy = Mockito.spy(reader);
    msg.readTrailer(false, spy);

    // nothing should have been called at this point
    Mockito.verify(spy, Mockito.times(0)).read(Mockito.any(ByteBuf.class), Mockito.anyBoolean());

    // write less than full segment to the appender, which should be passed to the reader
    buf[0] = fill(946);
    appender.decode(Mockito.mock(ChannelHandlerContext.class), buf[0], Collections.EMPTY_LIST);

    // write some more data and expect it to be passed to the reader also
    nextSegment[0] = trailerLength - nextSegment[0];
    buf[0] = fill(nextSegment[0]);
    done[0] = true;
    appender.decode(Mockito.mock(ChannelHandlerContext.class), buf[0], Collections.EMPTY_LIST);

    // verify that the read was called with both chunks
    Mockito.verify(spy, Mockito.times(2)).read(Mockito.any(ByteBuf.class), Mockito.anyBoolean());
    verifyCompleteWithoutErrors(complete, handler);
  }

  private void verifyCompleteWithoutErrors(
      NettyInboundRoadRunnerMessage.TrailerCompleteCallback complete,
      DecodingExceptionHandler handler) {
    Mockito.verify(complete, Mockito.times(1)).trailerComplete();
    Mockito.verify(handler, Mockito.never()).decodingException(Mockito.anyString(),
        Mockito.any(Exception.class));
  }

  private BytePublisher create(Subscriber<ByteBuf> subscriber) {
    BytePublisher appender = new BytePublisher();
    appender.subscribe(subscriber);
    return appender;
  }

  /**
   * Can stream data but the appender gets a buffer before we request to read the trailer
   */
  @Test
  public void testStreamSentDataBeforeMessageRequest() throws Exception {
    int trailerLength = 1200;
    boolean[] done = new boolean[] { false };
    Message rpc = Mockito.mock(Message.class);
    NettyInboundRoadRunnerMessage msg =
        new NettyInboundRoadRunnerMessage((byte) 1, rpc, trailerLength);
    // prepare the message for reading
    NettyInboundRoadRunnerMessage.TrailerCompleteCallback complete = Mockito.mock(
        NettyInboundRoadRunnerMessage.TrailerCompleteCallback.class);
    DecodingExceptionHandler handler = Mockito.mock(DecodingExceptionHandler.class);
    msg.setReadReady(complete, handler);

    // setup the appender
    BytePublisher appender = create(msg);

    // write a less than full segment to the appender
    final ByteBuf[] buf = new ByteBuf[1];
    appendBytes(appender, 946, buf);

    // read the trailer
    final int[] nextSegment = new int[] { 946 };
    TrailerReader reader = new TrailerReader() {
      @Override
      public void read(ByteBuf trailer, boolean isLast) {
        assertEquals(nextSegment[0], trailer.readableBytes());
        // verify the actual data
        for (int i = 0; i < trailer.readableBytes(); i++) {
          assertEquals("Mismatch at " + i, buf[0].getByte(i), trailer.readByte());
        }
      }
    };
    TrailerReader spy = Mockito.spy(reader);
    msg.readTrailer(false, spy);

    // then write some more data and expect it to be passed to the reader
    nextSegment[0] = trailerLength - nextSegment[0];
    done[0] = true;
    appendBytes(appender, nextSegment[0], buf);

    Mockito.verify(spy, Mockito.times(2)).read(Mockito.any(ByteBuf.class), Mockito.anyBoolean());
    verifyCompleteWithoutErrors(complete, handler);
  }

  private ByteBuf fill(int bytes) {
    ByteBuf buf = alloc.buffer(bytes);
    // this will eventually wrap size of byte - that's fine
    for (int i = 0; i < bytes; i++) {
      buf.writeByte(i);
    }
    return buf;
  }

  /**
   * Test that we correctly handle cases where the publisher has a larger trailer than the chunk
   * size requested from the subscriber. Specifically, this could cause an issue in the
   * subscriber when it
   */
  @Test
  public void testLargerTrailerChunks() throws Exception {
    int trailerLength = 1200;
    final boolean[] done = new boolean[] { false };
    Message rpc = Mockito.mock(Message.class);
    NettyInboundRoadRunnerMessage msg =
        new NettyInboundRoadRunnerMessage((byte) 1, rpc, trailerLength);
    // prepare the message for reading
    NettyInboundRoadRunnerMessage.TrailerCompleteCallback complete = Mockito.mock(
        NettyInboundRoadRunnerMessage.TrailerCompleteCallback.class);
    DecodingExceptionHandler handler = Mockito.mock(DecodingExceptionHandler.class);
    msg.setReadReady(complete, handler);

    // setup the appender
    BytePublisher appender = create(msg);

    // write a less than full segment to the appender
    final int[] nextSegment = new int[] { 700 };
    final ByteBuf[] buf = new ByteBuf[1];
    buf[0] = fill(nextSegment[0]);

    // write to the appender
    appender.decode(Mockito.mock(ChannelHandlerContext.class), buf[0], Collections.EMPTY_LIST);

    // read the trailer
    NettyInboundRoadRunnerMessage.TrailerReader reader = new NettyInboundRoadRunnerMessage
        .TrailerReader() {
      @Override
      public void read(ByteBuf trailer, boolean isLast) {
        assertEquals(done[0], isLast);
        assertEquals(nextSegment[0], trailer.readableBytes());
        trailer.forEachByte(new ByteBufProcessor() {
          int i = 0;

          @Override
          public boolean process(byte value) throws Exception {
            assertEquals("Mismatch at " + i, buf[0].getByte(i), value);
            i++;
            return true;
          }
        });
      }
    };
    int chunksize = 100;
    NettyInboundRoadRunnerMessage.TrailerReader spy = Mockito.spy(reader);

    msg.readTrailer(chunksize, spy);

    // then write some more data and expect it to be passed to the reader
    int i;
    for (i = nextSegment[0]; i < trailerLength; i += chunksize) {
      nextSegment[0] = chunksize;
      done[0] = (i + chunksize) == trailerLength;
      appendBytes(appender, nextSegment[0], buf);
    }

    Mockito.verify(spy, Mockito.times(6)).read(Mockito.any(ByteBuf.class), Mockito.anyBoolean());
    verifyCompleteWithoutErrors(complete, handler);
  }

  private void appendBytes(BytePublisher appender, int nextSegmentSize,
      final ByteBuf[] buf) throws Exception {
    buf[0] = fill(nextSegmentSize);
    send(appender, nextSegmentSize, buf[0]);
  }

  private void send(BytePublisher appender, int nextSegmentSize, ByteBuf buf) throws Exception {
    LOG.info("--- Sending " + nextSegmentSize + " bytes to appender ---");
    appender.decode(Mockito.mock(ChannelHandlerContext.class), buf, Collections.EMPTY_LIST);
  }

  /**
   * Test that we correctly allocate/release buffer when reading fully
   */
  @Test
  public void testMultipleChunksWhenReadAll() throws Exception {
    final int trailerLength = 1200;

    // setup the appender
    Message rpc = Mockito.mock(Message.class);
    NettyInboundRoadRunnerMessage msg =
        new NettyInboundRoadRunnerMessage((byte) 1, rpc, trailerLength);
    // prepare the message for reading
    NettyInboundRoadRunnerMessage.TrailerCompleteCallback complete = Mockito.mock(
        NettyInboundRoadRunnerMessage.TrailerCompleteCallback.class);
    DecodingExceptionHandler handler = Mockito.mock(DecodingExceptionHandler.class);
    msg.setReadReady(complete, handler);

    final BytePublisher appender = create(msg);

    // write a less than full segment to the appender
    final ByteBuf buffer = fill(1200);
    // copy over the bytes so we can validate them later
    final ByteBuf copy = alloc.buffer(buffer.capacity());
    copy.setBytes(0, buffer);

    int chunkSize = 946;
    // mark the readable bytes
    buffer.readerIndex(0);
    buffer.writerIndex(chunkSize);
    // write the first chunk
    send(appender, buffer.readableBytes(), buffer);

    // then write the rest of data and expect it to be passed to the reader eventually
    buffer.readerIndex(chunkSize);
    buffer.writerIndex(buffer.capacity());
    send(appender, buffer.readableBytes(), buffer);

    // continuously write to the appender until there are no more bytes to read
    Thread t = new Thread() {
      @Override
      public void run() {
        while (buffer.readableBytes() > 0) {
          try {
            LOG.info("Decoding " + buffer.readableBytes() + " bytes in buffer");
            appender
                .decode(Mockito.mock(ChannelHandlerContext.class), buffer,
                    Collections.emptyList());
          } catch (Exception e) {
            LOG.error("Got an exception while trying to decode bytes", e);
            assertTrue(false);
          }

        }
        LOG.info("No more readable bytes to send!");
      }
    };
    t.setName("append-decode-caller");
    t.start();

    // read the trailer, should be all in a single batch
    copy.readerIndex(0);
    copy.writerIndex(copy.capacity());
    final CountDownLatch done = new CountDownLatch(1);
    TrailerReader reader = new TrailerReader(){
      @Override
      public void read(ByteBuf trailer, boolean isLast) {
        assertEquals(true, isLast);
        assertEquals(trailerLength, trailer.readableBytes());
        // verify the actual data
        for (int i = 0; i < trailer.readableBytes(); i++) {
          assertEquals("Mismatch at " + i, copy.getByte(i), trailer.readByte());
        }
        done.countDown();
      }
    };

    TrailerReader spy = Mockito.spy(reader);
    msg.readTrailer(true, spy);

    done.await();
    Mockito.verify(spy, Mockito.times(1)).read(Mockito.any(ByteBuf.class), Mockito.anyBoolean());
    verifyCompleteWithoutErrors(complete, handler);
  }
}