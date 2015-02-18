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

package com.salesforce.keystone.coyote.example.server;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import salesforce.coyote.example.Example.ClientService.BlockingInterface;
import salesforce.coyote.example.Example.ExceptionRequest;
import salesforce.coyote.example.Example.HelloRequest;
import salesforce.coyote.example.Example.HelloResponse;

import java.io.IOException;

/**
 * Simple RPC handler that just logs the request when it is received;
 */
public class HelloRpcService implements BlockingInterface {

  public static final String EXCEPTION_MESSAGE = "REMOTE_RPC_SERVICE_MESSAGE";

  @Override
  public HelloResponse hello(RpcController controller, HelloRequest request)
      throws ServiceException {
    System.out.println("Received rpc request: "+request+ " with controller: "+controller);
    return HelloResponse.newBuilder().setResponse(true).build();
  }

  @Override
  public HelloResponse exception(RpcController controller, ExceptionRequest request)
      throws ServiceException {
    throw new RuntimeException(EXCEPTION_MESSAGE);
  }
}