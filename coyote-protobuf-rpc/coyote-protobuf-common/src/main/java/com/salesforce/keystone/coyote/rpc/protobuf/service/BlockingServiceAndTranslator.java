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

package com.salesforce.keystone.coyote.rpc.protobuf.service;

import com.google.protobuf.BlockingService;
import com.google.protobuf.Service;
import com.salesforce.keystone.coyote.rpc.protobuf.ProtobufExtensionLookup;

/**
 * Datastructure for passing a {@link Service} and its associated class of
 * protobuf service interface.  For example, a server that fielded what is defined
 * in the client protobuf service would pass in an implementation of the client blocking service
 * and then its ClientService.BlockingInterface.class.  Used checking connection setup.
 */
public class BlockingServiceAndTranslator<S> {
  private final BlockingService service;
  private final ProtobufServiceTranslator translator;

  public BlockingServiceAndTranslator(final BlockingService service,
      ProtobufExtensionLookup extensions) {
    this.service = service;
    this.translator = new ProtobufServiceTranslator(service.getDescriptorForType().getName(),
        extensions);
  }

  public BlockingService getBlockingService() {
    return this.service;
  }

  public ProtobufServiceTranslator getTranslator() {
    return this.translator;
  }
}
