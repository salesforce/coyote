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

package com.salesforce.keystone.coyote.rpc.client.sync;

import com.google.protobuf.BlockingRpcChannel;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import com.salesforce.keystone.coyote.rpc.client.channel.RpcChannel;
import com.salesforce.keystone.coyote.rpc.client.request.Request;
import com.salesforce.keystone.coyote.rpc.client.request.Request.ResponseListener;
import com.salesforce.keystone.coyote.rpc.protobuf.StreamCarryingRpcController;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Utilities for using the {@link BlockingRpcChannel}
 */
public class BlockingRpcUtils {
  private static final Log LOG = LogFactory.getLog(BlockingRpcUtils.class);

  private BlockingRpcUtils() {
    // private ctor for util class
  }

  public static BlockingRpcChannel asBlockingChannel(RpcChannel channel) {
    return new CallbackOnFinishedBlockingChannel(channel);
  }

  private static class CallbackOnFinishedBlockingChannel implements BlockingRpcChannel,
      ResponseListener<Message> {
    private Message response;
    private final Object done = new Object();
    private final RpcChannel delegate;
    private Exception exception;

    public CallbackOnFinishedBlockingChannel(RpcChannel channel) {
      this.delegate = channel;
    }

    @Override
    public synchronized Message callBlockingMethod(MethodDescriptor method,
        RpcController controller, Message request, Message responsePrototype)
        throws ServiceException {
      delegate.callMethod(method, (StreamCarryingRpcController) controller, request,
          responsePrototype, this);
      try {
        return this.get();
      } catch (InterruptedException e) {
        throw new ServiceException("Interrupted while waiting for response.", e);
      } catch (Exception e) {
        throw new ServiceException(e);
      } finally {
        reset();
      }
    }

    @Override
    public void responseReceived(Message result, Request<Message> request) {
      //early exit if the rpc got more results than we expected
      if (hasResult()) {
        LOG.error("Got an unexpected response: " + result);
        return;
      }

      synchronized (done) {
        // tell the response we don't expect any more notifications
        request.markDone();

        // notify the waiters
        this.response = result;
        done.notify();
      }
    }

    @Override
    public void errorReceived(Throwable throwable, Request<Message> request) {
      //early exit if the rpc got more results than we expected
      if (hasResult()) {
        LOG.error("Got an unexpected response: " + throwable);
        return;
      }

      synchronized (done) {
        // tell the response we don't expect any more notifications
        request.markDone();

        // notify the waiters
        this.exception = throwable instanceof Exception ?
            (Exception) throwable :
            new RuntimeException(throwable);
        done.notify();
      }
    }

    private boolean hasResult() {
      return this.exception != null || this.response != null;
    }

    private void reset() {
      this.response = null;
      this.exception = null;
    }

    public Message get() throws Exception {
      while (!hasResult()){
        synchronized (done) {
          done.wait();
        }
      }

      if (exception != null) {
        throw exception;
      }

      return response;
    }
  }
}