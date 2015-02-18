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

package com.salesforce.keystone.coyote.netty.roadrunner;

import com.google.common.base.Preconditions;
import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.netty.handler.stream.NettyRoadRunnerMessage;
import com.salesforce.keystone.coyote.netty.handler.stream.roadrunner.DecodingExceptionHandler;
import com.salesforce.keystone.roadrunner.BaseRoadRunnerMessage;
import com.salesforce.keystone.roadrunner.RoadRunnerMessage;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;
import com.salesforce.keystone.roadrunner.memory.MemoryManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Like a RoadRunnerMessage, but with Netty based channel reads.
 * <p>
 * All ownership of buffers is handled by Netty, so we shouldn't try to free it with the
 * TrailerDataCallback
 * </p>
 * <p>
 * Use {@link #readTrailer(boolean, TrailerReader)} or {@link #readTrailer(boolean,
 * TrailerReader)} over the TrailerDataCallback implementation. While both will work,
 * it is far more efficient to use the Netty ByteBuf when possible, to avoid byte copies.
 * </p>
 * <p>
 * When reading chunks into the trailer callback, as with the original RoadRunner implementation,
 * chunks may be larger or smaller than the requested chunk size; chunk size is merely a
 * recommendation - the number of bytes received depends on the actual I/O rate and frequency of
 * updating the message.
 * </p>
 */
public class NettyInboundRoadRunnerMessage extends BaseRoadRunnerMessage
    implements Subscriber<ByteBuf>, NettyRoadRunnerMessage {

  private static final Log LOG = LogFactory.getLog(NettyInboundRoadRunnerMessage.class);

  /**
   * if we stream over the trailer, we'll do it in chunks of this size unless you specify
   * otherwise
   */
  private static final int DEFAULT_TRAILER_CHUNK_SIZE = 1024;

  private final Deque<ByteBuf> trailer;
  private int trailerChunkSize = DEFAULT_TRAILER_CHUNK_SIZE;
  private TrailerReader readCallback;
  /**
   * if the entire trailer should be buffered in memory, before be passed to the listener
   */
  private boolean readFully;
  /**
   * Number of bytes pending for this message to process. This is really an intermediate counter
   * between receiving bytes and passing them into the trailer callback (reader)
   */
  private long trailerOutstanding;

  /**
   * The total number of bytes that have been passed to the reader for processing
   */
  private long bytesSentToReader;

  /**
   * Subscription for more bytes
   */
  private Subscription subscription;
  private long bytesRequested;
  private TrailerCompleteCallback completeCallback;
  private DecodingExceptionHandler errorHandler;

  public NettyInboundRoadRunnerMessage(byte msgId, Message message, long trailerLen) {
    this(new RoadRunnerHeader(RoadRunnerMessage.CURRENT_VERSION, msgId, message.getSerializedSize(),
        trailerLen), message);
  }

  public NettyInboundRoadRunnerMessage(RoadRunnerHeader header, Message message) {
    super(header, message);
    this.trailerOutstanding = header.trailerLength;
    this.trailer = new ArrayDeque<>();
  }

  public void setReadReady(TrailerCompleteCallback callback, DecodingExceptionHandler handler) {
    this.completeCallback = callback;
    this.errorHandler = handler;
  }

  @Override
  public void readTrailer(int chunkSize, TrailerReader callback) {
    readTrailer(false, chunkSize, callback);
  }

  @Override
  public void readTrailer(boolean readFully, TrailerReader callback) {
    readTrailer(readFully, DEFAULT_TRAILER_CHUNK_SIZE, callback);
  }

  /**
   * Read the trailer into the callback asynchronously.
   *
   * @param readFully <tt>true</tt> if this should wait until the entire trailer is in memory
   * before sending it to the callback
   * @param chunkSize number of bytes per chunk read. This can be less than the full trailer
   * size, even if readFully is <tt>true</tt>, it just decreases the number of bytes read in each
   * back to build up the full buffer
   * @param callback to be notified when data is available. If readFully is <tt>true</tt> it is
   * only notified when the entire trailer is available
   */
  private synchronized void readTrailer(boolean readFully, int chunkSize, TrailerReader callback) {
    // ensure that we are setup correctly
    Preconditions.checkNotNull(completeCallback, "Message wasn't marked readReady with a non-null" +
        " on-complete callback");
    Preconditions.checkNotNull(errorHandler, "Message wasn't marked readReady with a non-null" +
        " error handler");

    // check that the argument are valid
    Preconditions.checkArgument(chunkSize >= 1,"chunkSize needs to be positive");
    Preconditions.checkNotNull(callback,"TrailerDataCallback parameter can not be null");
    Preconditions.checkState(readCallback == null,"You should only call readTrailer once!");

    if (bytesSentToReader == this.getTrailerLen()) {
      // Shouldn't ever happen, but the client may be trying to read the trailer when there is no
      // trailer to read
      LOG.error("Already sent all bytes to reader");
      return;
    }

    this.readCallback = callback;
    this.readFully = readFully;
    this.trailerChunkSize = chunkSize;

    // early exit, no data right now
    if (trailer.isEmpty()) {
      // signal to publisher that we are ready to process data
      requestNextChunk();
      return;
    }

    // check to see if we can/should write out the whole trailer
    if (readFully) {
      if (!doFullRead(null)) {
        // didn't read the entire, so we need some more data
        requestNextChunk();
      }
      return;
    }

    // just give them the data we have right now
    ByteBuf q;
    long r = getTrailerLen();
    LOG.trace("Sending buffered data to callback");
    while ((q = trailer.poll()) != null) {
      r -= q.readableBytes();
      sendToReader(q, r <= 0);
      ReferenceCountUtil.release(q);
      if (r <= 0) {
        LOG.info("Already buffered all the trailer data - told the callback that we are done");
      }
    }

    LOG.info("Done sending all buffered data to callback");
    // get some more data
    requestNextChunk();
  }

  /**
   * Attempt to give the entire trailer to the read callback
   *
   * @param data last chunk of data
   * @return <tt>true</tt> if the callback was updated, <tt>false</tt> otherwise
   */
  private boolean doFullRead(ByteBuf data) {
    // not reading the whole buffer at once or we haven't see all the data yet
    if (!readFully || trailerOutstanding > 0) {
      return false;
    }

    LOG.trace("Reading full trailer into callback");

    ByteBuf finalTrailer = buildFullTrailer(trailer, data);
    sendToReader(finalTrailer, true);

    // release all the trailers we've accumulated
    for (ByteBuf buf : trailer) {
      ReferenceCountUtil.release(buf);
    }
    return true;
  }

  private void sendToReader(ByteBuf buffer, boolean complete) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Sending " + buffer.readableBytes() + " bytes to reader");
    }

    this.bytesSentToReader += buffer.readableBytes();
    readCallback.read(buffer, complete);

    if(complete){
      onComplete();
    }
  }

  /**
   * Request the next chunk of data, up to the size of the trailer
   */
  private void requestNextChunk() {
    if (bytesRequested >= getTrailerLen() || this.trailerOutstanding == 0) {
      LOG.info("Skipping next chunk request, bytesRequested: " + bytesRequested + ", " +
          "trailerLenth:" + getTrailerLen() + ", outstanding: " + trailerOutstanding);
      return;
    }

    // if we want to read the entire trailer, then just request the rest of the trailer
    if (readFully) {
      this.subscription.request(this.trailerOutstanding);
      return;
    }

    // otherwise, we just want to read up to the size of the next chunk

    // current number of outstanding bytes from the last request
    long outstanding = bytesRequested - bytesSentToReader;

    // have more than 1 trailer outstanding, so don't do anything
    if (outstanding > this.trailerChunkSize) {
      return;
    }

    // number of bytes needed to fill the next chunk
    long chunkBytes = this.trailerChunkSize - outstanding;

    // number of bytes needed to finish the trailer
    long remaining = this.trailerOutstanding - outstanding;

    long more = Math.min(chunkBytes, remaining);
    bytesRequested += more;

    // don't make any extra calls if there is no more data to request
    if (more <= 0) {
      return;
    }

    LOG.trace("Requesting " + more + " bytes of " + bytesRequested + " total bytes");
    this.subscription.request(more);
  }

  @Override
  public synchronized void onNext(ByteBuf data) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Received " + data.readableBytes() + " bytes of trailer");
    }
    // count down the number of bytes for which we are waiting
    trailerOutstanding -= data.readableBytes();

    // no callback yet, so couldn't have readFully set
    if (readCallback == null) {
      // retain the data
      claim(data);
      return;
    }

    // try to do a full read, if we have all the data
    if (readFully) {
      // successfully wrote all the data - done!
      if (doFullRead(data)) {
        return;
      }
      //missing some data, so get the next chunk
      claim(data);
      requestNextChunk();
      return;
    }

    // just pass along the data to the reader, get more data
    sendToReader(data, trailerOutstanding == 0);
    requestNextChunk();
  }

  private void claim(ByteBuf buf) {
    if (buf == null) {
      return;
    }
    ReferenceCountUtil.retain(buf);
    trailer.add(buf);
  }

  /**
   * Build the entire trailer (all expected bytes) from the collected buffers
   * <p>
   * If the lastBuffer is the entirety of the message, just returns that
   * </p>
   *
   * @param buffers all the buffers we've see so far
   * @param the next buffer in the trailer
   */
  private ByteBuf buildFullTrailer(Deque<ByteBuf> buffers, ByteBuf lastBuffer) {
    assert trailerOutstanding == 0 : "Cannot build full trailer if message is still missing some " +
        "bytes";
    // TODO should handle this instead of assert
    assert getTrailerLen() <= Integer.MAX_VALUE : "Trailer must fit into a single buffer, " +
        "but trailer length exceeds the max allocatable size";

    // there are no other buffers
    if (buffers == null || buffers.isEmpty()) {
      return lastBuffer;
    }
    // just have a single buffer
    if (buffers.size() == 1 && lastBuffer == null) {
      return buffers.getFirst();
    }

    // add the last buffer into the queue
    claim(lastBuffer);

    // combine the buffers into a single mega-buffer
    ByteBuf ret = ByteBufAllocator.DEFAULT.compositeDirectBuffer().addComponents(buffers);
    // move up the writer index to the end up the buffer
    int writer = 0;
    for (ByteBuf b : buffers) {
      writer += b.readableBytes();
    }
    ret.writerIndex(writer);

    return ret;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    this.subscription = subscription;
    // immediately ready for some data
    requestNextChunk();
  }

  @Override
  public void onError(Throwable throwable) {
    this.errorHandler
        .decodingException("Error from publisher while waiting for trailer", throwable);
  }

  @Override
  public void onComplete() {
    LOG.info("Publisher indicated that it has received and passed all trailer data!");
    this.subscription.cancel();
    this.completeCallback.trailerComplete();
  }

  public int getChunkSize() {
    return trailerChunkSize;
  }

  @Override
  public Iterator<ByteBuffer> trailerDataForWriting() {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't support being written");
  }

  /**
   * Callback to be notified when the trailer has been completely read
   */
  public interface TrailerCompleteCallback {
    public void trailerComplete();
  }
}