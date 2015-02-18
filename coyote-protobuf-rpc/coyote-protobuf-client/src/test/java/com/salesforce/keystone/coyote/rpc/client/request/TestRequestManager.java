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

package com.salesforce.keystone.coyote.rpc.client.request;

import com.salesforce.keystone.coyote.rpc.client.ResponseTranslator;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class TestRequestManager {

  @Test
  public void createAndMarkDown() throws Exception{
    RequestManager manager = new RequestManager();
    ResponseTranslator translator = Mockito.mock(ResponseTranslator.class);
    String id = "1";

    Request request = manager.createRequest(id, translator);
    assertEquals(request, manager.getRequest(id));

    request.markDone();
    assertNull(manager.getRequest(id));

    // if we create a new request, we shouldn't get the original one back still
    manager.createRequest("2", translator);
    assertNull(manager.getRequest(id));
  }

  @Test
  public void close() throws Exception{
    RequestManager manager = new RequestManager();
    ResponseTranslator translator = Mockito.mock(ResponseTranslator.class);
    String [] ids = new String[]{"1", "2", "3"};

    // add the requests
    manager.createRequest(ids[0], translator);
    manager.createRequest(ids[1], translator);
    manager.createRequest(ids[2], translator);

    //ensure that they are gone, when we close
    manager.close();

    assertNull(manager.getRequest(ids[0]));
    assertNull(manager.getRequest(ids[1]));
    assertNull(manager.getRequest(ids[2]));
  }

  @Test
  public void createDuplicates() throws Exception{
    RequestManager manager = new RequestManager();
    ResponseTranslator translator = Mockito.mock(ResponseTranslator.class);
    String id = "1";

    manager.createRequest(id, translator);
    try {
      manager.createRequest(id, translator);
      fail("Should not be able to create the same request twice");
    }catch (Exception e){
      //expected
    }
  }
}