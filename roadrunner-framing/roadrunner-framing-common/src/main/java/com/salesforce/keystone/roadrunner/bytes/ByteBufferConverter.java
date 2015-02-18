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

package com.salesforce.keystone.roadrunner.bytes;

import com.salesforce.keystone.roadrunner.nio.streams.ByteBufferInputStream;
import com.salesforce.keystone.roadrunner.nio.streams.ByteBufferOutputStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Simple converter that just delegates all calls to the bytebuffer itself
 */
public class ByteBufferConverter implements ByteConverter<ByteBuffer> {

  public static final ByteBufferConverter INSTANCE = new ByteBufferConverter();
  private ByteBufferConverter(){}

  @Override
  public ByteAccessor convert(final ByteBuffer h) {
    return new ByteBufferAccessor(h);
  }

  @Override
  public ByteBuffer unwrap(ByteAccessor b) {
    if (b instanceof ByteBufferAccessor) {
      ByteBufferAccessor bba = (ByteBufferAccessor) b;
      return ((ByteBufferAccessor) b).getBuffer();
    }

    throw new IllegalStateException(
        "Cannot unwrap an accessor of type " + b.getClass() + ". Can only" +
            " unwrap " + ByteBufferAccessor.class);
  }

  @Override
  public InputStream toInputStream(ByteBuffer buffer){
    return new ByteBufferInputStream(buffer);
  }

  @Override
  public OutputStream toOutputStream(ByteAccessor b) {
    ByteBuffer buffer = unwrap(b);
    return new ByteBufferOutputStream(buffer);
  }

  private class ByteBufferAccessor implements ByteAccessor {
    private final ByteBuffer buffer;

    public ByteBufferAccessor(ByteBuffer buffer) {
      this.buffer = buffer;
    }

    public ByteBuffer getBuffer() {
      return this.buffer;
    }

    @Override
    public int position() {
      return buffer.position();
    }

    @Override
    public byte get(int offset) {
      return buffer.get(offset);
    }

    @Override
    public int getInt(int index) {
      return buffer.getInt(index);
    }

    @Override
    public long getLong(int index) {
      return buffer.getLong(index);
    }

    @Override
    public void put(byte b) {
      buffer.put(b);
    }

    @Override
    public void put(byte[] b, int off, int len) {
      buffer.put(b, off, len);
    }

    @Override
    public void putInt(int value) {
      buffer.putInt(value);
    }

    @Override
    public void putLong(long value) {
      buffer.putLong(value);
    }

    @Override
    public void flip() {
      buffer.flip();
    }

    @Override
    public int remaining() {
      return buffer.remaining();
    }

    @Override
    public byte get() {
      return buffer.get();
    }

    @Override
    public void get(byte[] b, int off, int toRead) {
      buffer.get(b, off, toRead);
    }
  }
}