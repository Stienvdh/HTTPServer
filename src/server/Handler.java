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

	public Handler(Socket clientSocket) {
		this.clientSocket = clientSocket;
		try {
			this.inFromClient = new BufferedInputStream(this.clientSocket.getInputStream());
			this.outToClient = new DataOutputStream(this.clientSocket.getOutputStream());
		} catch (IOException e) {
			setStatusCode(500);
		}
	}
	
	private void setStatusCode(int status) {
		if (this.statusCode == 200) {
			this.statusCode = status;
		}
	}

	public void run() {
		int nextByte = 0;
		if (this.statusCode == 200) {
			try {
				nextByte = this.inFromClient.read();
			} catch (IOException e) {
				setStatusCode(500);
			}
			if (nextByte == -1) {
				endConnection();
			}
		}
		
		if (! this.clientSocket.isClosed() && this.statusCode == 200) {
			try {
				byte[] request = new byte[1000000];
				this.inFromClient.read(request);
				byte[] firstByte = new byte[]{(byte) nextByte};
				this.sentence = (new String(firstByte)) + (new String(request));
				setHTTP();
			}
			catch (IOException exc) {
				setStatusCode(500);
			}
			
			if (this.statusCode == 200) {
				if (!(containsHostHeader()) || !(getHost().equals(getRequestedHost())) ) {
					System.out.println(getRequestedHost());
					System.out.println(getHost());
					setStatusCode(400);
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
					setStatusCode(400);
				}
			}
			
			if (this.statusCode != 200) {
				byte[] pageToReturn = new byte[0];
				String fileName = getRequestedFile();
				try {
					pageToReturn = readFile(fileName);
				}
				catch (IOException exc) {
					setStatusCode(404);
				}
				String header = createHeader(fileName, pageToReturn);
				try {
					this.outToClient.writeChars(header);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			if (this.HTTP == 1) {
				run();
			}
		}
	}
	
	private void setHTTP() {
		int begin = this.sentence.indexOf("HTTP/");
		this.HTTP = Integer.parseInt(this.sentence.substring(begin+7, begin + 8));
	}

	private void executeGET() {
		byte[] pageToReturn = new byte[0];
		String fileName = null;
		
		if (this.statusCode == 200) {
			fileName = getRequestedFile();
			try {
				pageToReturn = readFile(fileName);
			}
			catch (IOException exc) {
				setStatusCode(400);
			}
		}
		
		String header = createHeader(getRequestedFile(), pageToReturn);
		try {
			outToClient.writeBytes(header);
			outToClient.write(pageToReturn);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void executeHEAD() {
		byte[] pageToReturn = new byte[0];
		String fileName = null;
		
		if (this.statusCode == 200) {
			fileName = getRequestedFile();
			try {
				pageToReturn = readFile(fileName);
			}
			catch (IOException exc) {
				setStatusCode(404);
			}
		}
		
		String header = createHeader(getRequestedFile(), pageToReturn);
		try {
			outToClient.writeBytes(header);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void executePUT() {
		int lengthBody = 0;
		if (this.statusCode == 200) {
			try {
				int begin = this.sentence.indexOf("Content-Length:");
				int end = this.sentence.indexOf("\r\n", begin);
				lengthBody = Integer.parseInt(this.sentence.substring(begin + 16, end));
				String fileName = getRequestedFile();
				String filePath = "Webpage" + fileName;
				begin = this.sentence.indexOf("\r\n\r\n");
				String fileToWrite = this.sentence.substring(begin+4, begin+4+lengthBody);
				File file = new File(filePath);
				FileWriter writer = new FileWriter(file);
				writer.write(fileToWrite);
				writer.close();
			}
			catch (IOException exc) {
				setStatusCode(500);
			}
			
		}
		String header = createHeader(getRequestedFile(), new byte[lengthBody]);
		try {
			outToClient.writeBytes(header);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void executePOST() {
		int lengthBody = 0;
		if (this.statusCode == 200) {
			try {
				int begin = this.sentence.indexOf("Content-Length:");
				int end = this.sentence.indexOf("\r\n", begin);
				lengthBody = Integer.parseInt(this.sentence.substring(begin + 16, end));
				String fileName = getRequestedFile();
				String filePath = "Webpage" + fileName;
				begin = this.sentence.indexOf("\r\n\r\n");
				String fileToWrite = this.sentence.substring(begin+4, begin+4+lengthBody);
				File file = new File(filePath);
				FileWriter writer = new FileWriter(file, true);
				writer.write(fileToWrite);
				writer.close();
			}
			catch (IOException exc) {
				setStatusCode(500);
			}
			
		}
		String header = createHeader(getRequestedFile(), new byte[lengthBody]);
		try {
			outToClient.writeBytes(header);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String createHeader(String fileName, byte[] pageToReturn) {
		String header = "HTTP/1." + this.HTTP + " ";
		
		long dateTime = System.currentTimeMillis();		
		SimpleDateFormat dateTimeFormat = new SimpleDateFormat("E, dd MMM Y HH:mm:ss");
		if (this.sentence.contains("If-Modified-Since: ")) {
			if (! this.sentence.contains("GET") || ! this.sentence.contains("GET")) {
				setStatusCode(400);
			}
			int begin = this.sentence.indexOf("If-Modified-Since:");
			int end = this.sentence.indexOf("GMT", begin);
			String ifModifiedSince = this.sentence.substring(begin + 19, end);
			ifModifiedSince = ifModifiedSince.trim();
			try {
				Date date1 = dateTimeFormat.parse(dateTimeFormat.format(dateTime));
				Date date2 = dateTimeFormat.parse(ifModifiedSince);
				if (date1.before(date2)) {
					setStatusCode(304);
				}
			} catch (ParseException e) {
				setStatusCode(500);
			}
		}
		
		if (this.statusCode == 200) {
			header += "200 OK";
		}
		else if (this.statusCode == 404) {
			header += "404 Not Found";
		}
		else if (this.statusCode == 400) {
			System.out.println("400");
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
		if (fileExtension.equalsIgnoreCase("html") || fileExtension.equalsIgnoreCase("txt")) {
			header += "text/html";
		}
		else {
			header += "image/" + fileExtension;
		}
		header += "\r\n";
		
		if (this.statusCode == 200) {
			header += "Content-Length: " + pageToReturn.length + "\r\n";
		}
				
		header += "Date: " + dateTimeFormat.format(dateTime) + " GMT\r\n\r\n";
		
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
			System.out.println("Ended a connection");
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
