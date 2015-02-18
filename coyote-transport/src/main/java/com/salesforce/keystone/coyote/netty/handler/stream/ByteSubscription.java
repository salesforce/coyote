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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscription;

/**
 * Subscription to a group of bytes
 */
public class ByteSubscription implements Subscription {
  private static final Log LOG = LogFactory.getLog(ByteSubscription.class);
  private final BytePublisherInterface publisher;
  private final String subscriberName;

  private volatile boolean canceled = false;
  private long totalRequested;
  private long outstanding;

  public ByteSubscription(BytePublisherInterface publisher, String subscriberName) {
    this.subscriberName = subscriberName;
    this.publisher = publisher;
  }

  @Override
  public void request(long l) {
    this.totalRequested += l;
    this.outstanding += l;
    this.publisher.requestedMore(l, totalRequested);

    if (LOG.isTraceEnabled()) {
      LOG.trace("Subscriber " + subscriberName + " requested:\n\t"
              + l + " more bytes\n\t"
              + outstanding + " outstanding bytes\n\t"
              + totalRequested + " total bytes\n\t"
      );
    }
  }

  public void sentBytes(long bytes) {
    this.outstanding -= bytes;
    assert outstanding >= 0;
  }

  public long getOutstandingBytes() {
    return this.outstanding;
  }

  @Override
  public void cancel() {
    LOG.trace(subscriberName + " canceling subscription");
    this.canceled = true;
    this.publisher.remove(this);
  }
}
