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
package com.salesforce.keystone.roadrunner.msgmapper;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.GeneratedMessage.Builder;
import com.google.protobuf.Message;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper for implementing MessageMapper, construct one of this with a map of MessageClass -> msgId and this'll do the rest of the work for you.
 * 
 * [TODO, can we add a protoc plugin to generate the necessary constructors]
 * 
 *  @author sfell
 */
public class MessageMapperImpl implements MessageMapper {

	public MessageMapperImpl(Map<Class<? extends Message>, Integer> messages) {
	    this(messages, null);
	}

    public MessageMapperImpl(Map<Class<? extends Message>, Integer> messages,
        ExtensionRegistry registry) {
        extensionRegistry = registry;
        types = new HashMap<>(messages);
        builders = new HashMap<>();
        // now create the inverse lookup
        for (Map.Entry<Class<? extends Message>, Integer> e : types.entrySet()) {
            Method existing = builders.put(e.getValue(), getBuilderMethod(e.getKey()));
            if (existing != null)
                throw new RuntimeException("Mapping for class " + e.getKey() + " has messagType of " + e.getValue() + " but that is already used by " + existing.getDeclaringClass());
            if (e.getValue() > 255)
                throw new RuntimeException("MessageType for class " + e.getKey() + " has a messageType of " + e.getValue() + " but 255 is the maximum allowed");
        }
    }
	
	private final Map<Class<? extends Message>, Integer> types;
	private final Map<Integer, Method> builders;
	private final ExtensionRegistry extensionRegistry;
	
	private static Method getBuilderMethod(Class<? extends Message> c) {
		try {
			return c.getMethod("newBuilder");
			
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException ("MessageClass " + c + " has no static newBuilder() method!, are you sure it was generated with protoc?", e);
		}
	}
	
	@Override
	public Builder<?> newInstance(byte msgId) {
		Method builder = builders.get(Integer.valueOf(msgId));
		if (builder == null) return null;
		try {
			return (Builder<?>) builder.invoke(null);
			
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException ("Unable to create new builder instance for msgId " + msgId, e);
		}
	}

	@Override
	public boolean supportsMessageId(byte messageId) {
		return builders.containsKey(Integer.valueOf(messageId));
	}

	@Override
	public byte messageIdOf(Message m, boolean throwIfInvalid) {
		Integer i = types.get(m.getClass());
		if (i == null && throwIfInvalid) throw new UnknownMessageTypeException(m);
		return i != null ? i.byteValue() : 0;
	}

    @Override
    public boolean hasExtensionRegistry() {
        return extensionRegistry != null;
    }

    @Override
    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }
}
