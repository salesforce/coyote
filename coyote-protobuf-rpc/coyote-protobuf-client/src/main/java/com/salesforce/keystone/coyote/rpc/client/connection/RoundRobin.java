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

package com.salesforce.keystone.coyote.rpc.client.connection;

import com.google.common.collect.Iterators;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Select the elements in a round-robin fashion.
 * <p>
 * If a given element is currently not valid - the element is temporarily unusable - you can mark
 * it as such via {@link com.salesforce.keystone.vault.client.connection.RoundRobin.Element#markInvalid()} The
 * element will then not be returned for a configurable number of traversals. Similarly,
 * if the element is valid, is should be marked as such with{@link com.salesforce.keystone.vault
 * .client.connection.RoundRobin.Element#markValid()} to ensure it returns to a 'usable' state
 * </p>
 * <p>
 * Similarly, we do not assume an element is valid unless it is marked as valid. However,
 * once it is marked as 'valid' we remove any history of it being invalid.
 * </p>
 * <p>
 * If an element is retried up to the configured number of retries and each time is not found to
 * be valid, then we will remove it from future consideration. The same element can only be
 * returned to use if we create a new element with the same properties via {@link
 * #replaceElements(List)}
 * </p>
 * <p>
 * If all the elements are removed from the set (retired due to retries) then we will restore the
 * entire original set and start again.
 * </p>
 * <p>
 * This class is thread-safe with regards to updating the elements (via {@linkplain
 * #replaceElements(List)} and access to them via the iterator. Concurrent updates the
 * the elements will merely cause the round-robin logic to reset to the head of the new list
 * </p>
 */
public class RoundRobin<T> {

  private static final Log LOG = LogFactory.getLog(RoundRobin.class);

  private static final int DEFAULT_TRAVERSALS = 1;
  private static final int DEFAULT_RETRIES = 3;
  private static final int DEFAULT_RELOADS = 0;
  private final int reloads;
  private final int traversals;
  private final int retries;
  private Iterator<Element> elements;
  private List<T> removed = new ArrayList<>();
  private final Object currentValidListLock = new Object();
  private int currentReloads = 0;
  private Collection<T> originalElements;

  /**
   * Build a round-robin manager for a set of objects. Each {@link Element} keeps track of
   * whether or not it should be considered valid.
   *
   * @param traversals number of times we have to traverse past an invalid element before it is
   *                   returned to the client (and allowed to be retried). For instance,
   *                   on the first pass the element is returned, since it is the first time it
   *                   is seen. Then it is marked as invalid. The next time we come to the
   *                   element we check to see if it has been traversed the specified number of
   *                   times before it is returned. So if traversals == 0,
   *                   we would return the element. However, if traversals == 1,
   *                   we would wait until we try all the remaining elements before returning to
   *                   this one.
   * @param retries    number of retries before an element is retired from the set.
   * @param numReloads number of times the list should be reloaded,
   *                   if it is emptied by exhausting the retries on the stored elements.
   */
  public RoundRobin(int traversals, int retries, int numReloads) {
    this.traversals = traversals;
    this.retries = retries;
    this.reloads = numReloads;
  }

  public RoundRobin() {
    this(DEFAULT_TRAVERSALS, DEFAULT_RETRIES, DEFAULT_RELOADS);
  }

  public Collection<T> getOriginalElements(){
    synchronized (currentValidListLock) {
      return this.originalElements;
    }
  }

  public void replaceElements(Collection<T> elems) {
    synchronized (currentValidListLock) {
      // copy the elements into a new list
      this.originalElements = new ArrayList<>(elems);

      // tranform into a new list of Elements
      List<Element> transformed = new ArrayList<>(elems.size());
      for (T elem : elems) {
        transformed.add(new Element(elem));
      }

      // convert to a circular iterator for ease of access
      this.elements = Iterators.cycle(transformed);
    }
  }

  /**
   * Restores the removed elements to the list as new elements, allowing us to retry them all
   * again
   * <p>
   * This should only be called when there are no more elements left to retry
   * </p>
   */
  private void restore() {
    assert !this.elements.hasNext() : "Attempting to restore, but there are still some valid " +
        "elements left to try";
    if (++currentReloads > reloads) {
      LOG.info("Attempting to restore the original elements (count " + currentReloads + ")," +
          "but not allowed to reload more than" +
          +reloads + " times");
      return;
    }
    replaceElements(removed);
  }

  /**
   * Try to do a restore of the elements, if we don't have any more elements.
   * <p>
   * must be called under {@link #currentValidListLock }
   * </p>
   *
   * @throws NoSuchElementException if, after attempting a restore,
   *                                we still don't have any elements available
   */
  private void tryRestore() throws NoSuchElementException {
    // we have some elements left in the list, don't need to restore
    if (this.elements.hasNext()) {
      return;
    }

    restore();

    // if there still aren't elements, then we fail
    if (!this.elements.hasNext()) {
      throw new NoSuchElementException("No known elements");
    }
  }

  /**
   * Get the next valid element.
   * @return
   * @throws NoSuchElementException if there are no more possible elements to retry
   */
  public Element next() throws NoSuchElementException {
    Element elem;
    synchronized (currentValidListLock) {
      // try to do a restore immediately
      tryRestore();

      // we can always call next because we just did a restore and no one could have modified us
      // up to this point
      elem = elements.next();

      // remove the element if we have retried it too many times
      if (elem.retired()) {
        elements.remove();
        removed.add(elem.get());
        return next();
      }
    }

    // Note that we can look at the element outside the synchronized block because all the
    // methods on that element are synchronized too - the state of the element is essentially
    // frozen (though it could be modified 'at the same time' at each check point,
    // it doesn't matter because all we care about is if it is valid at each point). The only
    // things we can't do are manipulate the underlying list,
    // which is why the retired/removal check is still in the currentValidListLock

    // update the seen count on the element to allow it to rejoin the valid list after the
    // configured number of traversals
    elem.seen();

    if (elem.isValid()) {
      return elem;
    }

    //the current one isn't valid, but isn't retired (needs to be traversed a few more
    // times) so we can just return the next one in the list
    return next();
  }

  @Override
  public String toString() {
    return "RoundRobin of " + elements + "\n\t" +
        currentReloads + "/" + reloads + " reloads\n\t" +
        traversals + " traversals, \n\t"+
        retries + " retries";
  }

  /**
   * Keep track of an element and if it should be included or skipped
   *
   * @param <T>
   */
  public class Element{
    private final T delegate;
    private boolean invalid = false;
    private int invalidCount = 0;
    private int traversalCount;

    @Override
    public String toString(){
      return "Element:\n\t" +
          delegate +"\n\t"+
          "valid: "+invalid+"\n\t"+
          "invalid count: "+invalidCount+"\n\t"+
          "traversals: "+traversalCount+"\n";
    }


    public Element(T delegate) {
      this.delegate = delegate;
    }

    public T get() {
      return delegate;
    }

    private synchronized void seen() {
      //only update if we aren't currently valid
      if (isValid()) {
        return;
      }
      this.traversalCount++;
    }

    public synchronized void markInvalid() {
      this.invalid = true;
      this.invalidCount++;
    }

    public synchronized void markValid() {
      this.invalid = false;
      this.invalidCount = 0;
      this.traversalCount = 0;
    }

    private boolean retired() {
      return invalidCount > retries;
    }

    private synchronized boolean isValid() {
      // its not marked invalid, so we can use it
      if (!invalid) {
        return true;
      }

      // it is marked invalid, but we need to check the counts and seen times to determine if we
      // should allow it to be returned

      // its been retried and failed more than we should allow
      if (retired()) {
        return false;
      }

      // we have looped enough times to retry it, so we can reset the count
      if (traversalCount > traversals) {
        traversalCount = 0;
        return true;
      }

      // its not been retried too many times, but we haven't traversed the rest of the list
      // enough time to give this one a shot again
      return false;
    }
  }
}