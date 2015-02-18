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

package com.salesforce.keystone.coyote.rpc.client.channel;

import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.rpc.client.ResponseTranslator;
import com.salesforce.keystone.coyote.rpc.client.request.Request;
import com.salesforce.keystone.coyote.rpc.client.request.Request.ResponseListener;
import com.salesforce.keystone.coyote.rpc.client.request.RequestManager;
import com.salesforce.keystone.coyote.rpc.protobuf.RpcRequestResponse;
import com.salesforce.keystone.coyote.rpc.protobuf.ServiceAndTranslator;
import com.salesforce.keystone.coyote.rpc.protobuf.StreamCarryingRpcController;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceTranslator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Channel for passing asynchronous RPC requests to the server
 */
public class RpcChannel {
  private static final Log LOG = LogFactory.getLog(RpcChannel.class);

  private final Channel ch;
  private final List<ProtobufServiceTranslator> services;
  private final RequestManager requests;

  public RpcChannel(Channel ch, List<ProtobufServiceTranslator> services, RequestManager requests) {
    this.requests = requests;
    this.services = services;
    this.ch = ch;
  }

  /**
   * Call the given method of the remote service.
   * <p></p>
   * This method is similar to
   * {@code Service.callMethod()} with one important difference:  the caller
   * decides the types of the {@code Message} objects, not the callee.  The
   * request may be of any type as long as
   * {@code request.getDescriptor() == method.getInputType()}.
   * The response passed to the callback will be of the same type as
   * {@code responsePrototype} (which must have
   * {@code getDescriptor() == method.getOutputType()}).
   * <p>Preconditions:
   * <ul>
   *   <li>{@code method.getService() == getDescriptorForType()}
   *   <li>{@code mesg} is of the exact same class as the object returned by
   *       {@code getRequestPrototype(method)}.
   *   <li>{@code controller} is of the correct type for the RPC implementation
   *       being used by this Service.  For stubs, the "correct type" depends
   *       on the RpcChannel which the stub is using.  Server-side Service
   *       implementations are expected to accept whatever type of
   *       {@code RpcController} the server-side RPC implementation uses.
   * </ul>
   * <p>Postconditions:
   * <ul>
   *   <li>{@code done} will be called when the method is complete.  This may be
   *       before {@code callMethod()} returns or it may be at some point in
   *       the future.
   *   <li>The parameter to {@code done} is the response. It is either a successful result from
   *   the server or an exception, and the appropriate method will be called respectively</li>
   * </ul>
   */
  public Request callMethod(MethodDescriptor method, StreamCarryingRpcController controller,
      Message msg, Message responsePrototype, final ResponseListener<Message> done) {
    // get the service information we need
    ProtobufServiceTranslator translator = ProtobufServiceTranslator.getMatchingTranslator
        (services, method);

    // convert the message to an RPC
    final RpcRequestResponse rpc = translator.translate(method, msg, controller);

    // Listenable for the response - clients use this to receive updates about message and
    // exceptions
    Request request = requests.createRequest(rpc.getMessage().getMessageId(),
        new ResponseTranslator(responsePrototype, translator, controller));
    request.addResponseListener(done);

    // do the actual message write
    writeMessage(rpc, request);
    return request;
  }

  private void writeMessage(final RpcRequestResponse rpc, final Request request) {
    // write the message down the pipeline
    ChannelFuture future = ch.pipeline().write(rpc);

    // update the done callback if there is an exception while writing the request
    future.addListener(new GenericFutureListener() {
      @Override
      public void operationComplete(Future future) throws Exception {
        boolean success = future.isSuccess();
        if (LOG.isTraceEnabled()) {
          LOG.trace("Writing rpc " + rpc + " to the channel " + (success ?
              "completed successfully!" : "failed because " + future.cause()));
        }
        // error handling
        if (!success) {
          Throwable cause = future.cause();
          LOG.error("Client got an error writing to the RPC channel", cause);
          request.receiveException(cause);
        }
      }
    });
  }

  public boolean isOpen() {
    return ch.isOpen();
  }
}