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

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message;
import com.salesforce.keystone.coyote.netty.ProtocolExceptions;
import com.salesforce.keystone.coyote.netty.ProtocolExceptions.UnexpectedMessageTypeException;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.Rpc;
import com.salesforce.keystone.roadrunner.RoadRunnerMessage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RoadRunnerMessageUtil {

  private static final Log LOG = LogFactory.getLog(RoadRunnerMessageUtil.class);

  private RoadRunnerMessageUtil() {
  }

  /**
   * @param receivedMessage the com.salesforce.keystone.roadrunner.roadrunner message from the RPC
   * @return the wrapped Rpc message
   * @throws UnexpectedMessageTypeException if we didn't get a {@link RpcMessages.Rpc}
   */
  public static Rpc unwrap(RoadRunnerMessage receivedMessage) throws
      UnexpectedMessageTypeException {
    final long trailerLength = receivedMessage.getTrailerLen();
    // early exit if there is a negative trailer length
    if (trailerLength == -1) {
      throw new IllegalStateException("Got a trailer with length " + trailerLength);
    }

    Message message = receivedMessage.getMessage();
    if (!(message instanceof RpcMessages.Rpc)) {
      // Did not get the correct response
      LOG.debug("Received unrecognized message from client");
      throw new ProtocolExceptions.UnexpectedMessageTypeException();
    }
    return (RpcMessages.Rpc) message;
  }
}
