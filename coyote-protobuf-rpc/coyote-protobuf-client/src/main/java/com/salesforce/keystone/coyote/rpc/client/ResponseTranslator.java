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

package com.salesforce.keystone.coyote.rpc.client;

import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.netty.handler.stream.NettyRoadRunnerMessage;
import com.salesforce.keystone.coyote.netty.handler.stream.NettyTrailerAccessor;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.Rpc;
import com.salesforce.keystone.coyote.rpc.protobuf.StreamCarryingRpcController;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceTranslator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Translates from a generic response to a message
 */
public class ResponseTranslator {

  private static final Log LOG = LogFactory.getLog(ResponseTranslator.class);

  private final ProtobufServiceTranslator translator;

  private Message responsePrototype;
  private StreamCarryingRpcController controller;

  public ResponseTranslator(Message responseProto, ProtobufServiceTranslator translator,
      StreamCarryingRpcController controller) {
    this.translator = translator;
    this.responsePrototype = responseProto;
    this.controller = controller;
  }

  public Message buildResponse(Rpc rpc, NettyRoadRunnerMessage receivedMessage) {
    Message msg = translator.translate(rpc, responsePrototype);
    if (receivedMessage.hasTrailer()) {
      controller.setInboundTrailer(
          new NettyTrailerAccessor(receivedMessage, receivedMessage.getChunkSize()));
    }
    return msg;
  }
}
