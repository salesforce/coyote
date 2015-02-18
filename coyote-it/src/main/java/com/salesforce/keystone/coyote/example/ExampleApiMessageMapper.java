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

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.Rpc;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapper;
import com.salesforce.keystone.roadrunner.msgmapper.MessageMapperImpl;
import salesforce.coyote.example.Example;

import java.util.HashMap;
import java.util.Map;

/**
 * Map messageTypes in RoadRunner messages to the generated Protocol Buffers
 * class that handles them.
 * <p>
 * A new message type must be added if a message type other than Rpc is needed. It is possible to add
 * new RPC methods without adding a new message type by extending Rpc in a protobuf file.
 * <p>
 * Hopefully, Rpc + the protobuf extension model and the method name approach gives enough flexibility that
 * new message types will not be needed.
 * <p>
 * To add a new message type:
 * <ol>
 * <li> add a new message type code (1)</li>
 * <li> add the generated class to message type code mapping to the map  (2)</li>
 * </ol>
 */
public class ExampleApiMessageMapper {

  /*
   * Message type codes (1)
   */
  static final int RPC_MESSAGE_TYPE = 10;

  private static final MessageMapper INSTANCE;

  public static MessageMapper getMapper() {
    return INSTANCE;
  }

  static {
    ExtensionRegistry extRegistry = ExtensionRegistry.newInstance();

    // each registers their own extensions
    Example.registerAllExtensions(extRegistry);

    // just map from the generic VaultRpc which then handles looking up the methods
    Map<Class<? extends Message>, Integer> mapping = new HashMap<>();

    /*
     *  Add message types to the map (2)
     */
    mapping.put(Rpc.class, RPC_MESSAGE_TYPE);

    INSTANCE = new MessageMapperImpl(mapping, extRegistry);
  }

  private ExampleApiMessageMapper() {
    assert false;
  }
}
