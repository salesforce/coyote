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

import com.google.protobuf.Service;
import com.salesforce.keystone.coyote.example.ExampleApiMessageMapper;
import com.salesforce.keystone.coyote.example.ExampleExtensions;
import com.salesforce.keystone.coyote.example.server.ExampleServerServiceInitializer;
import com.salesforce.keystone.coyote.rpc.client.sync.BlockingClientChannels;
import com.salesforce.keystone.coyote.rpc.protobuf.ProtobufExtensionLookup;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceTranslator;
import salesforce.coyote.example.Example;
import salesforce.coyote.example.Example.ClientService;
import salesforce.coyote.example.Example.StreamService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Create a client stub around a blocking channel.
 * <p>This is just a simple glue class so we can link the example stub making to the actual
 * channel.</p>
 */
public class ClientStubs {
  private final BlockingClientChannels channels;

  private final List<ProtobufServiceTranslator> services = new ArrayList<>(2);
  // add all the services that the client supports
  {
    services.add(new ProtobufServiceTranslator(Example.ClientService.getDescriptor().getName(),
        ExampleExtensions.IMPL));
    services.add(new ProtobufServiceTranslator(Example.StreamService.getDescriptor().getName(),
        ExampleExtensions.IMPL));
  }

  public ClientStubs(List<String> servers, int port) {
    this.channels =
        new BlockingClientChannels(servers, port, ExampleApiMessageMapper.getMapper(), services);
  }

  public ClientService.BlockingInterface getClientService() throws IOException,
      InterruptedException {
    return ClientService.newBlockingStub(channels.getBlockingChannel());
  }

  public StreamService.BlockingInterface getStreamingService() throws IOException,
      InterruptedException {
    return StreamService.newBlockingStub(channels.getBlockingChannel());
  }

  public void close() {
    channels.close();
  }
}