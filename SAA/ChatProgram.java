//PA5, open chat by Adam Wilkinson and Sean Bates
//CSE 223


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;

//When called, joins or creates a server
public class ChatProgram{
	public enum chatStatus{
		starting,
		hosting,
		client,
		error
	}
	public static chatStatus status = chatStatus.starting;

	public static void main(String argv[]) throws Exception {
		ClientMain listener = new ClientMain(1201);
		ClientMain talker = new ClientMain(0);

		if (status == chatStatus.hosting){
				ServerMain.InitServer();
				listener = new ClientMain(1201);
		}

		listener.start(); // Start the listening thread
		talker.start(); // This will be the input thread
	}
}


/**
* The main server class that listens for a connection
*/
class ServerMain extends Thread{
	private MyWrangler mw = new MyWrangler();  // One wrangler instance that gets passed to each listener thread
	public boolean runSilent = false;
	public static boolean running;

	public static void main(String args[]) {
		ServerMain ms = new ServerMain();
		ms.runSilent = false;

				running = true;
		ms.run();

	}

	public static boolean isActive(){
		return running;
	}

	public static void ShutDown(){

		running = false;
	}

	public static void InitServer(){
		ServerMain ms = new ServerMain();
		ms.runSilent = true;

		running = true;

		ms.start();


	}

	public void run() {
		mw.runSilent = runSilent;

		final int portNumber = 1201;
		System.out.println("Creating server socket on port " + portNumber);

		try {
			ServerSocket serverSocket = new ServerSocket(portNumber);
			while (running) { // Wait for a connection, then start a new thread when you get one
				MyListener lSock = new MyListener(serverSocket.accept(), mw); // Pass the unique socket and the common wrangler
				lSock.start();
				if (!runSilent) System.out.println("Client connected.");
			}
		} catch (Exception e) {
			System.out.println("ServerMain: " + e.toString());
		}

		mw.ShutDown();
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
	public boolean runSilent = false;

	public int count () { // Total count of socks in the pile
		synchronized (socks) {
			return socks.size();
		}
	}

	public void add(MyListener lsock) { // Add another sock to the pile
		synchronized (socks) {
			socks.add(lsock);
		}
	}

	public void remove(MyListener lsock) { // Clean up sock pile
		synchronized (socks) {
			socks.remove(lsock);
		}
	}

	//kill everything
	public void ShutDown(){
		running = false;
		for (MyListener lsock : socks){
			lsock.close();
		}
	}

	// Main method needed to pass (message) to all the connected clients
	public void message (String msg, MyListener talkingSock) {
		PrintWriter out = null;
		for (MyListener lsock : socks) {
			try {
				out = new PrintWriter(lsock.sock.getOutputStream(), true);
				out.println(msg);


				if (out.checkError()) { // Client isn't connected
					lsock.status = MyListener.sockState.error;

					socks.remove(lsock); // Clean up the list

					System.out.println("Client disconnected.");
				}
			} catch (Exception e) {
				lsock.close();
				System.out.println("message: " + e.toString());
				return;
			}
		}
		// Just here for info, and to make sure the sock count matches what you think it should

		if (!runSilent) System.out.println("Sending message through the wrangler, sock count: " + count());
		out = null;
	}

	public String UserList(){
		String chatList = "User: \t\tStatus:   \n=========================================\n";

		for (MyListener lsock : socks) {
			chatList += lsock.getStatus() + '\n';
		}

		chatList += "=========================================\n";

		return chatList;
	}
}


/*
* When a new client connects, a MyListener is created by ServerMain
* This holds the socket reference and the wrangler with the list of other connected clients

*/
class MyListener extends Thread {
	private String name = "";
	public Socket sock = null;
	private MyWrangler mw = null;

	boolean running = true;

	public enum sockState{
		connecting,
		active,
		typing,
		closing,
		error
	}
	public sockState status;

	MyListener (Socket newSocket, MyWrangler mainWrangler) {
		sock = newSocket; // Assign the socket to this so it has a reference to start a new thread
		mw = mainWrangler;
		status = sockState.connecting;
	}

	// Close the socket
	public void close() {
		try {
			if (sock != null) sock.close();
		} catch (Exception e) {
			status = sockState.error;
			System.out.println("MyListener close: " + e.toString());
		}
	}

	// Set the name attached to the client
	public void setHandle(String name) {
		this.name = name;
		message(name + " has joined the chat.");
		status = sockState.active;
	}

	// The name attached to the client
	public String getHandle() {
		return name;
	}

	public String getStatus(){
		if (status == sockState.connecting)
			return ("new user: connecting...");
		else if (status == sockState.active)
			return (name.toString() + ": \t\tACTIVE");
		else if (status == sockState.typing)
			return (name.toString() + ": \t\ttyping...");
		else if (status == sockState.closing)
			return (name.toString() + ": \t\tDISCONNECTED");
		else if (status == sockState.error)
			return (name.toString() + ": \t\tCONNECTION ERROR");

		else return "";
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
			status = sockState.error;
			mw.remove(this);
			System.out.println("Client disconnected.");
			return;
		}


		try {
			String entry = "";
			while (ServerMain.isActive()) { // Main loop waiting on client messages
				entry = br.readLine();
				if (entry == null || entry == "\0") {
					status = sockState.closing;
					message(name + " has left the chat.");
					return;
				}
				else if (entry.startsWith("/"))
					ChatCommand(entry, this.sock);

				else message(getHandle() + ": " + entry);
			}
		} catch (Exception e) {

			status = sockState.error;
			mw.remove(this);
			System.out.println("Client disconnected.");
			return;
		}
	}

	public void message(String msg) {
			mw.message(msg, this);
	}


//Escape commands for chat
	void ChatCommand (String command, Socket commandSock)
	{
		//chat commands
		if (command.startsWith("/who"))
			System.out.println( mw.UserList());

		else if (command.startsWith("/close"))
		{
			mw.remove(this);
			close();
		}else if (command.startsWith("/name"))
		{
			if (command.length() > 5)
				name = command.substring(6);
			else{
				try{PrintWriter	out = new PrintWriter(commandSock.getOutputStream(), true);
				out.print("usage: /name [newName]");
				}catch (Exception e){
					return;
				}
			}
		}else if (command.startsWith("/emote"))
		{
			if (command.length() > 6)
				mw.message(getHandle() + command.substring(6), this);
			else
				System.out.println("usage: /emote [active voice statement]");
		}
		else{
			try{PrintWriter	out = new PrintWriter(commandSock.getOutputStream(), true);
			out.print("Commands: /who /close /name /help /emote");
			}catch (Exception e){
				return;
			}
		}
	}
}




/**
* Chat Client, Main program
*/
class ClientMain extends Thread {
	private static Socket sock = null;
	private int port = 0;
	protected static volatile String message = "";
	private boolean running = true;

	ClientMain (int port) {
		if (port == 0) return;
		try { // Else it's the thread holding the socket
			this.port = port;
			sock = new Socket("localhost", port);
		} catch (Exception e)
		{
			//System.out.println("ClientMain constructor: " + e.toString());
			System.out.println("Initializing server...");
			ChatProgram.status = ChatProgram.chatStatus.hosting;
			return;
		}
	}

	//main program if run independently
	public static void main(String argv[]) throws Exception {
		ClientMain listener = new ClientMain(1201);
		ClientMain talker = new ClientMain(0);

		listener.start(); // Start the listening thread
		talker.start(); // This will be the input thread
	}

	public void close(){
		System.out.println("Exiting chat client.");

		//KILL SERVER IF HOSTED LOCALLY
		if (ChatProgram.status == ChatProgram.chatStatus.hosting)
			ServerMain.ShutDown();

		running = false;

		try {
			sock.close();
		}catch(Exception e) {
			System.out.println("Connection failure.");
		}
	}

	public void run() {
		BufferedReader br = null;
		PrintWriter out = null;
		String t = "";

		if (ChatProgram.status == ChatProgram.chatStatus.starting)
			ChatProgram.status = ChatProgram.chatStatus.client;

		if (port > 0) { // Listening to the socket
			try {
				sock.setSoTimeout(500); // 500ms timeout on reads
				br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				out = new PrintWriter(sock.getOutputStream(), true);
			} catch (Exception e) {
				System.out.println("Connection not established.");
				return;

			}
			System.out.print("Connected on port " + port + "\nWhat's your name? ");
			while (running) {
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
						if (message == null || message == "\0") {

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
			while (running) {
				try {
					t = br.readLine();
					synchronized (message) {
						message = t;


							if( message.startsWith("/close")){
								close();
								return;
							}

					}
				} catch (Exception e) {
					System.out.println("Talk, buffered reader: " + e.toString());

					return;
				}
			}
		}
	}
}
