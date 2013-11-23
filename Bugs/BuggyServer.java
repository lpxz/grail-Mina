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



import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.PropertyConfigurator;
import org.apache.mina.api.IdleStatus;
import org.apache.mina.api.IoFilter;
import org.apache.mina.api.IoService;
import org.apache.mina.api.IoServiceListener;
import org.apache.mina.api.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.filterchain.ReadFilterChainController;
import org.apache.mina.filterchain.WriteFilterChainController;
import org.apache.mina.service.OneThreadSelectorStrategy;
import org.apache.mina.service.SelectorFactory;
import org.apache.mina.transport.tcp.NioSelectorProcessor;
import org.apache.mina.transport.tcp.nio.NioTcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A basic Acceptor test
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * 
 */
public class BuggyServer {
	
	public static int totalPorts = 2000;
	public static long startOfAll =-1;

    static final private Logger LOG = LoggerFactory.getLogger(BuggyServer.class);

    public static void main(String[] args) throws Exception {
    	PropertyConfigurator.configure ("log4j.properties");
    	
        LOG.info("starting echo server");

        OneThreadSelectorStrategy strategy = new OneThreadSelectorStrategy(new SelectorFactory(
                NioSelectorProcessor.class));
        NioTcpServer acceptor = new NioTcpServer(strategy);

        // create the fitler chain for this service
        acceptor.setFilters(new LoggingFilter("LoggingFilter1"), new IoFilter() {

            @Override
            public void sessionOpened(IoSession session) {
                LOG.info("session {} open", session);
            }

            @Override
            public void sessionIdle(IoSession session, IdleStatus status) {
                LOG.info("session {} idle", session);
            }

            @Override
            public void sessionCreated(IoSession session) {
                LOG.info("session {} created", session);
            }

            @Override
            public void sessionClosed(IoSession session) {
                LOG.info("session {} open", session);
            }

            @Override
            public void messageWriting(IoSession session, Object message, WriteFilterChainController controller) {
                // we just push the message in the chain
                controller.callWriteNextFilter(session, message);
            }

            @Override
            public void messageReceived(IoSession session, Object message, ReadFilterChainController controller) {

                if (message instanceof ByteBuffer) {
                    LOG.info("echoing");
                    session.write(message);
                }
            }
        });

        acceptor.addListener(new IoServiceListener() {

            @Override
            public void sessionDestroyed(IoSession session) {
                LOG.info("session destroyed {}", session);

            }

            @Override
            public void sessionCreated(IoSession session) {
                LOG.info("session created {}", session);

                String welcomeStr = "welcome\n";
                ByteBuffer bf = ByteBuffer.allocate(welcomeStr.length());
                bf.put(welcomeStr.getBytes());
                bf.flip();
                session.write(bf);
            }

            @Override
            public void serviceInactivated(IoService service) {
                LOG.info("service deactivated {}", service);
            }

            @Override
            public void serviceActivated(IoService service) {
                LOG.info("service activated {}", service);
            }
        });

         startOfAll = System.currentTimeMillis();
         List<BindingThread> threads = new ArrayList<BindingThread>();
         for(int i= 9999; i<totalPorts+9999; i++)
         {
        	 BindingThread bindingThread = new BindingThread(acceptor, i);
        	 threads.add(bindingThread);
        	 bindingThread.start();
         }
         
         Thread.sleep(500);
         acceptor.unbindAll();// the iterator in it must be in the synchronized block, but it is not in the sync block, so the concurrentModificationException happens.

//               Picked up JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF8
//        		 Exception in thread "main" java.util.ConcurrentModificationException
//        		 	at java.util.HashMap$HashIterator.nextEntry(HashMap.java:894)
//        		 	at java.util.HashMap$KeyIterator.next(HashMap.java:928)
//        		 	at org.apache.mina.transport.tcp.nio.NioTcpServer.unbindAll(NioTcpServer.java:127)
//        		 	at org.apache.mina.examples.echoserver.BuggyServer.main(BuggyServer.java:143)
         
          // join does not work for the binding thread as they wait for the incoming requests.
         
         
         
         
//        try {
//            SocketAddress address = new InetSocketAddress(9999);
//            acceptor.bind(address);
//            LOG.debug("Running the server for 25 sec");
//            Thread.sleep(25000);
//            LOG.debug("Unbinding the TCP port");
//            acceptor.unbind(address);
//        } catch (IOException e) {
//            LOG.error("I/O exception", e);
//        } catch (InterruptedException e) {
//            LOG.error("Interrupted exception", e);
//        }
    }
}