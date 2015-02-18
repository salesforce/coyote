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

package com.salesforce.keystone.coyote.rpc.client.request;

import com.salesforce.keystone.coyote.rpc.client.ResponseTranslator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This can be used to listen to the response(s) from a request. Additionally,
 * its how you can signal to the rpc mechanism that you are finished listening for any more
 * responses to the original request.
 */
//TODO investigate using Listenable/CheckedFutures
public class Request<T> {

  private static final Log LOG = LogFactory.getLog(Request.class);

  private final RequestManager manager;
  private final String id;
  private final ResponseTranslator responseTranslator;
  private final List<ResponseListener<T>> listeners = new ArrayList<>();

  private boolean done = false;

  Request(RequestManager manager, String id, ResponseTranslator translator) {
    this.responseTranslator = translator;
    this.id = id;
    this.manager = manager;
  }

  public void markDone() {
    if (this.done) {
      return;
    }
    this.done = true;
    manager.markDone(this);
  }

  public String getId() {
    return id;
  }

  /**
   * Add a listener for any <b>new</b> responses to the request. Any responses received before
   * the listener were added will not be passed to the listener
   *
   * @param listener to update
   */
  public void addResponseListener(ResponseListener listener) {
    if (done) {
      LOG.warn(this + " was already marked done, " +
          "listener cannot receive any more updates. Not adding it to listening list");
      return;
    }
    // update the listener with anything we have already received
    synchronized (this) {
      listeners.add(listener);
    }
  }

  public void receiveResponse(T response) {
    if (done) {
      LOG.error(this + " was already marked done, but got an response update. NOT updating " +
          "listeners with response: " + response);
      return;
    }

    synchronized (this){
      for(ResponseListener<T> listener :this.listeners){
        listener.responseReceived(response, this);
      }
    }
  }

  public void receiveException(Throwable throwable) {
    if (done) {
      LOG.error(this + " was already marked done, but got an error update. NOT updating listeners",
          throwable);
      return;
    }

    synchronized (this){
      for(ResponseListener<T> listener :this.listeners){
        listener.errorReceived(throwable, this);
      }
    }
  }

  public ResponseTranslator getResponseTranslator() {
    return responseTranslator;
  }

  @Override
  public String toString() {
    return "Request -  id: " + this.getId();
  }

  public static interface ResponseListener<T> {
    public void responseReceived(T response, Request<T> request);

    public void errorReceived(Throwable throwable, Request<T> request);
  }
}
