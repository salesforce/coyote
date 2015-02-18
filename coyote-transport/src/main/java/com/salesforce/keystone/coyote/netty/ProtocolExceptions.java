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

package com.salesforce.keystone.coyote.netty;

import java.io.IOException;

public class ProtocolExceptions {
  private static final String PROTOCOL_ERROR_UNEXPECTED_RESPONSE =
      "Protocol error: response message is not for the pending request";
  private static final String PROTOCOL_ERROR_INCORRECT_RESPONSE_TYPE =
      "Protocol error: response message is not the correct type";
  private static final String PROTOCOL_ERROR_MISSING_TRAILER =
      "Protocol error: missing extent data in the stream";
  private static final String PROTOCOL_ERROR_UNEXPECTED_TRAILER =
      "Protocol error: unexpected data in the stream";
  private static final String PROTOCOL_ERROR_UNEXPECTED_MESSAGE_TYPE =
      "Protocol error: unexpected RPC message type";
  private static final String PROTOCOL_ERROR_EXTENT_DATA_NOT_RETURNED =
      "Protocol error: Extent data was not returned from the Vault";
  private static final String PROTOCOL_ERROR_MISSING_METHOD =
      "Protocol error: RPC method name was not supplied";

  public static class UnexpectedResponseException extends IOException {
    public UnexpectedResponseException() {
      super(PROTOCOL_ERROR_UNEXPECTED_RESPONSE);
    }
  }

  public static class IncorrectResponseTypeException extends IOException {
    public IncorrectResponseTypeException() {
      super(PROTOCOL_ERROR_INCORRECT_RESPONSE_TYPE);
    }
  }

  public static class MissingTrailerException extends IOException {
    public MissingTrailerException() {
      super(PROTOCOL_ERROR_MISSING_TRAILER);
    }
  }

  public static class UnexpectedTrailerException extends IOException {
    public UnexpectedTrailerException() {
      super(PROTOCOL_ERROR_UNEXPECTED_TRAILER);
    }
  }

  public static class UnexpectedMessageTypeException extends IOException {
    public UnexpectedMessageTypeException() {
      super(PROTOCOL_ERROR_UNEXPECTED_MESSAGE_TYPE);
    }
  }

  public static class ExtentDataNotReturned extends IOException {
    public ExtentDataNotReturned() {
      super(PROTOCOL_ERROR_EXTENT_DATA_NOT_RETURNED);
    }
  }

  public static class MissingMethodException extends IOException {
    public MissingMethodException() {
      super(PROTOCOL_ERROR_MISSING_METHOD);
    }
  }

  /**
   * Exception for when we receive an RPC with a method name not specified in the configured
   * method map
   */
  @SuppressWarnings("serial")
  public static class NoHandlerForMethodException extends IOException {
    private final String method;

    public NoHandlerForMethodException(String method) {
      super("No handler was supplied for method: '" + method + "'");
      this.method = method;
    }

    public String getMethod() {
      return method;
    }
  }
}
