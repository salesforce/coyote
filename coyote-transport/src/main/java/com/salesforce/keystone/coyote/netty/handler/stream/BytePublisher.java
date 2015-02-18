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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;

import java.util.List;

/**
 * A Publisher that will publish raw ByteBufs to the single subscription
 */
public class BytePublisher extends ByteToMessageDecoder implements BytePublisherInterface {

  private static final Log LOG = LogFactory.getLog(BytePublisher.class);

  private Subscriber<? super ByteBuf> subscriber;
  private ByteSubscription subscription;

  private Object writeLock = new Object();
  private Object subscriptionLock = new Object();

  /**
   * Decode the from one ByteBuf to an other. This method will be called till either the input
   * ByteBuf has nothing to read when return from this method or till nothing was read from the
   * input ByteBuf.
   * <p>
   * Got a trailer from the wire, now we have to decide we should wait or pass it onto the
   * current subscriber.
   * </p>
   *
   * @param ctx context of the request
   * @param trailer to pass onto the subscriber
   * @param msg no messages are returned from <tt>this</tt>
   */
  @Override
  public void decode(ChannelHandlerContext ctx, ByteBuf trailer,
      List<Object> msg) throws Exception {
    synchronized (subscriptionLock) {
      // wait for a subscription - if we don't have one, just wait until we do
      waitSubscription();

      // have a subscription, wait for it to request data
      synchronized (writeLock) {
        // attempt to write some data. If we do, we can just skip doing anything else because
        // either we have exhausted the current section of the trailer OR the client has received
        // all the bytes it expects
        while (!doWrite(trailer)) {
          // We didn't write anything to the client since it wasn't ready. Now, we have to  wait
          // until we get signaled from the client that we need to send on requestedBytes data.
          // Otherwise, if we return without modifying the ByteBuf then we won't see it again
          writeLock.wait();
        }
      }
    }
  }

  /**
   * Wait for a subscription
   */
  private void waitSubscription() throws InterruptedException {
    if (subscription != null) {
      return;
    }

    // wait for a subscription
    subscriptionLock.wait();
  }

  @Override
  protected void decodeLast(ChannelHandlerContext ctx, ByteBuf trailer, List<Object> out)
      throws Exception {
    // there is some more data to write, so we need to do the usual thing
    while (trailer.readableBytes() > 0) {
      decode(ctx, trailer, out);
    }

    done();
  }

  private void done() {
    // tell the subscriber that we are done
    subscriber.onComplete();
  }

  /**
   * Do the write of the trailer to the subscriber
   *
   * @param trailer to pass onto the subscriber
   * @return <tt>true</tt> if the subscriber was updated, <tt>false</tt> otherwise
   */
  private boolean doWrite(ByteBuf trailer) {
    // need to get a separate reference here because subscribers could be changed in the same
    // thread, so any updates need to be to the old subscription
    ByteSubscription subscription = this.subscription;

    // if the client is not waiting for more data, so don't do anything
    if (subscription.getOutstandingBytes() <= 0) {
      LOG.trace("Skipping write because client hasn't requested data yet");
      return false;
    }
    long written = passOnBytes(trailer);
    assert written > 0 : "Was supposed to have passed some bytes onto the subscriber";

    // update the number of bytes written
    subscription.sentBytes(written);

    return true;
  }

  /**
   * Pass on the bytes from the specified trailer
   *
   * @param trailer trailer to pass onto the subscriber
   * @return number of bytes consumed
   */
  private long passOnBytes(ByteBuf trailer) {
    long numberOfBytesRequested = subscription.getOutstandingBytes();
    LOG.trace("Subscriber has " + numberOfBytesRequested + " bytes outstanding");

    int possibleToConsume = trailer.readableBytes();

    // we can read all the bytes from the trailer
    if (numberOfBytesRequested >= possibleToConsume) {
      this.subscriber.onNext(trailer.readSlice(possibleToConsume));
      return possibleToConsume;
    }

    // we requested less than the number of bytes available, so just ship what we have. This must
    // be an int, since its less than the possible length of a ByteBuf,
    // which is capped at Integer.MAX_VALUE
    // TODO add some chunking here so we don't create a lot of objects when there are smaller
    // reads
    this.subscriber.onNext(trailer.readSlice((int) numberOfBytesRequested));
    return numberOfBytesRequested;
  }

  @Override
  public void subscribe(Subscriber<? super ByteBuf> subscriber) {
    synchronized (subscriptionLock) {
      if (this.subscription != null) {
        subscriber.onError(new Exception(this.getClass() + " only supports a single subscriber"));
        return;
      }
      this.subscriber = subscriber;
      this.subscription = new ByteSubscription(this, subscriber.getClass().getSimpleName());
      this.subscriber.onSubscribe(subscription);

      // notify if we are waiting for a new subscriber
      this.subscriptionLock.notify();
    }
  }

  @Override
  public void requestedMore(long more, long totalRequested) {
    if (more <= 0) {
      return;
    }
    synchronized (writeLock) {
      if (more > 0) {
        // wake up that we have some data we can write
        writeLock.notifyAll();
      }
    }
  }

  @Override
  public void remove(ByteSubscription subscription) {
    synchronized (subscriptionLock) {
      if (this.subscription != subscription) {
        LOG.error("Trying to remove a subscription when it is not bound to us");
        return;
      }

      // reset the subscription info
      this.subscription = null;
    }
  }
}