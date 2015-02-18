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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.HashMap;

/**
 * Memoize the services represented by the parent wrapper
 */
public class ProtobufExtensionLookup {

  private static final Log LOG = LogFactory.getLog(ProtobufExtensionLookup.class);
  private final AbstractMap<Class, GeneratedMessage.GeneratedExtension> fields = new HashMap<>();

  public ProtobufExtensionLookup add(Class<?> parent) {
    // find all the generated service extensions for the class specified
    for (Field field : parent.getFields()) {
      // skip anything that isn't a generated extension. should be fine as long as we dont start
      // mucking around with class loaders
      if (field.getType() != GeneratedMessage.GeneratedExtension.class) {
        continue;
      }

      try {
        GeneratedMessage.GeneratedExtension extension =
            (GeneratedMessage.GeneratedExtension) field.get(parent);
        Message defaultMessageInst = extension.getMessageDefaultInstance();
        this.fields.put(defaultMessageInst.getClass(), extension);
      } catch (IllegalAccessException e) {
        LOG.warn("Could not not access " + field + " for " + parent);
      }
    }
    return this;
  }

  public GeneratedMessage.GeneratedExtension getExtension(Message message) {
    return fields.get(message.getClass());
  }
}
