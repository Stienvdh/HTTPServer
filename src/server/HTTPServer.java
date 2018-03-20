package server;

import java.io.*;
import java.net.*;

/**
 * A class that implements a multithreaded HTTP server. 
 * 
 * @invar	A HTTP server is always instantiated with a server socket, where it listens for 
 * 			incoming connections.
 */
public class HTTPServer {
	
	/**
	 * A constructor for a new HTTP server.
	 * 
	 * @effect	At its server socket, the server listens for incoming conenctions.
	 * @effect	With each incoming connection, the server starts a new thread to handle
	 * 			the connection with the client socket.
	 */
	public HTTPServer() {
		try {
			setSocket(new ServerSocket(9999));
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (true) {
			Socket clientSocket;
			try {
				clientSocket = getSocket().accept();
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
	
	/**
	 * Set the socket of this HTTPServer to the given socket.
	 * 
	 * @param socket	The new socket
	 * 
	 * @post	| new.getSocket() == socket
	 */
	private void setSocket(ServerSocket socket) {
		this.serverSocket = socket;
	}
	
	/**
	 * Returns the socket of this HTTPServer
	 */
	private ServerSocket getSocket() {
		return this.serverSocket;
	}
	  
	public ServerSocket serverSocket;
}
