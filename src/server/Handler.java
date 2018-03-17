package server;

import java.net.Socket;

public class Handler {

	public Handler(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}
	
	public int start() {
		return 0;
	}
	
	public Socket clientSocket;
}
