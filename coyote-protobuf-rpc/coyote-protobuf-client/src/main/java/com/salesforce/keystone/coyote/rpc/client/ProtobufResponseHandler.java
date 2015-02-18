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
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.RemoteExceptionHolder;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.Rpc;
import com.salesforce.keystone.coyote.rpc.client.request.Request;
import com.salesforce.keystone.coyote.rpc.client.request.RequestManager;
import com.salesforce.keystone.coyote.rpc.protobuf.RoadRunnerMessageUtil;
import com.salesforce.keystone.coyote.rpc.utils.CoyoteRemoteException;
import com.salesforce.keystone.coyote.rpc.utils.RemoteExceptionUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Handles converting the final RPC calls to the actual response type
 */
public class ProtobufResponseHandler extends SimpleChannelInboundHandler<NettyRoadRunnerMessage> {

  private static final Log LOG = LogFactory.getLog(ProtobufResponseHandler.class);

  private final RequestManager requestManager;

  public ProtobufResponseHandler(RequestManager requestManager) {
    this.requestManager = requestManager;
  }

  @Override
  protected void channelRead0(final ChannelHandlerContext ctx,
      NettyRoadRunnerMessage receivedMessage) throws Exception {
    RpcMessages.Rpc rpc = RoadRunnerMessageUtil.unwrap(receivedMessage);

    // find the request to which this response maps
    Request request = this.requestManager.getRequest(rpc.getCorrelationId());
    if (request == null) {
      LOG.error("Received response, but don't have an outstanding request to serve it! " +
          "Response:" + rpc);
      return;
    }

    // this could be an exception, so we handle that separately. Otherwise, we can do the standard
    // message handling
    if (rpc.hasRemoteException()) {
      handleException(request, rpc);
      return;
    }

    ResponseTranslator translator = request.getResponseTranslator();
    Message response = translator.buildResponse(rpc, receivedMessage);
    request.receiveResponse(response);
  }

  private void handleException(Request request, Rpc rpc) {
    RemoteExceptionHolder remote = rpc.getRemoteException();
    CoyoteRemoteException exception = RemoteExceptionUtils.unmarshalRemoteException(remote);
    request.receiveException(exception);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    // some exception caught in the channel - don't know which request this is tied to,
    // so call we can do is just log it
    LOG.error("Client got an error from the RPC channel", cause);
    super.exceptionCaught(ctx, cause);
  }
}