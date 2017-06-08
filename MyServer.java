import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;

/**
* The main server class that listens for a connection
*/
public class MyServer {
	private MyWrangler mw = new MyWrangler();  // One wrangler instance that gets passed to each listener thread
	
	public static void main(String args[]) {
		MyServer ms = new MyServer();
		ms.run();
	}
	
	private void run() {
		final int portNumber = 1201;
		System.out.println("Creating server socket on port " + portNumber);
		try {
			ServerSocket serverSocket = new ServerSocket(portNumber);
			while (true) { // Wait for a connection, then start a new thread when you get one
				MyListener mySock = new MyListener(serverSocket.accept(), mw); // Pass the unique socket and the common wrangler
				mySock.start();
				System.out.println("Client connected.");
			}		
		} catch (Exception e) {
			System.out.println("MyServer: " + e.toString());
		}
	}
}

/**
* Didn't know what to call it, so this class "wrangles" all the socket clients, 
* into a list.  When a client sends a message it comes through here, and goes out
* to all the other clients
*/
class MyWrangler extends Thread {
	private List<MyListener> socks = new ArrayList<MyListener>();
	private boolean running = true;
	
	public int count () { // Total count of socks in the pile
		return socks.size();
	}
	
	public void add(MyListener sock) { // Add another sock to the pile
		socks.add(sock);
	}
	
	public void remove(MyListener sock) { // Clean up sock pile
		socks.remove(sock);
	}
	
	// Main method needed to pass message to all the connected clients
	public void message (String msg, MyListener talkingSock) {				
		PrintWriter out = null;
		for (MyListener sock : socks) {
			try {
				out = new PrintWriter(sock.sock.getOutputStream(), true);
				out.println(msg);
				if (out.checkError()) { // Client isn't connected
					socks.remove(sock); // Clean up the list
					System.out.println("Client disconnected.");
				}
			} catch (Exception e) {
				sock.close();
				System.out.println("message: " + e.toString());
				return;
			}
		}
		// Just here for info, and to make sure the sock count matches what you think it should
		System.out.println("Sending message through the wrangler, sock count: " + count());
		out = null;
	}
}

/*
* When a new client connects, a MyListener is created by MyServer
* This holds the socket reference and the wrangler with the list of other connected clients

*/
class MyListener extends Thread {
	private String name = "";
	public Socket sock = null;
	private MyWrangler mw = null;	
	PrintWriter pw = null;
	
	MyListener (Socket sock, MyWrangler mw) {
		this.sock = sock; // Assign the socket to this so it has a reference to start a new thread
		this.mw = mw;
	}
	
	// Close the socket
	public void close() {
		try {
			if (sock != null) sock.close();
		} catch (Exception e) {
			System.out.println("MyListener close: " + e.toString());
		}
	}
	
	// Set the name attached to the client
	public void setHandle(String name) {
		this.name = name;
		message(name + " has joined the chat.");
	}
	
	// The name attached to the client
	public String getHandle() { 
		return name; 
	}
	
	public void run() {
		PrintWriter out = null;
		BufferedReader br = null;
		
		try { // First asks for a name connected to the client
			mw.add(this); // Add to the sock collection in the wrangler so we can message back
			out = new PrintWriter(sock.getOutputStream(), true);
			out.print("What's your name? ");
			out.flush();
			br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			setHandle(br.readLine());
		} catch (Exception e) {
			mw.remove(this);
			System.out.println("Client disconnected.");
			return;
		}
		
		try {
			String t = "";
			while (true) { // Main loop waiting on client messages
				t = br.readLine();
				if (t == "" || t == null) {
					message(name + " has left the chat.");
					return;
				} else message(getHandle() + ": " + t);				
			}
		} catch (Exception e) {
			mw.remove(this);
			System.out.println("Client disconnected.");
			return;
		}
	}

	public void message(String msg) {
		mw.message(msg, this);
	}
}

/**
* Just a generic client, not quite working yet
*/
class MyClient extends Thread {
	private static Socket sock = null;
	private int port = 0;
	protected static volatile String message = "";
	
	MyClient (int port) {
		if (port == 0) return;		
		try { // Else it's the thread holding the socket
			this.port = port;
			sock = new Socket("localhost", port);
		} catch (Exception e) {
			System.out.println("MyClient constructor: " + e.toString());
			return;
		}
	}
	
	public static void main(String argv[]) throws Exception {
		MyClient listener = new MyClient(1201);
		MyClient talker = new MyClient(0);
		listener.start(); // Start the listening thread
		talker.start(); // This will be the input thread
	}
	
	public void run() {
		BufferedReader br = null;
		PrintWriter out = null;
		String t = "";
		
		if (port > 0) { // Listening to the socket					
			try {
				sock.setSoTimeout(500); // 500ms timeout on reads
				br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				out = new PrintWriter(sock.getOutputStream(), true);
			} catch (Exception e) {
			}
			System.out.print("Connected on port " + port + "\nWhat's your name? ");
			while (true) {
				try {					
					if (br.ready()) {
						t = br.readLine();
						System.out.println(t);
						t = "";
					}
				} catch (Exception e) {
					//System.out.println (e.toString());
					// The TCP/IP read timeout is set to 500ms above, this is a stupid hack
				}				

				try {
					synchronized (message) {
						if (message == null) {							
							System.out.println("Exiting chat client.");
							return;
						}
						if (message.compareTo("") > 0) {
							out.println(message);
							out.flush();
							message = "";
						}
					}
				} catch (Exception e) {
					//System.out.println("Exiting chat client.");
				}
			}			
		} else { // User input thread
			br = new BufferedReader(new InputStreamReader(System.in));
			while (true) {
				try {
					t = br.readLine();
					synchronized (message) {
						message = t;
					}
				} catch (Exception e) {
					System.out.println("Talk, buffered reader: " + e.toString());
				}
			}
		}
	}
}