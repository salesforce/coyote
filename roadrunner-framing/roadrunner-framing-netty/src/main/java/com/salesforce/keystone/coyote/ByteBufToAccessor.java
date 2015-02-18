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

package com.salesforce.keystone.coyote;

import com.salesforce.keystone.roadrunner.bytes.ByteAccessor;
import com.salesforce.keystone.roadrunner.bytes.ByteConverter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;

/**
 * Converts the integer and long reads from the buffer into 4-byte and 8-byte big endian reads,
 * respectively. All other operations are as expected from a ByteBuf
 */
public class ByteBufToAccessor implements ByteConverter<ByteBuf> {

  public static final ByteBufToAccessor INSTANCE = new ByteBufToAccessor();

  @Override
  public ByteAccessor convert(ByteBuf bb) {
    return new ByteBufAccessor(bb);
  }

  @Override
  public ByteBuf unwrap(ByteAccessor b) {
    if (b instanceof ByteBufAccessor) {
      ByteBufAccessor bba = (ByteBufAccessor) b;
      return ((ByteBufAccessor) b).getBuffer();
    }

    throw new IllegalStateException(
        "Cannot unwrap an accessor of type " + b.getClass() + ". Can only" +
            " unwrap " + ByteBufAccessor.class);
  }

  @Override
  public InputStream toInputStream(ByteBuf bytes) {
    return new ByteBufInputStream(bytes);
  }

  @Override
  public OutputStream toOutputStream(ByteAccessor b) {
    ByteBuf buffer = unwrap(b);
    return new ByteBufOutputStream(buffer);
  }

  private class ByteBufAccessor implements ByteAccessor {

    private final ByteBuf bb;

    public ByteBufAccessor(ByteBuf buffer) {
      this.bb = buffer;
    }

    public ByteBuf getBuffer() {
      return bb;
    }

    @Override
    public int position() {
      return bb.readerIndex();
    }

    @Override
    public byte get(int offset) {
      return bb.getByte(offset);
    }

    // its not clear which way is faster with ByteBuf - getting each byte individually or
    // copying them into a byte[] and then accessing them. So, for now,
    // we mix them! Again, this is also a mess if the bytebuf is on the heap,
    // in which case we might want to _not_ copy the data and just do the array access.
    // However, its just simpler for the moment to just do it one way

    @Override
    public int getInt(int index) {
      return makeInt(bb.getByte(index), bb.getByte(index + 1), bb.getByte(index + 2),
          bb.getByte(index + 3));
    }

    @Override
    public long getLong(int index) {
      byte[] data = new byte[8];
      bb.getBytes(8, data);
      return makeLong(data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7]);
    }

    @Override
    public void put(byte b) {
      bb.writeByte(b);
    }

    @Override
    public void put(byte[] b, int off, int len) {
      if (len > bb.writableBytes()) {
        throw new BufferOverflowException();
      }
      bb.writeBytes(b, off, len);
    }

    @Override
    public void putInt(int x) {
      write(bb, int3(x), int2(x), int1(x), int0(x));
    }

    @Override
    public void putLong(long x) {
      write(bb, long7(x), long6(x), long5(x), long4(x), long3(x), long2(x), long1(x), long0(x));
    }

    private void write(ByteBuf buff, byte... bytes) {
      bb.writeBytes(bytes);
    }

    @Override
    public void flip() {
    }

    @Override
    public int remaining() {
      return bb.writableBytes();
    }

    @Override
    public byte get() {
      return bb.readByte();
    }

    @Override
    public void get(byte[] b, int off, int toRead) {
      bb.readBytes(b, off, toRead);
    }
  }

  // copies of what java.nio.Bits does, but its package-private, so we copy it here

  static private int makeInt(byte b3, byte b2, byte b1, byte b0) {
    return (((b3) << 24) |
        ((b2 & 0xff) << 16) |
        ((b1 & 0xff) << 8) |
        ((b0 & 0xff)));
  }

  static private long makeLong(byte b7, byte b6, byte b5, byte b4,
      byte b3, byte b2, byte b1, byte b0) {
    return ((((long) b7) << 56) |
        (((long) b6 & 0xff) << 48) |
        (((long) b5 & 0xff) << 40) |
        (((long) b4 & 0xff) << 32) |
        (((long) b3 & 0xff) << 24) |
        (((long) b2 & 0xff) << 16) |
        (((long) b1 & 0xff) << 8) |
        (((long) b0 & 0xff)));
  }

  private static byte int3(int x) { return (byte)(x >> 24); }
  private static byte int2(int x) { return (byte)(x >> 16); }
  private static byte int1(int x) { return (byte)(x >>  8); }
  private static byte int0(int x) { return (byte)(x      ); }

  private static byte long7(long x) { return (byte)(x >> 56); }
  private static byte long6(long x) { return (byte)(x >> 48); }
  private static byte long5(long x) { return (byte)(x >> 40); }
  private static byte long4(long x) { return (byte)(x >> 32); }
  private static byte long3(long x) { return (byte)(x >> 24); }
  private static byte long2(long x) { return (byte)(x >> 16); }
  private static byte long1(long x) { return (byte)(x >>  8); }
  private static byte long0(long x) { return (byte)(x      ); }
}