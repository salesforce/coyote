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
import com.salesforce.keystone.coyote.example.ByteArrayTrailer;
import com.salesforce.keystone.coyote.example.client.ExampleClient;
import com.salesforce.keystone.coyote.netty.handler.stream.TrailerAccessorInterface;
import com.salesforce.keystone.coyote.rpc.utils.CoyoteRemoteException;
import org.junit.Test;
import org.mockito.Mockito;
import salesforce.coyote.example.Example.StreamRequest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for server-specific code
 */
public class TestExampleServer {

  /**
   * In some streaming implementaions, its possible the trailer isn't pulled off the wire if
   * there is an exception before the streamed data is read. We need to exceed he standard
   * trailer size in Netty (1024 bytes) to make sure it is chunked.
   * @throws Exception on failure
   */
  @Test
  public void testExceptionInStreamingMessages() throws Exception{
    byte[] data = new byte[4096];
    byte datum = 0;
    for (int i = 0; i < data.length; i++) {
      // this will wrap mulitple times, but that's fine - any bytes could be sent across in the
      // trailer
      data[i] = datum++;
    }

    // create a server which will first throw an exception, then work
    String exceptionMessage = "Intentional failure";
    final StreamRpcService delegate = new StreamRpcService();
    StreamRpcService rpc = Mockito.mock(StreamRpcService.class);
    Mockito.when(rpc.stream(Mockito.any(RpcController.class), Mockito.any(StreamRequest.class)))
        .thenThrow(new ServiceException(exceptionMessage)).thenCallRealMethod();

    // use our custom service
    ExampleServerServiceInitializer initializer = new ExampleServerServiceInitializer();
    initializer.setStreamService(rpc);
    ExampleRpcServer server = new ExampleRpcServer();
    server.start(initializer);

    // create a client
    int port = server.getPort();
    ExampleClient client = new ExampleClient(port);

    // first request should fail
    try {
      client.stream(new ByteArrayTrailer(data));
      fail("Exception didn't get thrown back to client");
    } catch (ServiceException e) {
      CoyoteRemoteException cause = (CoyoteRemoteException) e.getCause();
      assertEquals(exceptionMessage, cause.getMessage());
    }

    // then send the second request, which should just echo the stream back
    TrailerAccessorInterface trailer = client.stream(new ByteArrayTrailer(data));
    byte[] returned = trailer.writeOut(data.length);
    assertArrayEquals(data, returned);

    // cleanup after everything
    client.stop();
    server.stop();
  }
}