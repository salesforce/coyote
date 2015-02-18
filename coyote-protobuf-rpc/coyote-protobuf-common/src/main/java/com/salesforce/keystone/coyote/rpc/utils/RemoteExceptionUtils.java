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
package com.salesforce.keystone.coyote.rpc.utils;

import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.RemoteException;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.RemoteExceptionHolder;
import com.salesforce.keystone.coyote.protobuf.generated.RpcMessages.StackTraceEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for translating {@link RemoteException} to {@link RemoteException}.
 */
public class RemoteExceptionUtils {
  /**
   * Maximum number of entries in a stack trace that will be marshaled.
   */
  public static final int STACK_TRACE_LIMIT = 10;

  /**
   * Unmarshal a set of remote exceptions.
   * <p>
   * There may be one or more exceptions in the {@link RemoteExceptionHolder}. The first exception
   * is the primary exception; any other exceptions are linked together as a chain of exception
   * causes.
   * </p>
   *
   * @param remoteExceptionHolder the container of exceptions
   * @return a new {@link CoyoteRemoteException}
   * @see Throwable#getCause()
   */
  public static CoyoteRemoteException unmarshalRemoteException(
      RemoteExceptionHolder remoteExceptionHolder) {
    CoyoteRemoteException[] exceptions =
        new CoyoteRemoteException[remoteExceptionHolder.getRemoteExceptionsCount()];

    int nCauses = remoteExceptionHolder.getRemoteExceptionsCount() - 1;
    for (int i = 0; i < remoteExceptionHolder.getRemoteExceptionsCount(); i++) {
      exceptions[i] = RemoteExceptionUtils
          .unmarshalSingleRemoteException(remoteExceptionHolder.getRemoteExceptions(i));
    }

    for (int i = 0; i < nCauses; i++) {
      exceptions[i].initCause(exceptions[i + 1]);
    }

    return exceptions[0];
  }

  /*
   * Create a CoyoteRemoteException from exception passed in RPC result
   *
   * The remote exception is wrapped in a CoyoteRemoteException because the
   * exception class from the server may not be present in the client.
   */
  private static CoyoteRemoteException unmarshalSingleRemoteException(RemoteException remoteExc) {
    StackTraceElement[] stackTrace = null;
    if (remoteExc.getStackCount() > 0) {
      stackTrace = new StackTraceElement[remoteExc.getStackCount()];

      for (int i = 0; i < stackTrace.length; i++) {
        StackTraceEntry entry = remoteExc.getStack(i);
        stackTrace[i] = new StackTraceElement(entry.getDeclaringClass(),
            entry.getMethodName(),
            entry.getFileName(),
            entry.getLineNumber());
      }
    } else {
      // Create a fake stack entry, otherwise Java will give the current stack trace
      stackTrace = new StackTraceElement[1];
      stackTrace[0] = new StackTraceElement(remoteExc.getClassname(), "", "", 0);
    }

    CoyoteRemoteException exc =
        new CoyoteRemoteException(remoteExc.getMessage(), remoteExc.getClassname());
    exc.setStackTrace(stackTrace);

    return exc;
  }

  /**
   * Marshal an exception for transfer to remote peer.
   * <p>
   * The exception and its stack trace are marshaled along with the chain of exceptions
   * that are the cause of this exception.
   * </p>
   *
   * @param exc the exception to marshal
   * @return the {@link RemoteExceptionHolder} containing the marshaled exception and its causes
   * (if any).
   */
  public static RemoteExceptionHolder marshalRemoteException(Throwable exc) {
    RemoteExceptionHolder.Builder remoteExcHolder = RemoteExceptionHolder.newBuilder();
    RemoteException remoteExc = marshalSingleRemoteException(exc);

    List<RemoteException> causes = new ArrayList<>();
    causes.add(remoteExc);

    Throwable cause = exc.getCause();
    while (cause != null) {
      causes.add(marshalSingleRemoteException(cause));
      cause = cause.getCause();
    }

    remoteExcHolder.addAllRemoteExceptions(causes);

    return remoteExcHolder.build();
  }

  /*
   * Create a RemoteException from an exception.
   */
  private static RemoteException marshalSingleRemoteException(Throwable exc) {
    RemoteException.Builder remoteExc = RemoteException.newBuilder();
    remoteExc.setClassname(exc.getClass().getName());
    if (exc.getMessage() != null) {
      remoteExc.setMessage(exc.getMessage());
    }

    StackTraceElement[] stackTrace = exc.getStackTrace();
    for (int i = 0; i < stackTrace.length && i < STACK_TRACE_LIMIT; i++) {
      StackTraceElement ste = stackTrace[i];
      StackTraceEntry.Builder remoteSte = StackTraceEntry.newBuilder();

      remoteSte.setDeclaringClass(ste.getClassName());
      remoteSte.setMethodName(ste.getMethodName());
      remoteSte.setFileName(ste.getFileName());
      remoteSte.setLineNumber(ste.getLineNumber());

      remoteExc.addStack(i, remoteSte);
    }
    return remoteExc.build();
  }
}