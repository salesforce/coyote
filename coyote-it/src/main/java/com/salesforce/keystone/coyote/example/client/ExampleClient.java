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

package com.salesforce.keystone.coyote.example.client;

import com.google.protobuf.ServiceException;
import com.salesforce.keystone.coyote.netty.handler.stream.TrailerAccessorInterface;
import com.salesforce.keystone.coyote.rpc.protobuf.StreamCarryingRpcController;
import salesforce.coyote.example.Example;
import salesforce.coyote.example.Example.HelloRequest;
import salesforce.coyote.example.Example.StreamRequest;
import salesforce.coyote.example.Example.StreamResponse;

import java.io.IOException;

import static java.util.Arrays.asList;

/**
 * Simple client that can just say hello to a single server.
 */
public class ExampleClient {

  static final String HOST = System.getProperty("host", "127.0.0.1");
  private final ClientStubs rpc;

  public ExampleClient(int port) {
    this.rpc = new ClientStubs(asList(HOST), port);
  }

  public void hello() throws InterruptedException, IOException, ServiceException {
    rpc.getClientService().hello(null, HelloRequest.newBuilder().setValue(true).build());
  }

  public void exception() throws InterruptedException, IOException, ServiceException {
    rpc.getClientService().exception(null, Example.ExceptionRequest.getDefaultInstance());
  }

  public TrailerAccessorInterface stream(TrailerAccessorInterface dataToSend)
      throws InterruptedException, IOException, ServiceException {
    StreamCarryingRpcController rpcController = new StreamCarryingRpcController
        (dataToSend);
    StreamResponse response = rpc.getStreamingService().stream(rpcController,
        StreamRequest.getDefaultInstance());
    System.out.println("Got a response!");
    return rpcController.getInboundTrailer();
  }

  public void stop() {
    rpc.close();
  }
}