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

package com.salesforce.keystone.coyote.netty.handler.stream.subscriber;

import com.salesforce.keystone.coyote.netty.handler.stream.roadrunner.DecodingExceptionHandler;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Abstraction around the subscribed stream in that subclasses can view the buffer in {@link
 * #doNext(ByteBuf)} as containing all the bytes they require.
 */
public abstract class BaseRoadRunnerSubscriber implements Subscriber<ByteBuf> {

  private static final Log LOG = LogFactory.getLog(BaseRoadRunnerSubscriber.class);

  private int expectedBytes;
  protected final MessageMapper mapper;
  private final DecodingExceptionHandler handler;
  private final ByteBuf needed;
  protected Subscription subscription;

  public BaseRoadRunnerSubscriber(ByteBufAllocator allocator, MessageMapper mapper,
      DecodingExceptionHandler handler, int expectedBytes) {
    this.mapper = mapper;
    this.expectedBytes = expectedBytes;
    needed = allocator.buffer(expectedBytes);
    this.handler = handler;
  }

  @Override
  public void onSubscribe(Subscription s) {
    this.subscription = s;
    // immediately request the header size
    s.request(expectedBytes);
  }

  @Override
  public void onError(Throwable t) {
    LOG.error("Got an error while reading header!", t);
    this.handler.decodingException("Exception from the publisher while waiting for data", t);
  }

  @Override
  public void onComplete() {
    LOG.info("Stream has no more data!");
  }

  @Override
  public void onNext(ByteBuf buffer) {
    // got at least what we need
    if (buffer.readableBytes() >= expectedBytes) {
      // we haven't seen any data yet, so we can just read of the header
      if (needed.readableBytes() == 0) {
        read(buffer);
        return;
      }
    }

    // seen some data, so we just copy in what we need
    int copied = Math.min(expectedBytes, buffer.readableBytes());
    needed.writeBytes(buffer, copied);
    expectedBytes -= copied;

    // do the read if we have all the bytes we need
    if (expectedBytes <= 0) {
      read(needed);
    } else {
      LOG.trace("Waiting for " + expectedBytes + " more data");
    }
  }

  private void read(ByteBuf buffer) {
    try {
      doNext(buffer);

    } catch (Exception e) {
      this.handler.decodingException(decodingErrorMessage(), e);
    }
  }

  protected abstract void doNext(ByteBuf buffer) throws Exception;

  protected abstract String decodingErrorMessage();
}
