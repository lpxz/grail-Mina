package performance;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.mina.transport.tcp.nio.NioTcpServer;

public class ClientThread extends Thread{

	int NumOfIter = 100;
	NioTcpServer acceptor; 
	int id = -1;
	public ClientThread(NioTcpServer arg, int idarg)
	{
		acceptor = arg;
		id = idarg;
	}
	
	public void run(){
   	 
		for(int i=id*NumOfIter; i< (id+1)* NumOfIter; i++)
		{
			try {
	    		SocketAddress address = new InetSocketAddress(9999+i);
				acceptor.bind(address);
		        } catch (IOException e) {
		            System.err.println("I/O exception");
		    }
		}
//		System.out.println("here");
		this.stop();
		
    	 
		 
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
