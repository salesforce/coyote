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
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.GeneratedMessage.GeneratedExtension;
import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.netty.rpc.RpcUtils;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.Rpc;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.Rpc.Builder;
import com.salesforce.keystone.coyote.rpc.protobuf.ProtobufExtensionLookup;
import com.salesforce.keystone.coyote.rpc.protobuf.RpcRequestResponse;
import com.salesforce.keystone.coyote.rpc.protobuf.ServiceAndTranslator;
import com.salesforce.keystone.coyote.rpc.protobuf.StreamCarryingRpcController;

import java.util.List;

/**
 * Wrap the simple protobuf request into the generic RpcMessages.Rpc as an extension and then shove
 * that into an RpcRequestResponse, which we can easily serialize
 */
public class ProtobufServiceTranslator {

  private final String service;
  private final ProtobufExtensionLookup extensions;

  public ProtobufServiceTranslator(String serviceName, ProtobufExtensionLookup extensions) {
    this.service = serviceName;
    this.extensions = extensions;
  }

  /**
   * @param serviceName Some arbitrary string that represents a 'service'.
   * @param services Available service instances
   * @return Matching BlockingServiceAndInterface pair
   */
  public static BlockingServiceAndTranslator getBlockingServiceAndTranslator(
      final List<BlockingServiceAndTranslator> services, final String serviceName) {
    for (BlockingServiceAndTranslator bs : services) {
      if (bs.getBlockingService().getDescriptorForType().getName().equals(serviceName)) {
        return bs;
      }
    }
    return null;
  }

  public static ProtobufServiceTranslator getMatchingTranslator(List<ProtobufServiceTranslator>
      translators, MethodDescriptor method){
    String serviceName = method.getService().getName();
    for(ProtobufServiceTranslator translator: translators){
      if(translator.service.equals(serviceName)){
        return translator;
      }
    }

    return null;
  }

  public RpcRequestResponse translate(MethodDescriptor method, Message message,
      StreamCarryingRpcController controller) {
    // first convert the request to an RPC request
    Rpc.Builder msg = makeHeader(method.getName());
    // add the request to the header
    GeneratedExtension<Rpc, Message> extension = extensions.getExtension(message);
    msg.setExtension(extension, message);

    // then create the actual rpc that will be sent
    RpcRequestResponse rpc = new RpcRequestResponse(msg);
    if (controller != null && controller.getOutboundTrailer() != null) {
      rpc.setData(controller.getOutboundTrailer(), controller.getOutboundTrailerLength());
    }

    return rpc;
  }

  private Rpc.Builder makeHeader(String method) {
    return RpcUtils.initializeHeader().setMethod(method).setService(service);
  }

  /**
   * Translate from the external RPC message to the actual message embedded as an extension in
   * the Rpc message
   *
   * @param receivedMessage generic RPC received
   * @param prototype prototype of the message that we expect to receive
   * @return the twice wrapped underlying rpc message
   */
  public Message translate(Rpc rpc, Message prototype) {
    // unwrap the underlying message, which should be bound to one of the extensions
    GeneratedMessage.GeneratedExtension<RpcMessages.Rpc, Message> responseExtension =
        extensions.getExtension(prototype);
    return rpc.getExtension(responseExtension);
  }

  /**
   * Translate the Rpc into the request type message for the method
   *
   * @param rpc from which to extract the message
   * @param blockingService service which is serving the request
   * @param md description of the method for which the request message is extracted
   * @return the request message of the method stored in the rpc
   */
  public Message translateRequest(Rpc rpc, BlockingService blockingService, MethodDescriptor md) {
    Message requestProto = blockingService.getRequestPrototype(md);
    return this.translate(rpc, requestProto);
  }
}