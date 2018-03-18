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
				(new Handler(clientSocket)).run();
			}
			System.out.println("end");
		}
	}
	  
	public ServerSocket serverSocket;
	
}
