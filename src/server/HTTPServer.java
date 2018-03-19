package server;

import java.io.*;
import java.net.*;

public class HTTPServer {
	
	public HTTPServer() throws IOException {
		this.serverSocket = new ServerSocket(9999);
		while (true) {
			Socket clientSocket = serverSocket.accept();
			if (!(clientSocket == null)) {
				System.out.println("Made a connection");
				Handler handler = new Handler(clientSocket);
				Thread thread = new Thread(handler);
				thread.start();
			}
		}
	}
	  
	public ServerSocket serverSocket;
	
}
