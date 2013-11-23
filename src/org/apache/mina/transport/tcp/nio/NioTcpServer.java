/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.transport.tcp.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.mina.service.SelectorStrategy;
import org.apache.mina.transport.tcp.AbstractTcpServer;
import org.apache.mina.transport.tcp.DefaultSocketSessionConfig;
import org.apache.mina.transport.tcp.NioSelectorProcessor;
import org.apache.mina.transport.tcp.SocketSessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import performance.BuggyServer;

/**
 * This class implements a TCP NIO based server.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class NioTcpServer extends AbstractTcpServer {
	
	
    static final Logger LOG = LoggerFactory.getLogger(NioTcpServer.class);

    // list of bound addresses
    private Set<SocketAddress> addresses = Collections.synchronizedSet(new HashSet<SocketAddress>());

    
    
    // the strategy for dispatching servers and client to selector threads.
    private SelectorStrategy strategy;

    private SocketSessionConfig config;

    private boolean reuseAddress = false;
    
    public  int id = -1; 
    

    public NioTcpServer(SelectorStrategy strategy) {
        super();
        this.strategy = strategy;
        this.config = new DefaultSocketSessionConfig();
    }

    
    public NioTcpServer(SelectorStrategy strategy, int idarg) {
        super();
        this.strategy = strategy;
        this.config = new DefaultSocketSessionConfig();
        this.id = idarg; //lpxz
    }

    @Override
    public SocketSessionConfig getSessionConfig() {
        return config;
    }

    public void setSessionConfig(SocketSessionConfig config) {
        this.config = config;
    }

    @Override
    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    @Override
    public boolean isReuseAddress() {
        return reuseAddress;
    }

    @Override
    public void bind(SocketAddress... localAddress) throws IOException {
        if (localAddress == null) {
            // We should at least have one address to bind on
            throw new IllegalStateException("LocalAdress cannot be null");
        }


//        long start = System.currentTimeMillis();
        for (SocketAddress address : localAddress) {
            // check if the address is already bound
            synchronized (NioTcpServer.class)  // lpxz: this.
            {
                if (addresses.contains(address)) {
                    throw new IOException("address " + address + " already bound");
                }

                LOG.debug("binding address {}", address);

                addresses.add(address);
                NioSelectorProcessor processor = (NioSelectorProcessor) strategy.getSelectorForBindNewAddress();
                processor.bindAndAcceptAddress(this, address);// create a thread only at this point.
                if (addresses.size() == 1) {
                    // it's the first address bound, let's fire the event
                    fireServiceActivated();
                }
            }
        }
//        long end = System.currentTimeMillis();
        
//        BuggyServer.timers[id] =BuggyServer.timers[id] + (end-start); // isolation via the index.
        
    }

    @Override
    public Set<SocketAddress> getLocalAddresses() {
    	
    	
        return addresses;
    }

    @Override
    public void unbind(SocketAddress... localAddresses) throws IOException {
        for (SocketAddress socketAddress : localAddresses) {
            LOG.debug("unbinding {}", socketAddress);
            synchronized (this) {
                strategy.unbind(socketAddress);
                addresses.remove(socketAddress);
                if (addresses.isEmpty()) {
                    fireServiceInactivated();
                }
            }
        }
    }

//    @Override
//    public void unbindAll() throws IOException {// original code, it throws concurrentModException even in sequential settings! because it modifies the addresses during the tieration.
//        for (SocketAddress socketAddress : addresses) {
//            unbind(socketAddress);
//        }
//    }

    @Override
    public void unbindAll() throws IOException {// updated by lpxz, see the original code above.
        for (SocketAddress socketAddress : addresses) {// sitll buggy here, AV! the one we want to fix
                LOG.debug("unbinding {}", socketAddress);// inline
                synchronized (this) {
                    strategy.unbind(socketAddress);
//                    addresses.remove(socketAddress);
                    if (addresses.isEmpty()) {
                        fireServiceInactivated();
                    }
                }
        	
        	
        }
        synchronized (this) {
        	addresses.clear();
		}
        
    }
}