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

package com.salesforce.keystone.coyote.example.server;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import com.salesforce.keystone.coyote.netty.handler.stream.TrailerAccessorInterface;
import com.salesforce.keystone.coyote.rpc.protobuf.StreamCarryingRpcController;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import salesforce.coyote.example.Example.StreamRequest;
import salesforce.coyote.example.Example.StreamResponse;
import salesforce.coyote.example.Example.StreamService.BlockingInterface;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

/**
 * Handles streaming RPC requests by just echoing them back to the client
 */
public class StreamRpcService implements BlockingInterface {

  private static final Log LOG = LogFactory.getLog(StreamRpcService.class);

  @Override
  public StreamResponse stream(RpcController controller, StreamRequest request)
      throws ServiceException {
    LOG.info("Received rpc request: " + request + " with controller: " + controller);

    // log out the results
    StreamCarryingRpcController stream = (StreamCarryingRpcController) controller;
    TrailerAccessorInterface trailer = stream.getInboundTrailer();
    if (trailer == null) {
      LOG.info("Didn't get any trailer, responding with empty stream");
      return StreamResponse.newBuilder().build();
    }

    try {
      doTrailerReadWrite(trailer, stream);
    } catch (IOException | InterruptedException e) {
      throw new ServiceException(e);
    }
    return StreamResponse.newBuilder().build();
  }

  private void doTrailerReadWrite(TrailerAccessorInterface trailer,
      StreamCarryingRpcController
          stream)
      throws IOException, InterruptedException {
    LOG.info("Got a trailer with length:" + trailer.getTrailerLength());

    CopyOutputStream os = new CopyOutputStream();
    trailer.writeOut(os);
    FromBytesInputStream is = new FromBytesInputStream(trailer.getTrailerLength(), os.data);

    // just set the return stream as the input stream, but log it as we go
    stream.setOutboundTrailer(is, trailer.getTrailerLength());
  }

  private class CopyOutputStream extends OutputStream {
    Queue<int[]> data = new ArrayDeque<>();

    int[] bytes = new int[Integer.MAX_VALUE];
    int index = 0;

    @Override
    public void write(int b) throws IOException {
      // make new data storage array
      if (index == Integer.MAX_VALUE) {
        data.add(bytes);
        bytes = new int[Integer.MAX_VALUE];
      }
      bytes[index++] = b;
    }
  }

  private class FromBytesInputStream extends InputStream {
    private final long size;
    private long count = 0;
    private final Queue<int[]> data;
    private int[] current;
    private int index = 0;

    public FromBytesInputStream(long size, Queue<int[]> data) {
      this.size = size;
      this.data = data;
      current = data.remove();
    }

    @Override
    public int read() throws IOException {
      if (index == Integer.MAX_VALUE) {
        index = 0;
        current = data.remove();
      }
      // no more data left based on the size, so return less than 0
      if (count++ > size) {
        return -1;
      }

      return current[index++];
    }
  }

  private class LoggingTrailer implements TrailerAccessorInterface {
    private final TrailerAccessorInterface delegate;

    public LoggingTrailer(TrailerAccessorInterface delegate) {
      this.delegate = delegate;
    }

    @Override
    public long getTrailerLength() {
      return delegate.getTrailerLength();
    }

    @Override
    public byte[] writeOut(int length) throws IOException, InterruptedException {
      byte[] out = delegate.writeOut(length);
      LOG.info("Got data: " + Arrays.toString(out));
      return out;
    }

    @Override
    public void writeOut(final OutputStream out) throws IOException, InterruptedException {
      LOG.info("Got data:");
      OutputStream os = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
          LOG.info(b + ",");
        }
      };
    }
  }
}