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
	 * @param socket	The socket to communicate with
	 * 
	 * @post	The new Handler is instantiated with the given arguments. It now has an input- and outputstream,
	 * 			by which communication with the given socket is made possible.
	 */
	public Handler(Socket clientSocket) {
		setClientSocket(clientSocket);
		try {
			setInFromClient(new BufferedInputStream(getClientSocket().getInputStream()));
			setOutToClient(new DataOutputStream(getClientSocket().getOutputStream()));
		} catch (IOException e) {
			setStatusCode(500);
		}
	}

	/**
	 * Run the server. 
	 * 
	 * @effect	
	 */
	public void run() {
		int nextByte = 0;
		if (getStatusCode() == 200) {
			try {
				nextByte = getInFromClient().read();
			} catch (IOException e) {
				setStatusCode(500);
			}
			if (nextByte == -1) {
				endConnection();
			}
		}
		
		if (! getClientSocket().isClosed() && getStatusCode() == 200) {
			try {
				byte[] request = new byte[1000000];
				getInFromClient().read(request);
				byte[] firstByte = new byte[]{(byte) nextByte};
				setSentence((new String(firstByte)) + (new String(request)));
				setHTTP();
			}
			catch (IOException exc) {
				setStatusCode(500);
			}
			
			if (getStatusCode() == 200) {
				if (!(containsHostHeader()) || !(getHostName().equals(getRequestedHost())) ) {
					System.out.println(getRequestedHost());
					System.out.println(getHostName());
					setStatusCode(400);
				}
				if (getSentence().contains("GET")) {
					executeGET();
				}
				else if (getSentence().contains("HEAD")) {
					executeHEAD();
				}
				else if (getSentence().contains("PUT")) {
					executePUT();
				}
				else if (getSentence().contains("POST")) {
					executePOST();
				}
				else {
					setStatusCode(400);
				}
			}
			
			if (getStatusCode() != 200) {
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
					getOutToClient().writeChars(header);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			if (getHTTP() == 1) {
				run();
			}
		}
	}
	
	/**
	 * A method to set the HTTP.
	 * 
	 * @effect	The method sets the HTTP??
	 */
	private void setHTTP() {
		int begin = getSentence().indexOf("HTTP/");
		this.HTTP = Integer.parseInt(getSentence().substring(begin+7, begin + 8));
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
		
		if (getStatusCode() == 200) {
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
			getOutToClient().writeBytes(header);
			getOutToClient().write(pageToReturn);
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
		
		if (getStatusCode() == 200) {
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
			getOutToClient().writeBytes(header);
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
		if (getStatusCode() == 200) {
			try {
				int begin = getSentence().indexOf("Content-Length:");
				int end = getSentence().indexOf("\r\n", begin);
				lengthBody = Integer.parseInt(getSentence().substring(begin + 16, end));
				String fileName = getRequestedFile();
				String filePath = "Webpage" + fileName;
				begin = getSentence().indexOf("\r\n\r\n");
				String fileToWrite = getSentence().substring(begin+4, begin+4+lengthBody);
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
			getOutToClient().writeBytes(header);
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
		int lengthBody = 0;
		if (getStatusCode() == 200) {
			try {
				int begin = getSentence().indexOf("Content-Length:");
				int end = getSentence().indexOf("\r\n", begin);
				lengthBody = Integer.parseInt(getSentence().substring(begin + 16, end));
				String fileName = getRequestedFile();
				String filePath = "Webpage" + fileName;
				begin = getSentence().indexOf("\r\n\r\n");
				String fileToWrite = getSentence().substring(begin+4, begin+4+lengthBody);
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
			getOutToClient().writeBytes(header);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Creates the header that will be sent to the ChatClient through the server. 
	 * 
	 * @param fileName	The name of the file that is requested. 
	 * @param pageToReturn	The content of the file that is requested. 
	 * 
	 * @effect	The method creates an header that exist of the appropriate status code, content types, content length and date
	 * @effect	If the given file isn't modified after the if modified since date, the status code will be set to 304.
	 * 
	 * @return	The method returns a string, that corresponds to the header that will be sent to the ChatClient.
	 */
	private String createHeader(String fileName, byte[] pageToReturn) {
		String header = "HTTP/1." + getHTTP() + " ";
		
		long dateTime = System.currentTimeMillis();		
		SimpleDateFormat dateTimeFormat = new SimpleDateFormat("E, dd MMM Y HH:mm:ss");

		if (getSentence().contains("If-Modified-Since: ")) {
			if (! getSentence().contains("GET") || ! getSentence().contains("HEAD")) {
				setStatusCode(400);
			}
			int begin = getSentence().indexOf("If-Modified-Since:");
			int end = getSentence().indexOf("GMT", begin);
			String ifModifiedSince = getSentence().substring(begin + 19, end);
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
		
		if (getStatusCode() == 200) {
			header += "200 OK";
		}
		else if (getStatusCode() == 404) {
			header += "404 Not Found";
		}
		else if (getStatusCode() == 400) {
			System.out.println("400");
			header += "400 Bad Request";
		}
		else if (getStatusCode() == 500) {
			header += "500 Server Error";
		}
		else if (getStatusCode() == 304) {
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
		
		if (getStatusCode() == 200) {
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
	 * A method to check whether the header contains a host. 
	 * 
	 * @return	The method returns a boolean, depending on whether there is a host in the header.
	 */
	private boolean containsHostHeader() {
		return (getSentence().contains("\r\nHost:"));
	}
	
	/**
	 * A method to end the connection with the client. 
	 * 
	 * @effect	The method closes the socket with the client.
	 */
	private void endConnection() {
		try {
			getClientSocket().close();
			System.out.println("Ended a connection");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Returns the host name and port number of this server.
	 */
	
	/**
	 * A method to fetch the host from an header. 
	 * 
	 * @return	The method returns a string, that contains the host.
	 */
	private String getRequestedHost() {
		String host = null;
		if (containsHostHeader()) {
			int begin = getSentence().indexOf("Host:");
			int end = getSentence().indexOf("\r\n", begin);
			host = getSentence().substring(begin + 6, end);
		}
		return host;
	}

	/**
	 * A method to fetch the filename from the given HTTP command. 
	 * 
	 * @return	The method returns a string, that contains the filename in the command between the first slash and the "HTTP/".
	 */
	private String getRequestedFile() {
		int begin = getSentence().indexOf("/");
		int end = getSentence().indexOf("HTTP/");
		String fileName = getSentence().substring(begin, end);
		fileName = fileName.trim();
		if (fileName.endsWith("/")) {
			fileName += "index.html";
		}
		return fileName;
	}

	/**
	 * Returns the clientSocket of the server.
	 */
	private Socket getClientSocket() {
		return this.clientSocket;
	}

	/**
	 * Returns the inputstream of the server.
	 */
	private BufferedInputStream getInFromClient() {
		return this.inFromClient;
	}

	/**
	 * Returns the outToClient of the server.
	 */
	private DataOutputStream getOutToClient() {
		return this.outToClient;
	}

	/**
	 * Returns the code of the status.
	 */
	private int getStatusCode() {
		return this.statusCode;
	}

	/**
	 * Returns the hostName of the server.
	 */
	private String getHostName() {
		return hostName;
	}

	/**
	 * Returns the sentence of the server.
	 */
	private String getSentence() {
		return sentence;
	}

	/**
	 * Returns the HTTP of the server.
	 */
	private int getHTTP() {
		return HTTP;
	}

	/**
	 * @param clientSocket	The client socket of the server	
	 * 
	 * Set the client socket to the given clientSocket. 
	 * 
	 * @post	| new.getclientSocket() == clientSocket
	 */
	private void setClientSocket(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}
	
	/**
	 * @param inFromClient	The inputstream from the client.	
	 * 
	 * Set the inputstream to the given inputstream. 
	 * 
	 * @post	| new.getinFromClient() == inFromClient
	 */
	private void setInFromClient(BufferedInputStream inFromClient) {
		this.inFromClient = inFromClient;
	}
	
	/**
	 * @param outToClient	The outputstream of the server	
	 * 
	 * Set the outputstream to the given outputstream. 
	 * 
	 * @post	| new.getoutToClient() == outToClient
	 */
	private void setOutToClient(DataOutputStream outToClient) {
		this.outToClient = outToClient;
	}
	
	/**
	 * @param status	The code of the status of the request.	
	 * 
	 * Set the status code to the given status. 
	 * 
	 * @post	| new.getstatusCode() == status
	 */
	private void setStatusCode(int status) {
		this.statusCode = status;
	}

	/**
	 * @param hostName the hostName to set
	 * 
	 * @post	| new.getHostName() == hostName
	 */
	private void setHostName(String hostName) {
		this.hostName = hostName;
	}

	/**
	 * @param sentence the sentence to set
	 * 
	 * @post	| new.getSentence() == sentence
	 */
	private void setSentence(String sentence) {
		this.sentence = sentence;
	}

	/**
	 * @param HTTP the HTTP to set
	 * 
	 * @post	| new.getHTTP() == HTTP
	 */
	private void setHTTP(int HTTP) {
		this.HTTP = HTTP;
	}

	private Socket clientSocket;
	private BufferedInputStream inFromClient;
	private DataOutputStream outToClient;
	private int statusCode = 200;
	private String hostName = "localhost:9999";
	private String sentence;
	private int HTTP;
}
