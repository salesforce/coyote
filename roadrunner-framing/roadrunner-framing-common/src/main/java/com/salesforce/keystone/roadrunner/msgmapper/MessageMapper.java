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

/** 
 * MessageMapper provides a way for a service to indicate the msgId <-> Message mappings.
 * 
 * See MessageMappImpl for a helper implementation of this.
 * 
 * @author sfell
 */
public interface MessageMapper {
	
	/** @return a new message builder for this messageId, or null if the messageId is unknown */
	public Builder<?> newInstance(byte messageId);
	
	/** @return true if this messageId is supported/has a known Message mapping, false otherwise */
	public boolean supportsMessageId(byte messageId);
	
	/** @return the MessageId of this particular message instance, 
	  * if there's no known mapping, it should return 0, unless throwIfInvalid is true
	  * in which case a RuntimeExcetpion will should be thrown */
	public byte messageIdOf(Message m, boolean thowIfInvalid) throws UnknownMessageTypeException;

	/** 
	 * Message may use extensions which requires the use of an ExtensionRegistry
	 * 
	 * @return true if the MessageMapper has an ExtensionRegistry
	 */
	public boolean hasExtensionRegistry();
	
	/**
	 * Get the ExtensionRegistry associated with this mapper.
	 * 
	 * @return the ExtensionRegistry or null
	 */
	public ExtensionRegistry getExtensionRegistry();
	
	/** Thrown by messageIdOf if the supplied message doesn't have a registered messageId */
	public static class UnknownMessageTypeException extends RuntimeException {
		
		private static final long serialVersionUID = 1L;

		UnknownMessageTypeException(Message m) {
			this("No messageId registered for type " + m.getClass());
		}
		
		UnknownMessageTypeException(String msg) {
			super(msg);
		}
		
		UnknownMessageTypeException(String msg, Throwable cause) {
			super(msg, cause);
		}
	}
}