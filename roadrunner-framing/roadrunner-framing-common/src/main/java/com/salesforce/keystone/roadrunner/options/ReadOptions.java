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
package com.salesforce.keystone.roadrunner.options;

/**
 * Defines Options for working with RoadRunner messages 
 * 
 * This has reasonable defaults for all settings, so generally you can just do new Options.Builder().build();
 * 
 * @author sfell
 */
public class ReadOptions {
    
    public static final long DEFAULT_MAX_TRAILER_SIZE = 20L * 1024L * 1024L * 1024L;
    public static final int DEFAULT_MAX_THREAD_POOL_SIZE = 5;
    public static final int DEFAULT_MAX_PROTOBUF_SIZE = 32 * 1024;
    
	public static class Builder {
        /**
		 * set the maximum size of the protocol buffer message in bytes. if a
		 * message header indicates that the protobuf message is larger than
		 * this an error will be thrown. the default is 32KiB
		 */
		public Builder setMaxProtoBufMessageSize(int maxBytes) {
			if (maxBytes < 1) throw new IllegalArgumentException();
			maxPbMsgSize = maxBytes;
			return this;
		}
		
		/**
		 * set the maximum size of the trailer we'll process, if a message
		 * indicates a trailer larger than this we'll throw an error, the
		 * default is 4GiB
		 */
		public Builder setMaxTrailerSize(long maxBytes) {
			if (maxBytes < 1) throw new IllegalArgumentException();
			maxTrailerSize = maxBytes;
			return this;
		}

		
		/** @return a new Options instance based on the current values */
		public ReadOptions build() {
			return new ReadOptions(maxPbMsgSize, maxTrailerSize);
		}
		
		private int maxPbMsgSize = DEFAULT_MAX_PROTOBUF_SIZE;
		private long maxTrailerSize = DEFAULT_MAX_TRAILER_SIZE;
	}
	
	private ReadOptions(int maxPbMsgSize, long maxTrailerSize){
		this.maxPbMsgSize = maxPbMsgSize;
		this.maxTrailerSize = maxTrailerSize;
	}
	
	private final int maxPbMsgSize;
	private final long maxTrailerSize;
	
	/** @return the configured max size of the protocol buffer message inside a RoadRunner frame */
	public int getMaxProtoBufMessageSize() {
		return maxPbMsgSize;
	}
	
	/** @return the configured max size in bytes of a trailer inside a RoadRunner frame */
	public long getMaxTrailerSize() {
		return maxTrailerSize;
	}
	
	/** @return a new builder based on the current options */
	public Builder toBuilder() {
		Builder b = new Builder();
		b.maxPbMsgSize = maxPbMsgSize;
		b.maxTrailerSize = maxTrailerSize;
		return b;
	}
}