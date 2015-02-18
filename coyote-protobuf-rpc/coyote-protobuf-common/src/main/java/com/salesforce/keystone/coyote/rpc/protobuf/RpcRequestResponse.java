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

package com.salesforce.keystone.coyote.rpc.protobuf;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.Rpc;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.Rpc.Builder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

import java.io.InputStream;

/**
 * Wrapper around an protobuf messsage request or response
 * <p>
 * Handles setting the correlationID and sequence number based on the original RPC properties
 * </p>
 */
public class RpcRequestResponse {

  public static AttributeKey<Long> RPC_REQUEST_SEQUENCE_NUMBER_KEY = AttributeKey.valueOf
      ("rpc.sequenceNumber");
  public static AttributeKey<String> RPC_REQUEST_MESSAGE_ID_KEY =
      AttributeKey.valueOf("rpc.messageID");

  private final Builder message;
  private long sequenceNumber = 0;
  private String correlationId;
  private InputStream data;
  private long dataSize;

  /*
   * Response with the message and the extension to which its bound
   */
  public RpcRequestResponse(Rpc.Builder rpc) {
    this.message = rpc;
  }

  public Rpc getMessage() {
    // set all the properties that we need on the rpc
    this.message.setSequenceNumber(this.sequenceNumber);
    // only set the correlation ID if we have one (e.g. on a response)
    if (correlationId != null) {
      this.message.setCorrelationId(this.correlationId);
    }
    return message.build();
  }

  private void setSequenceNumber(long sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }

  private void setCorrelationId(String messageId) {
    this.correlationId = messageId;
  }

  public void setData(InputStream data, long dataSize) {
    this.data = data;
    this.dataSize = dataSize;
  }

  public InputStream getData() {
    return data;
  }

  public long getDataSize() {
    return dataSize;
  }

  /**
   * Update the message parameters from the channel handled by the specified context
   *
   * @param ctx to examine for necessary message parameters
   */
  public void setAttributesFromContext(ChannelHandlerContext ctx) {
    // update the response so it knows how it should be converted
    long sequenceNumber = ctx.channel().attr(RPC_REQUEST_SEQUENCE_NUMBER_KEY).get();
    this.setSequenceNumber(++sequenceNumber);
    String correlationId = ctx.channel().attr(RPC_REQUEST_MESSAGE_ID_KEY).get();
    this.setCorrelationId(correlationId);
  }

  /**
   * Set the specified attributes onto the channel currently being handled by the current context
   * <p>
   * This has to store the attributes in the channel and not in the context because the context
   * changes between handlers, so while the initial handler may be in one context,
   * the ultimate response may come from another handler (e.g. exception is caught)
   * </p>
   *
   * @param sequenceID current sequence ID of the inbound message
   * @param messageId message ID of the message
   * @param ctx in which to set the attributes
   */
  public static void setContextAttributes(long sequenceID, String messageId,
      ChannelHandlerContext ctx) {
    ctx.channel().attr(RPC_REQUEST_SEQUENCE_NUMBER_KEY).set(sequenceID);
    ctx.channel().attr(RPC_REQUEST_MESSAGE_ID_KEY).set(messageId);
  }
}