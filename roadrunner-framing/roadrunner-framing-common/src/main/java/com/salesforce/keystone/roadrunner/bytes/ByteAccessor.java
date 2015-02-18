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

import java.nio.BufferOverflowException;
import java.nio.ReadOnlyBufferException;

/**
 * Mapping interface between ByteBuffer and ByteBuf. This is necessary because  ByteBuffer stores
 * things as 4 byte ints and 8 byte longs, but that is not how ByteBuf lets you access data,
 * so we need to map between the two
 */
public interface ByteAccessor {

  public int position();

  public byte get(int offset);

  public int getInt(int index);

  public long getLong(int index);

  void put(byte b);

  /**
   * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
   *
   * <p> This method transfers bytes into this buffer from the given
   * source array.  If there are more bytes to be copied from the array
   * than remain in this buffer, that is, if
   * <tt>length</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>, then no
   * bytes are transferred and a {@link BufferOverflowException} is
   * thrown.
   *
   * <p> Otherwise, this method copies <tt>length</tt> bytes from the
   * given array into this buffer, starting at the given offset in the array
   * and at the current position of this buffer.  The position of this buffer
   * is then incremented by <tt>length</tt>.
   *
   * <p> In other words, an invocation of this method of the form
   * <tt>dst.put(src,&nbsp;off,&nbsp;len)</tt> has exactly the same effect as
   * the loop
   *
   * <pre>
   *     for (int i = off; i < off + len; i++)
   *         dst.put(a[i]); </pre>
   *
   * except that it first checks that there is sufficient space in this
   * buffer and it is potentially much more efficient. </p>
   *
   * @param  src
   *         The array from which bytes are to be read
   *
   * @param  offset
   *         The offset within the array of the first byte to be read;
   *         must be non-negative and no larger than <tt>array.length</tt>
   *
   * @param  length
   *         The number of bytes to be read from the given array;
   *         must be non-negative and no larger than
   *         <tt>array.length - offset</tt>
   *
   * @return  This buffer
   *
   * @throws  BufferOverflowException
   *          If there is insufficient space in this buffer
   *
   * @throws  IndexOutOfBoundsException
   *          If the preconditions on the <tt>offset</tt> and <tt>length</tt>
   *          parameters do not hold
   *
   * @throws ReadOnlyBufferException
   *          If this buffer is read-only
   */
  void put(byte[] b, int off, int len);

  void putInt(int i);

  void putLong(long l);

  /**
   * Prepare the underlying buffer for reading the most recently written data.
   * <p> After a sequence of channel-read or <i>put</i> operations, invoke
   * this method to prepare for a sequence of channel-write or relative
   * <i>get</i> operations.  For example:
   *
   * <blockquote><pre>
   * buf.put(magic);    // Prepend header
   * in.read(buf);      // Read data into rest of buffer
   * buf.flip();        // Flip buffer
   * out.write(buf);    // Write header + data to channel</pre></blockquote>
   *
   * <p> This method is often used in conjunction with the {@link
   * java.nio.ByteBuffer#compact compact} method when transferring data from
   * one place to another.  </p>
   */
  void flip();

  /**
   * @return number of bytes available to write in the current buffer
   */
  int remaining();

  byte get();

  void get(byte[] b, int off, int toRead);
}