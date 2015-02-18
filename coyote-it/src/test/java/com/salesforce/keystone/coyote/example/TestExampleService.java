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

package com.salesforce.keystone.coyote.example;

import com.google.protobuf.ServiceException;
import com.salesforce.keystone.coyote.example.client.ExampleClient;
import com.salesforce.keystone.coyote.example.server.ExampleRpcServer;
import com.salesforce.keystone.coyote.example.server.HelloRpcService;
import com.salesforce.keystone.coyote.netty.handler.stream.TrailerAccessorInterface;
import com.salesforce.keystone.coyote.rpc.utils.CoyoteRemoteException;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestExampleService {

  @Test
  public void testSendMessage() throws Exception {
    // start the rpc server
    ExampleRpcServer server = new ExampleRpcServer();
    server.start();
    int port = server.getPort();

    ExampleClient client = new ExampleClient(port);
    client.hello();

    // cleanup after everything
    client.stop();
    server.stop();
  }

  @Test
  public void testRemoteException() throws Exception {
    // start the rpc server
    ExampleRpcServer server = new ExampleRpcServer();
    server.start();
    int port = server.getPort();

    ExampleClient client = new ExampleClient(port);
    try {
      client.exception();
      fail("Exception didn't get thrown back to client");
    } catch (ServiceException e) {
      CoyoteRemoteException cause = (CoyoteRemoteException) e.getCause();
      assertEquals(HelloRpcService.EXCEPTION_MESSAGE, cause.getMessage());
    }

    // cleanup after everything
    client.stop();
    server.stop();
  }

  @Test
  public void testStreamingMessage() throws Exception {
    // start the rpc server
    ExampleRpcServer server = new ExampleRpcServer();
    server.start();
    int port = server.getPort();

    ExampleClient client = new ExampleClient(port);
    // start with a small byte[]
    byte[] data = new byte[100];
    for (byte i = 0; i < data.length; i++) {
      data[i] = i;
    }
    TrailerAccessorInterface response = client.stream(new ByteArrayTrailer(data));
    byte[] returnedData = response.writeOut((int) response.getTrailerLength());
    assertArrayEquals(data, returnedData);

    // cleanup after everything
    client.stop();
    server.stop();
  }
}