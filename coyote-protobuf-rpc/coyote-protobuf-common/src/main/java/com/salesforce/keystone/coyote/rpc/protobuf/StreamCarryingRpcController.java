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

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import com.salesforce.keystone.coyote.netty.handler.stream.TrailerAccessorInterface;

import java.io.InputStream;

/**
 * Simple interface an RpcController that merely allows the rpc mechansim to receive and return
 * a stream of data from the RPC channel.
 */
public class StreamCarryingRpcController implements RpcController {

  private TrailerAccessorInterface inboundTrailer;
  private InputStream returnTrailer;
  private long returnTrailerLength;

  public StreamCarryingRpcController(TrailerAccessorInterface trailer) {
    this.inboundTrailer = trailer;
  }

  /**
   * Get the inboundTrailer data that was included from the remote location
   */
  public TrailerAccessorInterface getInboundTrailer() {
    return this.inboundTrailer;
  }

  /**
   * Set the inboundTrailer when inbound rpc includes a inboundTrailer, but the controller has
   * already been created (e.g. client specified inboundTrailer)
   *
   * @param trailer inboundTrailer data
   */
  public void setInboundTrailer(TrailerAccessorInterface trailer) {
    this.inboundTrailer = trailer;
  }

  /**
   * Set the inboundTrailer that will be written to the remote location
   */
  public void setOutboundTrailer(InputStream returnTrailer, long length) {
    this.returnTrailer = returnTrailer;
    this.returnTrailerLength = length;
  }

  public long getOutboundTrailerLength() {
    return returnTrailerLength;
  }

  public InputStream getOutboundTrailer() {
    return returnTrailer;
  }

  // ------------------------------------------------------------------------
  // Needed to support the RpcController interface, but aren't actually used
  // ------------------------------------------------------------------------
  @Override
  public void reset() {
  }

  @Override
  public boolean failed() {
    return false;
  }

  @Override
  public String errorText() {
    return null;
  }

  @Override
  public void startCancel() {
  }

  @Override
  public void setFailed(String reason) {
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

  @Override
  public void notifyOnCancel(RpcCallback<Object> callback) {
  }
}