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

package com.salesforce.keystone.roadrunner.memory;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.salesforce.keystone.roadrunner.bytes.ByteAccessor;
import com.salesforce.keystone.roadrunner.bytes.ByteConverter;

/**
 * Helper converter to make it easier to wrap a given byte manager into a generic ByteAccessor.
 */
public class ConvertingByteManager<B> implements BytesManager<ByteAccessor> {

  private final ByteConverter<B> converter;
  private final BytesManager<B> delegate;

  public ConvertingByteManager(ByteConverter<B> converter, BytesManager<B> delegate){
    this.converter = converter;
    this.delegate = delegate;
  }

  @Override
  public ByteAccessor allocate(int size) {
    return converter.convert(delegate.allocate(size));
  }

  @Override
  public void free(ByteAccessor b) {
    delegate.free(converter.unwrap(b));
  }

  @Override
  public void free(Iterable<ByteAccessor> buffers) {
    Iterables.transform(buffers, new Function<ByteAccessor, B>(){
      @Override
      public B apply(ByteAccessor input) {
        return converter.unwrap(input);
      }
    });
  }
}