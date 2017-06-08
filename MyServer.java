import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;

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
			while (true) {				
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
	
	public void message (String msg, MyListener talkingSock) {		
		System.out.println("Sending message through the wrangler, sock count: " + count());
		PrintWriter out = null;
		for (MyListener sock : socks) {
			if (talkingSock != sock && talkingSock.sock.isConnected()) {
				try {
					out = new PrintWriter(sock.sock.getOutputStream(), true);
					out.println(msg);
					if (out.checkError()) {
						socks.remove(sock);
						System.out.println("Client disconnected.");
					}
				} catch (Exception e) {
					sock.close();
					System.out.println("message: " + e.toString());
					return;
				}
			}
		}
		out = null;
	}
}

class MyListener extends Thread {
	private boolean debug = false;
	private String name = "";
	public Socket sock = null;
	private MyWrangler mw = null;	
	PrintWriter pw = null;
	
	MyListener (Socket sock, MyWrangler mw) {
		this.sock = sock; // Assign the socket to this so it has a reference to start a new thread
		this.mw = mw;
	}
	
	public void close() {
		try {
			if (sock != null) sock.close();
		} catch (Exception e) {
			System.out.println("MyListener close: " + e.toString());
		}
	}
	
	public void setHandle(String name) {
		this.name = name;
		message(name + " has joined the chat.");
	}
	
	public String getHandle() { 
		return name; 
	}
	
	public void run() {
		PrintWriter out = null;
		BufferedReader br = null;
		
		try {
			if (debug) System.out.println("In run method");			
			mw.add(this); // Add to the sock collection in the wrangler so we can message back
			
			out = new PrintWriter(sock.getOutputStream(), true);
			out.print("What's your name? ");
			out.flush();
			br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			setHandle(br.readLine());
		} catch (Exception e) {
			System.out.println("MyListener.run 1: " + e.toString());
			return;
		}
		
		try {		
			String t = "";
			while (true) {
				if (!sock.isConnected()) return;
				t = br.readLine();
				if (t == "" || t == null) return;
				if (!message(getHandle() + ": " + t)) return;
				
				out.print("\n" + name + ":");
			}
		} catch (Exception e) {
			System.out.println("MyListener.run 2: " + e.toString());
			return;
		}
	}

	public boolean message(String msg) {
		if (mw != null && !sock.isClosed()) {
			if (debug) System.out.println("Sending message from the listener");
			mw.message(msg, this);
			return true;
		} else {
			if (debug) System.out.println("listener message Sock: " + sock.isConnected() + " Wrangler: " + mw == null);
			return false;
		}
	}
}

class MyClient extends Thread {
	private static Socket sock = null;
	private boolean mode = false;
	
	MyClient (Socket sock, boolean mode) {
		try {
			sock = new Socket("localhost", 1201);
		} catch (Exception e) {
			System.out.println("MyClient constructor: " + e.toString());
			return;
		}
		this.sock = sock;
		this.mode = mode;
	}
	
	public static void main(String argv[]) throws Exception {
		MyClient listener = new MyClient(sock, true);
		MyClient talker = new MyClient(sock, false);
		
		listener.start();
		talker.start();
	}
	
	public void run() {
		if (mode) {
			System.out.println("Starting listener.");
			Listen();
		} else {
			System.out.println("Starting talker.");
			Talk();
		}
	}
	
	private void Talk() {
		String t = "";
		PrintWriter out = null;
		try {
			out = new PrintWriter(sock.getOutputStream(), true);
		} catch (Exception e) {
			System.out.println("Talk, print writer: " + e.toString());
			return;
		}
		
		if (out == null) return;
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			try {
				t = br.readLine();
			} catch (Exception e) {
				System.out.println("Talk, buffered reader: " + e.toString());
				return;
			}
			if (t == "" || t == null) return;
			out.print(t);
			out.flush();
		}
	}
	
	private void Listen() {
		BufferedReader br = null;
		String t = "";
		
		while(true) {
			try {
				br = new BufferedReader(new InputStreamReader(System.in));
				t = br.readLine();
			} catch (Exception e) {
				System.out.println("Listen readline: " + e.toString());
				return;
			}
			
			if (t == "" || t == null) return;
			System.out.print(t);
		}
	}
}