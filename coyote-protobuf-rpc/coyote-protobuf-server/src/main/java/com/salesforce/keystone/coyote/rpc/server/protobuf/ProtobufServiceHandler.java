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

package com.salesforce.keystone.coyote.rpc.server.protobuf;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.netty.ProtocolExceptions;
import com.salesforce.keystone.coyote.netty.handler.stream.NettyTrailerAccessor;
import com.salesforce.keystone.coyote.netty.roadrunner.NettyInboundRoadRunnerMessage;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages;
import com.salesforce.keystone.coyote.rpc.exception.IncorrectServiceNameException;
import com.salesforce.keystone.coyote.rpc.protobuf.service.BlockingServiceAndTranslator;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceInitializer;
import com.salesforce.keystone.coyote.rpc.protobuf.service.ProtobufServiceTranslator;
import com.salesforce.keystone.coyote.rpc.protobuf.RoadRunnerMessageUtil;
import com.salesforce.keystone.coyote.rpc.protobuf.RpcRequestResponse;
import com.salesforce.keystone.coyote.rpc.protobuf.StreamCarryingRpcController;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * Handles delegating calls from a RPC request to an actual service implementation
 */
public class ProtobufServiceHandler extends
    SimpleChannelInboundHandler<NettyInboundRoadRunnerMessage> {

  private static final Log LOG = LogFactory.getLog(ProtobufServiceHandler.class);

  private final List<BlockingServiceAndTranslator> services;
  private final ListeningExecutorService pool;

  public ProtobufServiceHandler(ProtobufServiceInitializer initializer, ExecutorService pool) {
    this.pool = MoreExecutors.listeningDecorator(pool);
    this.services = initializer.getServices();
  }

  protected void channelRead0(final ChannelHandlerContext ctx,
      NettyInboundRoadRunnerMessage receivedMessage) throws Exception {
    final RpcMessages.Rpc rpc = RoadRunnerMessageUtil.unwrap(receivedMessage);

    // find the blocking service for the call
    String serviceName = rpc.getService();
    final BlockingServiceAndTranslator service = ProtobufServiceTranslator
        .getBlockingServiceAndTranslator(services, serviceName);
    if (service == null) {
      throw new IncorrectServiceNameException(serviceName);
    }

    // ensure that we can serve this method request
    if (!rpc.hasMethod()) {
      // Remote caller didn't send a method
      throw new ProtocolExceptions.MissingMethodException();
    }
    final Descriptors.MethodDescriptor md =
        service.getBlockingService().getDescriptorForType().findMethodByName(rpc.getMethod());
    if (md == null) {
      throw new ProtocolExceptions.NoHandlerForMethodException(rpc.getMethod());
    }

    // set the current request context. this only works because we assume the rpcs are
    // synchronous. once we have a client that will send multiple updates over the same channel
    // we need to be smarter when binding requests and responses
    RpcRequestResponse
        .setContextAttributes(rpc.getSequenceNumber(), rpc.getMessageId(), ctx);

    final StreamCarryingRpcController controller = new StreamCarryingRpcController(receivedMessage
        .getTrailerLen() == 0 ? null : new NettyTrailerAccessor(receivedMessage,
        receivedMessage.getChunkSize()));

    // get the request prototype from the service
    final Message request =
        service.getTranslator().translateRequest(rpc, service.getBlockingService(), md);

    // submit the request to the service and listen for the response
    ListenableFuture<RpcRequestResponse> response = pool.submit(
        new Callable<RpcRequestResponse>() {
          @Override
          public RpcRequestResponse call() throws Exception {
            // get the simple response
            Message response = service.getBlockingService().callBlockingMethod(
                md, controller, request);
            // wrap it into an rpc response that we can send
            return service.getTranslator().translate(md, response,
                controller);
          }
        });

    // listen for the result so we can handle it
    Futures.addCallback(response, new FutureCallback<RpcRequestResponse>() {

      public void onSuccess(RpcRequestResponse response) {
        response.setAttributesFromContext(ctx);
        // pass it along the channel
        ctx.write(response, ctx.voidPromise());
      }

      public void onFailure(Throwable thrown) {
        LOG.debug("Got an error while handling rpc response, passing onto channel handlers!",
            thrown);
        ctx.fireExceptionCaught(thrown);
      }

    });
  }
}