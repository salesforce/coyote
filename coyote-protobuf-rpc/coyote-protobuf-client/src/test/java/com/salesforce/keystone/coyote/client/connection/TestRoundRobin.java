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

package com.salesforce.keystone.coyote.client.connection;

import com.salesforce.keystone.coyote.rpc.client.connection.RoundRobin;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestRoundRobin {

  private final List<String> objects = new ArrayList<>();
  private final String one = "one";
  private final String two = "two";

  {
    objects.add(one);
    objects.add(two);
  }

  @Test
  public void testNoTraversalsAndNoRetries() {
    // allow it to attempt to be retried after every traversal, but don't allow it to be retried
    RoundRobin<String> rr = new RoundRobin<>(0, 0, 1);
    rr.replaceElements(objects);

    RoundRobin.Element e = rr.next();
    assertEquals(one, e.get());

    e.markInvalid();

    e = rr.next();
    assertEquals(two, e.get());

    // should be at a new iteration, so we should be getting a just the second one again
    assertEquals(two, rr.next().get());
  }

  @Test
  public void testNoTraversalsAndOneRetry() {
    // allow a retry after a traversal
    RoundRobin<String> rr = new RoundRobin<>(0, 1, 1);
    rr.replaceElements(objects);

    RoundRobin.Element e = rr.next();
    assertEquals(one, e.get());
    e.markInvalid();
    assertEquals(two, rr.next().get());
    assertEquals(one, rr.next().get());
    // mark it a success the second time
    e.markValid();
    assertEquals(two, rr.next().get());
    assertEquals(one, rr.next().get());

    // now mark it a failure twice (we retried outside of the round robin logic)
    e.markInvalid();
    e.markInvalid();
    assertEquals(two, rr.next().get());
    assertEquals("Didn't skip the failed element", two, rr.next().get());
  }

  @Test
  public void testOneTraversalsAndOneRetry() {
    // allow a retry after a traversal
    RoundRobin<String> rr = new RoundRobin<>(1, 1, 1);
    rr.replaceElements(objects);

    RoundRobin.Element e = rr.next();
    assertEquals(one, e.get());
    e.markInvalid();
    assertEquals(two, rr.next().get());
    assertEquals("Didn't skip the failed element on the first traversal", two, rr.next().get());
    assertEquals("Didn't return failed element on second traversal", one, rr.next().get());
    assertEquals(two, rr.next().get());
  }

  @Test
  public void testRestoreOriginalSetWhenAllFail() throws Exception {
    RoundRobin<String> rr = new RoundRobin<>(1, 0, 1);
    rr.replaceElements(objects);

    RoundRobin.Element e = rr.next();
    assertEquals(one, e.get());
    e.markInvalid();
    e = rr.next();
    assertEquals(two, e.get());
    e.markInvalid();

    // here we should have run out of elements, but then restored the entire original set,
    // getting us back to "one"
    assertEquals("Didn't restore original set when all elements were removed", one,
        rr.next().get());
  }
}
