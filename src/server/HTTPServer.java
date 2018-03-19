package server;

import java.io.*;
import java.net.*;

public class HTTPServer {
	
	public HTTPServer() {
		try {
			this.serverSocket = new ServerSocket(9999);
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (true) {
			Socket clientSocket;
			try {
				clientSocket = serverSocket.accept();
				if (!(clientSocket == null)) {
					System.out.println("Made a connection");
					Handler handler = new Handler(clientSocket);
					Thread thread = new Thread(handler);
					thread.start();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	  
	public ServerSocket serverSocket;
	
}
