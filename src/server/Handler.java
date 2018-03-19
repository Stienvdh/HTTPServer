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

	/**
	 * Create a new Handler with a given socket.
	 *
	 * @param socket			The socket to communicate with
	 * 
	 * @post	The new Handler is instantiated with the given arguments. It now has an input- and outputstream,
	 * 			by which communication with the given socket is made possible.
	 */
	
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
	
	/**
	 * Handle a GET request. 
	 * 
	 * @effect	The method reads the requested file.
	 * @effect 	The method creates an appropriate header for the given status code and file.
	 * @effect	The method writes the header and the content of the file to the output.
	 */

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
	
	/**
	 * Handle an HEAD request. 
	 * 
	 * @effect	The method reads the requested file.
	 * @effect 	The method creates an appropriate header for the given status code and file.
	 * @effect	The method writes the header to the output.
	 */
	
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
	
	/**
	 * Handle a PUT request. 
	 * 
	 * @effect	The method generates a directory for the given file to store.
	 * @effect 	The method creates a text file with the given content in the created directory.
	 * @effect	The method writes the appropriate header and writes it to the output.
	 */
	
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

	/**
	 * Handle a PUT request. 
	 * 
	 * @effect	The method generates a directory for the given file to store.
	 * @effect 	The method appends the given content to the already stored content in the text file.
	 * @effect	The method creates a new file or rewrite an existing file with the merged content.
	 * @effect	The method writes the appropriate header and writes it to the output.
	 */
	
	private void executePOST() {
		
	}
	
	/**
	 * Creates the header that will be sent to the ChatClient through the server. 
	 * 
	 * @param fileName	The name of the file that is requested. 
	 * @param pageToReturn	The content of the file that is requested. 
	 * 
	 * @effect	The method creates an header that exist of the appropriate statuscode, content types, content length and date
	 * @effect	If the given file isn't modified after the if modified since date, the status code will be set to 304.
	 * 
	 * @return	The method returns a string, that corresponds to the header that will be sent to the ChatClient.
	 */
	
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
				
		header += "Date: " + dateTimeFormat.format(dateTime) + " GMT\r\n\r\n";
		
		return header;
	}

	/**
	 * Creates the header that will be sent to the ChatClient through the server. 
	 * 
	 * @param fileName	The name of the file that is requested. 
	 * 
	 * @return	The method returns a byte array, that contains all the bytes of the given filename.
	 */
	
	private byte[] readFile(String fileName) throws IOException {
		Path filePath = Paths.get("Webpage" + fileName);
		return Files.readAllBytes(filePath);
	}

	/**
	 * A method to fetch the filename from the given HTTP command. 
	 * 
	 * @return	The method returns a string, that contains the filename in the command between the first slash and the "HTTP/".
	 */
	
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

	/**
	 * A method to fetch the host from an header. 
	 * 
	 * @return	The method returns a string, that contains the filename in the command between the first slash and the "HTTP/".
	 */
	
	private String getRequestedHost() {
		String host = null;
		if (containsHostHeader()) {
			int begin = this.sentence.indexOf("Host:");
			int end = this.sentence.indexOf("\r\n", begin);
			host = this.sentence.substring(begin + 6, end);
		}
		return host;
	}

	/**
	 * A method to check whether the header contains a host. 
	 * 
	 * @return	The method returns a boolean, depending on whether there is a host in the header.
	 */
	
	private boolean containsHostHeader() {
		return (this.sentence.contains("\r\nHost:"));
	}
	
	/**
	 * A method to end the connection with the client. 
	 * 
	 * @effect	The method closes the socket with the client.
	 */
	
	
	private void endConnection() {
		try {
			this.clientSocket.close();
			System.out.println("Ended a connection");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Returns the host name of this ChatClient.
	 */
	
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
