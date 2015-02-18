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

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Manages the location of the servers.
 * <p>
 * By specifying a time greater than {@value #NEVER}, in ms, you can periodically refresh the
 * known server list using the specified {@link ServerLocationFinder}.
 * </p>
 */
public class ServerLocationManager extends RoundRobin<ServerName> {

  public static final long NEVER = 0;
  public static final long ONE_MINUTE = 60000;
  public static final long FIVE_MINUTES = 5*ONE_MINUTE;

  private final ServerLocationFinder servers;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new
                                                                                           ThreadFactory() {
    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setName("ServerLocationManager-RefreshThread");
      t.setDaemon(true);

      return t;
    }
  });

  public ServerLocationManager(ServerLocationFinder servers) {
    this(servers, FIVE_MINUTES);
  }

  /**
   * @param servers location of the servers
   * @param refreshPeriod frequency with which we should re-check for the location of the servers
   * and update the internal list
   */
  public ServerLocationManager(ServerLocationFinder servers, long refreshPeriod){
    this.servers = servers;
    setup(refreshPeriod);
  }

  public ServerLocationManager(ServerLocationFinder servers, long refreshPeriod, int traversals,
      int retries, int numReloads){
    super(traversals, retries, numReloads);
    this.servers = servers;
    setup(refreshPeriod);
  }

  private void setup(long refreshPeriod) {
    this.replaceElements(servers.getServerLocations());

    if(refreshPeriod > NEVER){
      startRefresher(refreshPeriod);
    }
  }

  private void startRefresher(long refreshPeriod) {
    executor.scheduleAtFixedRate(new Refresher(), refreshPeriod, refreshPeriod,
        TimeUnit.MILLISECONDS);
  }

  private class Refresher implements Runnable{

    @Override
    public void run() {
      Collection<ServerName> current = ServerLocationManager.this.getOriginalElements();
      Collection<ServerName> updated =  servers.getServerLocations();

      //only update the servers if we find a different list
      if(!current.equals(updated)){
        ServerLocationManager.this.replaceElements(updated);
      }
    }
  }
}