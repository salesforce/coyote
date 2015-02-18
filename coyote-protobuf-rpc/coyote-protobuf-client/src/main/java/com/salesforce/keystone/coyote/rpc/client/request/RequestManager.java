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

import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.rpc.client.ResponseTranslator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the multiplexing of requests to responses
 */
public class RequestManager implements AutoCloseable {

  private static final Log LOG = LogFactory.getLog(RequestManager.class);

  private Map<String, Request<Message>> outstandingRequests = new ConcurrentHashMap<>();

  /**
   * Mark the given request to as complete. No further responses to the request will be accepted
   * and passed, but instead will just be dropped on the floor
   *
   * @param request request to ignore
   */
  public void markDone(Request request) {
    outstandingRequests.remove(request.getId());
  }

  /**
   * Create a {@link Request} for the given RPC request that can be updated when there is a
   * response received
   *
   * @param responsePrototype @return a {@link Request} for the rpc
   */
  public Request<Message> createRequest(String id,
      ResponseTranslator translator) {
    Request<Message> ret = new Request<>(this, id, translator);
    if (this.outstandingRequests.get(id) != null) {
      throw new IllegalArgumentException("Already have a request with id: " + id);
    }
    this.outstandingRequests.put(id, ret);
    return ret;
  }

  @Override
  public void close() throws Exception {
    List<Request> requests = new ArrayList(outstandingRequests.values());
    for (Request request : requests) {
      request.markDone();
    }
  }

  public Request getRequest(String correlationId) {
    return this.outstandingRequests.get(correlationId);
  }
}