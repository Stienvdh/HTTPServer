package server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A class to implement a Handler, which handles a client request to a local HTTP server.
 * 
 * @invar	A Handler is always instantiated with a client socket, an input- and outputstream, 
 * 			a status code, a host name and a request sentence. 
 * @invar	A Handler communicates with the client according to the HTTP/1.1 protocol.
 */
public class Handler implements Runnable {

	/**
	 * Create a new Handler with a given socket.
	 *
	 * @param socket	The socket to communicate with
	 * 
	 * @post	The new Handler is instantiated with the given arguments. It now has an input- and outputstream,
	 * 			by which communication with the given socket is made possible.
	 * @post	If any problem accurs when instantiating the input- or outputstream, the status code
	 * 			of this Handler is set to 500.
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
	 * Handle the current request of this Handler.
	 * 
	 * @effect	The method reads the first byte of its inputstream. If -1 is returned, this indicates that 
	 * 			the stream has been closed by the client and the connection to the client is closed. 
	 * @effect	Otherwise, the request from the client is read and execution is rerouted to
	 * 			the appropriate method to handle the request.
	 * @effect	After finishing execution, the method calls itself, in order to maintain a persistent
	 * 			connection.
	 * 
	 * @post	If a problem concerning input- and outputstream occurs, and this is the first abnormality
	 * 			to occur, the statuscode is set to 500.
	 * @post	If the request does not contain a host header, or the host header does not refer to this HTTPServer, 
	 * 			and this is the first abnormality to occur, the statuscode is set to 400.
	 * @post	If the requested page is not found, and this is the first abnormality to occur, the status 
	 * 			code is set to 404.
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
			}
			catch (IOException exc) {
				setStatusCode(500);
			}
			
			if (getStatusCode() == 200) {
				if (!(containsHostHeader()) || !(getHostName().equals(getRequestedHost())) ) {
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
			run();
		}
	}
	
	/**
	 * Handle a GET request. 
	 * 
	 * @effect	The method determines the requested file from the client request.
	 * @effect 	The method creates an appropriate header for the given status code and file.
	 * 			| createHeader(fileName, pageToReutrn)
	 * @effect	The method writes the header and the content of the file to the outputstream and thus
	 * 			sends it to the client.
	 * 
	 * @post	If the requested page is not found, and this is the first abnormality to occur, the status 
	 * 			code is set to 404.
	 */
	public void executeGET() {
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
	 * 			| createHeader(fileName, pageToReturn)
	 * @effect	The method writes the header to the outputstream and thus sends it to the client.
	 * 
	 * @post	If the requested page is not found, and this is the first abnormality to occur, the status 
	 * 			code is set to 404.
	 */
	public void executeHEAD() {
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
	 * @effect 	The method creates a text file with the given content in the same local directory 
	 * 			where the /index.html file of the server is located, 
	 * 			or overwrites it with the given content if it already exists. 
	 * @effect	The method writes the appropriate header and writes it to its outputstream.
	 * 
	 * @post	If a problem concerning input- and outputstream occurs, and this is the first abnormality
	 * 			to occur, the statuscode is set to 500.
	 */
	public void executePUT() {
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
		String header = createHeader(getRequestedFile(), new byte[0]);
		try {
			getOutToClient().writeBytes(header);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Handle a POST request. 
	 * 
	 * @effect 	The method creates a text file with the given content in the same local directory 
	 * 			where the /index.html file of the server is located, 
	 * 			or append it with the given content if it already exists. 
	 * @effect	The method writes the appropriate header and writes it to its outputstream.
	 * 
	 * @post	If a problem concerning input- and outputstream occurs, and this is the first abnormality
	 * 			to occur, the statuscode is set to 500.
	 */
	public void executePOST() {
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
		String header = createHeader(getRequestedFile(), new byte[0]);
		try {
			getOutToClient().writeBytes(header);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Creates the header that will be sent to the ChatClient through the server. 
	 * 
	 * @param fileName		The name of the file that is requested. 
	 * @param pageToReturn	The content of the file that is requested. 
	 * 
	 * @effect	The method creates a header that exist of the appropriate status code, content type, content length and date.
	 * 
	 * @post	If the given file isn't modified after the 'If-Modified-Since' date (if this is present is the request), 
	 * 			and this is the first abnormality to occur, the status code will be set to 304.
	 * @post	If an 'If-Modified-Since'-date is included in the request, but it is not an HEAD or GET
	 * 			request, and this is the first abnormality to occur, the status code is set to 400.
	 * @post	If a problem concerning input- and outputstream occurs, and this is the first abnormality
	 * 			to occur, the statuscode is set to 500.
	 * 
	 * @return	The method returns a string, that corresponds to the header that will be sent to the ChatClient.
	 */
	public String createHeader(String fileName, byte[] pageToReturn) {
		String header = "HTTP/1.1 ";
		
		long dateTime = System.currentTimeMillis();		
		SimpleDateFormat dateTimeFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss");

		if (getSentence().contains("If-Modified-Since: ")) {
			if (! getSentence().contains("GET") || ! getSentence().contains("HEAD")) {
				setStatusCode(400);
			}
			int begin = getSentence().indexOf("If-Modified-Since:");
			int end = getSentence().indexOf("GMT", begin);
			String ifModifiedSince = getSentence().substring(begin + 19, end);
			ifModifiedSince = ifModifiedSince.trim();
			try {
				Date date1 = dateTimeFormat.parse(dateTimeFormat.format(
						(new File("Webpage/"+getRequestedFile())).lastModified()));
				Date date2 = dateTimeFormat.parse("di, 20 mrt 2018 23:38:38");
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
		
		if (getStatusCode()!=200) {
			pageToReturn = new byte[0];
		}
		header += "Content-Length: " + pageToReturn.length + "\r\n";
				
		header += "Date: " + dateTimeFormat.format(dateTime) + " GMT\r\n\r\n";
		
		return header;
	}

	/**
	 * Reads the file that is requested by hte client. 
	 * 
	 * @param fileName		The name of the file that is requested. 
	 * 
	 * @return	The method returns a byte array, that contains the content of the requested file.
	 * 
	 * @throws IOException	If any problem concerning reading the file occurs.
	 */
	public byte[] readFile(String fileName) throws IOException {
		Path filePath = Paths.get("Webpage" + fileName);
		return Files.readAllBytes(filePath);
	}

	/**
	 * A method to check whether the header contains a host header. 
	 * 
	 * @return	The method returns a boolean, depending on whether there is a host header in the
	 * 			client request.
	 */
	public boolean containsHostHeader() {
		return (getSentence().contains("\r\nHost:"));
	}
	
	/**
	 * A method to end the connection with the client. 
	 * 
	 * @effect	The method closes the client socket.
	 */
	public void endConnection() {
		try {
			getClientSocket().close();
			System.out.println("Ended a connection");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * A method to fetch the requested host from a client request, mentioned in its host header field. 
	 * 
	 * @return	The method returns a string, that contains the requested host.
	 * @return	If no host header is present, null is returned.
	 */
	public String getRequestedHost() {
		String host = null;
		if (containsHostHeader()) {
			int begin = getSentence().indexOf("Host:");
			int end = getSentence().indexOf("\r\n", begin);
			host = getSentence().substring(begin + 6, end);
		}
		return host;
	}

	/**
	 * A method to fetch the filename from the given client request. 
	 * 
	 * @return	The method returns a string, that contains the filename in the command between the first slash in
	 * 			the request and "HTTP/".
	 * @return	If the filename ends by a forward slash, the filename is appended by 'index.html'.
	 */
	public String getRequestedFile() {
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
	 * Returns the clientSocket of this Handler.
	 */
	private Socket getClientSocket() {
		return this.clientSocket;
	}

	/**
	 * Returns the inputstream of this h-Handler.
	 */
	private BufferedInputStream getInFromClient() {
		return this.inFromClient;
	}

	/**
	 * Returns the outputstream of this Handler.
	 */
	private DataOutputStream getOutToClient() {
		return this.outToClient;
	}

	/**
	 * Returns the status code of this Handler.
	 */
	private int getStatusCode() {
		return this.statusCode;
	}

	/**
	 * Returns the client request of this Handler.
	 */
	private String getSentence() {
		return sentence;
	}

	/**
	 * Returns the hostName of this Handler.
	 */
	private String getHostName() {
		return hostName;
	}

	/**
	 * Set the client socket to the given clientSocket. 
	 * 
	 * @param clientSocket	The new client socket of this Handler.
	 * 
	 * @post	| new.getclientSocket() == clientSocket
	 */
	private void setClientSocket(Socket clientSocket) {
		this.clientSocket = clientSocket;
	}
	
	/**
	 * Set the inputstream to the given inputstream. 
	 * 
	 * @param inFromClient	The inputstream of this Handler.
	 * 
	 * @post	| new.getinFromClient() == inFromClient
	 */
	private void setInFromClient(BufferedInputStream inFromClient) {
		this.inFromClient = inFromClient;
	}
	
	/**
	 * Set the outputstream to the given outputstream. 
	 * 
	 * @param outToClient	The outputstream of this Handler.
	 * 
	 * @post	| new.getoutToClient() == outToClient
	 */
	private void setOutToClient(DataOutputStream outToClient) {
		this.outToClient = outToClient;
	}
	
	/**
	 * Set the status code to the given status code.
	 * 
	 * @param status	The new status code of this Handler. 
	 * 
	 * @effect	The status code of this Handler is only change if its current status code is 200, so the
	 * 			first abnormality is the abnormality reported by the Handler.
	 * 
	 * @post	| new.getstatusCode() == status
	 */
	private void setStatusCode(int status) {
		if (getStatusCode() == 200) {
			this.statusCode = status;
		}
	}

	/**
	 * @param sentence 	The current client request of this Handler
	 * 
	 * @post	| new.getSentence() == sentence
	 */
	private void setSentence(String sentence) {
		this.sentence = sentence;
	}

	private Socket clientSocket;
	private BufferedInputStream inFromClient;
	private DataOutputStream outToClient;
	private int statusCode = 200;
	private String sentence;
	private String hostName = "localhost:9999";
}
