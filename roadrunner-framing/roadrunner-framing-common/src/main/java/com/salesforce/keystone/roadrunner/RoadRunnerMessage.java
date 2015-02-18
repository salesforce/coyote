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
package com.salesforce.keystone.roadrunner;

import com.google.protobuf.Message;
import com.salesforce.keystone.roadrunner.header.RoadRunnerHeader;

import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * Java representation of a RoadRunner Message.
 *<p>
 * The on the wire representation is as follows
 * <ul>
 * <li>Byte 0 : Version # - currently 43;
 * <li>Byte 1 : Message Id
 * <li>Byte 2 : Spare
 * <li>Byte 3 : Spare
 * <li>Byte 4-7 : Length of Message in bytes, big-endian [msgLen]
 * <li>Byte 8-11 : Length of Trailer in bytes, big-endian [trailerLen]
 * </ul>
 * <p> which is encapsulated as a {@link RoadRunnerHeader}. Followed by:</p>
 * </ul>
 * <li>[xxx]	Serialized Message [exactly msgLen long]
 * <li>[xxx]	Optional binary trailer, [exactly trailerLen long]
 * </ul>
 * <p>
 * Reading the trailer from the message is left up to the implementing class(es) as it will vary
 * based on way data is read from the wire
 * </p>
 */
public interface RoadRunnerMessage {

	static final byte CURRENT_VERSION = 43;

	/** @return the version # of this particular message */
	byte getVersion();

	/** @return the messageId of the actual protobuf message part of this frame */
	byte getMsgId();

	/** @return the total number of bytes that the protobuf message takes up on the wire */
	int getMsgLen();

	/** @return the total number of bytes of the optional binary trailer in this frame */
	long getTrailerLen();
	
	/** @return the processed protobuf message */
	Message getMessage();

	/** @return true if this message includes a binary data trailer */
	boolean hasTrailer();
	
	/**
	 * data provider for the trailer when writing the ProtoBufMessage out, the call to hasNext/next() provided by the iterator
	 * shouldn't block, as this will block the channel from any other messages being written. For really larger trailers
	 * you'll at least want it to be partially in memory before passing the protobuf to channel.write(), and you'll want to be able
	 * to quickly fetch the remaining data.
	 * 
	 * An exception will be thrown by the channel,and the connection reset if the total of all the buffers returned here don't total
	 * the size reported by getTrailerLen(). If this sequence of buffers has more data than reported by getTrailerLen(), only getTrailerLen()
	 * amount will be written to the channel, and the remainder will be ignored
	 */
	Iterator<ByteBuffer> trailerDataForWriting();
	
	/**
	 * Called by the channel after its finished writing this ByteBuffer (which it got via trailerDataForWriting). The message can choose
	 * to recycle the buffer, hold on to it, or throw it away as it sees fit.
	 */
	void wroteTrailer(ByteBuffer b);
}
