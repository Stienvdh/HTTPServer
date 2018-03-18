package server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;

public class Handler {

	public Handler(Socket clientSocket) throws IOException {
		this.clientSocket = clientSocket;
		this.inFromClient = new BufferedInputStream(this.clientSocket.getInputStream());
		this.outToClient = new DataOutputStream(this.clientSocket.getOutputStream());
	}
	
	public void start() {
		try {
			byte[] request = new byte[1000000];
			this.inFromClient.read(request);
			this.sentence = new String(request);
		}
		catch (IOException exc) {
			this.statusCode = 500;
		}
		
		if (!(this.containsHostHeader()) || !(getHost() == getRequestedHost()) ) {
			this.statusCode = 400;
		}
		
		if (this.sentence.contains("GET")) {
			executeGET();
		}
		else if (this.sentence.contains("HEAD")) {
			executeHEAD();
		}
		else if (this.sentence.contains("PUT")) {
			executePUT();
		}
		else if (this.sentence.contains("POST")) {
			executePOST();
		}
		else {
			this.statusCode = 400;
		}
	}
	
	private void executeGET() {
		byte[] pageToReturn = null;
		String fileName = null;
		
		if (this.statusCode == 200) {
			fileName = getRequestedFile();
			
			try {
				pageToReturn = readFile(fileName);
			}
			catch (IOException exc) {
				this.statusCode = 404;
			}
		}
		
		if (this.statusCode == 200) {
			try {
				String header = createHeader(fileName, pageToReturn);
				outToClient.writeBytes(header);
				outToClient.write(pageToReturn);
			}
			catch (IOException exc) {
				this.statusCode = 500;
			}
		}
		
	}

	private String createHeader(String fileName, byte[] pageToReturn) {
		String header = "HTTP/1." + this.HTTP + " ";
		
		if (this.statusCode == 200) {
			header += "200 OK";
		}
		else if (this.statusCode == 404) {
			header += "404 Not Found";
		}
		else if (this.statusCode == 400) {
			header += "400 Bad Request";
		}
		else if (this.statusCode == 500) {
			header += "500 Server Error";
		}
		else if (this.statusCode == 304) {
			header += "304 Not Modified";
		}
		header += "\r\n";
		
		header += "Content-type: ";
		int begin = fileName.indexOf(".") + 1;
		String fileExtension = fileName.substring(begin);
		if (fileExtension == "html") {
			header += "text/html";
		}
		else {
			header += "image/" + fileExtension;
		}
		header += "\r\n";
		
		header += "Content-length: " + pageToReturn.length + "\r\n";
		
		long dateTime = System.currentTimeMillis();				
		SimpleDateFormat dateTimeFormat = new SimpleDateFormat("E, dd MMM Y HH:mm:ss");
		header += "Date:  " + dateTimeFormat.format(dateTime) + " GMT\r\n\r\n";
		
		return header;
	}

	private byte[] readFile(String fileName) throws IOException {
		Path filePath = Paths.get(fileName);
		return Files.readAllBytes(filePath);
	}

	private String getRequestedFile() {
		int begin = this.sentence.indexOf("/");
		int end = this.sentence.indexOf("HTTP/");
		String fileName = this.sentence.substring(begin, end);
		fileName = fileName.trim();
		if (fileName.endsWith("/")) {
			fileName += "index.html";
		}
		return fileName;
	}

	private void executeHEAD() {
		// TODO Auto-generated method stub
		
	}

	private void executePUT() {
		// TODO Auto-generated method stub
		
	}

	private void executePOST() {
		// TODO Auto-generated method stub
		
	}

	private String getRequestedHost() {
		String host = null;
		if (containsHostHeader()) {
			int begin = this.sentence.indexOf("Host:");
			int end = this.sentence.indexOf("\r\n", begin);
			host = this.sentence.substring(begin + 6, end);
		}
		return host;
	}

	private boolean containsHostHeader() {
		return (this.sentence.contains("\r\nHost:"));
	}

	private String getHost() {
		return this.hostName;
	}

	public Socket clientSocket;
	public BufferedInputStream inFromClient;
	public DataOutputStream outToClient;
	public int statusCode = 200;
	public String hostName = "localhost";
	public String sentence;
	public int HTTP;

}
