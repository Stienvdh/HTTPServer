package server;

import java.io.*;
import java.net.*;

public class HTTPServer {
	
	public HTTPServer() throws IOException {
		this.serverSocket = new ServerSocket(9999);
		while (true) {
			System.out.println("ok");
			Socket clientSocket = serverSocket.accept();
			if (!(clientSocket == null)) {
				System.out.println("found client");
				Handler handler = new Handler(clientSocket);
				Thread thread = new Thread(handler);
				thread.start();
			}
			System.out.println("end");
		}
	}
	  
	public ServerSocket serverSocket;
	
}
