package server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Handler implements Runnable {

	public Handler(Socket clientSocket) throws IOException {
		this.clientSocket = clientSocket;
		this.inFromClient = new BufferedInputStream(this.clientSocket.getInputStream());
		this.outToClient = new DataOutputStream(this.clientSocket.getOutputStream());
	}
	
	public void run() {
		int nextByte = 0;
		try {
			nextByte = this.inFromClient.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (nextByte == -1) {
			endConnection();
		}
		
		if (! this.clientSocket.isClosed()) {
			try {
				byte[] request = new byte[1000000];
				this.inFromClient.read(request);
				byte[] firstByte = new byte[]{(byte) nextByte};
				this.sentence = (new String(firstByte)) + (new String(request));
			}
			catch (IOException exc) {
				this.statusCode = 500;
			}
			
			if (!(containsHostHeader()) || !(getHost().equals(getRequestedHost())) ) {
				this.statusCode = 400;
			}
			
			if (this.statusCode == 200) {
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
			
			if (this.statusCode != 200) {
				try {
					String fileName = getRequestedFile();
					String header = createHeader(fileName, new byte[0]);
					outToClient.writeChars(header);
				}
				catch (IOException exc) {
					exc.printStackTrace();
				}
			}
			
			run();
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
	
	private void executeHEAD() {
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
			}
			catch (IOException exc) {
				this.statusCode = 500;
			}
		}
	}

	private void executePUT() {
		if (this.statusCode == 200) {
			try {
				int begin = this.sentence.indexOf("Content-Length:");
				int end = this.sentence.indexOf("\r\n", begin);
				int lengthBody = Integer.parseInt(this.sentence.substring(begin + 16, end));
			
				String fileName = getRequestedFile();
				String filePath = "Webpage" + fileName;
				begin = this.sentence.indexOf("\r\n\r\n");
				String fileToWrite = this.sentence.substring(begin+4, begin+4+lengthBody);
				File file = new File(filePath);
				FileWriter writer = new FileWriter(file);
				writer.write(fileToWrite);
				writer.close();

				String header = createHeader(fileName, new byte[lengthBody]);
				System.out.println("header created");
				outToClient.writeBytes(header);
			}
			catch (IOException exc) {
				this.statusCode = 500;
			}
		}
	}

	private void executePOST() {
		
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
		if (fileExtension.equalsIgnoreCase("html")) {
			header += "text/html";
		}
		else {
			header += "image/" + fileExtension;
		}
		header += "\r\n";
		
		if (this.statusCode == 200) {
			header += "Content-Length: " + pageToReturn.length + "\r\n";
		}
		
		long dateTime = System.currentTimeMillis();				
		SimpleDateFormat dateTimeFormat = new SimpleDateFormat("E, dd MMM Y HH:mm:ss");
		header += "Date: " + dateTimeFormat.format(dateTime) + " GMT\r\n\r\n";
		
		if (this.sentence.contains("If-Modified-Since: ")) {
			if (! this.sentence.contains("GET") || ! this.sentence.contains("GET")) {
				this.statusCode = 400;
			}
			begin = this.sentence.indexOf("If-Modified-Since:");
			int end = this.sentence.indexOf("GMT", begin);
			String ifModifiedSince = this.sentence.substring(begin + 19, end);
			ifModifiedSince = ifModifiedSince.trim();
			try {
				Date date1 = dateTimeFormat.parse(dateTimeFormat.format(dateTime));
				Date date2 = dateTimeFormat.parse(ifModifiedSince);
				if (date1.before(date2)) {
					this.statusCode = 304;
				}
			} catch (ParseException e) {
				this.statusCode = 500;
			}
		}
		
		return header;
	}

	private byte[] readFile(String fileName) throws IOException {
		Path filePath = Paths.get("Webpage" + fileName);
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

	private void endConnection() {
		try {
			this.clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String getHost() {
		return this.hostName;
	}
	
	public Socket clientSocket;
	public BufferedInputStream inFromClient;
	public DataOutputStream outToClient;
	public int statusCode = 200;
	public String hostName = "localhost:9999";
	public String sentence;
	public int HTTP;

}
