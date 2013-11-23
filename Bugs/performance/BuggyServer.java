package performance;
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
import java.util.Random;

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
	

    static final private Logger LOG = LoggerFactory.getLogger(BuggyServer.class);
    
    public static NioTcpServer[] servers = null; 
//    public static long timers[] = null;
    public static Random random = new Random();

    public static void main(String[] args) throws Exception {
    	PropertyConfigurator.configure ("log4j.properties");
    	


    	int serverNo = 20; // sufficient for evaluation. 
    	servers = new NioTcpServer[serverNo];
//    	timers = new long[serverNo];
    	for(int i=0;i< serverNo; i++)
    	{
    		OneThreadSelectorStrategy strategy = new OneThreadSelectorStrategy(new SelectorFactory(
                    NioSelectorProcessor.class));
            NioTcpServer acceptor = new NioTcpServer(strategy,i);
            setFiltersListeners(acceptor);
            servers[i] =  acceptor;
//            timers[i] =0;
    	}
    	

    	
    	  int uniquePort = 9999;
    	  int clientThreadNo = Integer.parseInt(args[0]);
    	  ClientThread[] clientthreads = new  ClientThread[clientThreadNo];
    	  for(int i=0;i<clientThreadNo;i++)
    	  {
    		  clientthreads[i] = new ClientThread(servers[i], i);// each work for a different thread.
    	  }
    	  
    	  long start = System.currentTimeMillis();
    	  for(int i=0;i<clientThreadNo;i++)
    	  {
    		  clientthreads[i].start();
    	  }
    	  
    	  for(int i=0;i<clientThreadNo;i++)
    	  {
    		  clientthreads[i].join();
    	  }
         long end = System.currentTimeMillis();
    	  System.out.println("takes: " + (end-start));
    	  
       
         
        
         System.exit(0);


    }

	private static void setFiltersListeners(NioTcpServer acceptor) {

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

	}
}