

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.mina.transport.tcp.nio.NioTcpServer;

public class BindingThread extends Thread{

	NioTcpServer acceptor; 
	int port = -1;
	public BindingThread(NioTcpServer arg, int portarg)
	{
		acceptor = arg;
		port = portarg;
	}
	public static int total  = 0;
	
	public void run(){
		  try {
	            SocketAddress address = new InetSocketAddress(port);
	            acceptor.bind(address);
//	            acceptor.unbind(address);
	           synchronized (BindingThread.class) {
	        	   total++;
	        	   if(total==BuggyServer.totalPorts)
	        	   {
	        		   System.out.println("It takes " + (System.currentTimeMillis() -BuggyServer.startOfAll) + " msec to bind to all ports");
	        	   }
			   } 
	        } catch (IOException e) {
	            System.err.println("I/O exception");
	        }
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
