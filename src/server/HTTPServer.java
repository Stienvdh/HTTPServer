package server;

import java.io.*;
import java.net.*;

public class HTTPServer {
	
	public HTTPServer() throws IOException {
		this.serverSocket = new ServerSocket(9999);
		while (true) {
			Socket clientSocket = serverSocket.accept();
			if (!(clientSocket == null)) {
				(new Handler(clientSocket)).start();
			}
		}
		
	}
	  
	public ServerSocket serverSocket;
	
}
