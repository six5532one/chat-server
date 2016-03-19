// $Id$
 import java.io.*;
 import java.net.*;
 import java.util.*;
 public class Server
 {
   // The ServerSocket we'll use for accepting new connections
   private static final String credentialFilePath = "/Users/emchen/Dropbox/columbia/computer_networks/programming/chatserver_cli/user_pass.txt";
   private ServerSocket ss;
   private Map<String, String> credentials;
   private Map<String, HashMap<InetAddress, Integer>> blockedIPs;
   // A mapping from sockets to DataOutputStreams.  This will
   // help us avoid having to create a DataOutputStream each time
   // we want to write to a stream.
   private Hashtable outputStreams = new Hashtable();

   // Constructor and while-accept loop all in one.   
   public Server( int port ) throws IOException {
     File credentialsFile = new File(credentialFilePath);
     credentials = new HashMap<String, String>();
     try {
        BufferedReader br = new BufferedReader(new FileReader(credentialsFile)); 
         String line;
         while ((line = br.readLine()) != null) {
             String[] data = line.trim().split(" ");
             credentials.put(data[0], data[1]);
         }
     }
     catch (Exception e)    {
         System.err.println(e.getMessage());
     }
     blockedIPs = new HashMap<String, HashMap<InetAddress, Integer>>();
     // All we have to do is listen
     listen( port );
   }
   
private void listen( int port ) throws IOException {
     // Create the ServerSocket
     ss = new ServerSocket( port );
     // Tell the world we're ready to go
     System.out.println( "Listening on "+ss );
     // Keep accepting connections forever
     while (true) {
       // Grab the next incoming connection
       Socket s = ss.accept();
       String nameOfLoggedInUser;
       if ((nameOfLoggedInUser = authenticated(s)) != null)   {
           System.out.println(nameOfLoggedInUser);
           /*
           // Tell the world we've got it
           System.out.println( "Connection from "+s.getInetAddress() );
           // Create a DataOutputStream for writing data to the
           // other side
           DataOutputStream dout = new DataOutputStream( s.getOutputStream() );
           // Save this stream so we don't need to make it again
           outputStreams.put( s, dout );
           // Create a new thread for this connection, and then forget
           // about it
           new ServerThread( this, s );
           */
       }
       else {System.out.println("auth failed");}
     }
}

private String authenticated(Socket s)  { 
    String username = null;
    String password = null;
    try {
        DataOutputStream dout = new DataOutputStream( s.getOutputStream() );
        DataInputStream din = new DataInputStream( s.getInputStream() );
        while (username == null)    {
            dout.writeUTF( "Username: " );
            String[] tokens = din.readUTF().trim().split(" ");
            if (tokens.length > 1)  {
                dout.writeUTF("Username may not contain whitespace.");
            }
            else if (tokens.length == 1) {
                username = tokens[0];
            }
        }
        while (password == null)    {
            dout.writeUTF( "Password: " );
            String[] tokens = din.readUTF().trim().split(" ");
            if (tokens.length > 1)  {
                dout.writeUTF("Password may not contain whitespace.");
            }
            else if (tokens.length == 1) {
                password = tokens[0];
            }
        }
    } catch( IOException ie ) { System.out.println( ie ); }
    return username;
}

// Get an enumeration of all the OutputStreams, one for each client
// connected to us
Enumeration getOutputStreams() {
     return outputStreams.elements();
}

// Send a message to all clients (utility routine)
void sendToAll( String message ) {
   // We synchronize on this because another thread might be
   // calling removeConnection() and this would screw us up
   // as we tried to walk through the list
   synchronized( outputStreams ) {
     // For each client ...
     for (Enumeration e = getOutputStreams(); e.hasMoreElements(); ) {
       // ... get the output stream ...
       DataOutputStream dout = (DataOutputStream)e.nextElement();
       // ... and send the message
       try {
         dout.writeUTF( message );
       } catch( IOException ie ) { System.out.println( ie ); }
    } //for
  } //synchronized
}


 // Remove a socket, and it's corresponding output stream, from our
 // list.  This is usually called by a connection thread that has
 // discovered that the connectin to the client is dead.
 void removeConnection( Socket s ) {
   // Synchronize so we don't mess up sendToAll() while it walks
   // down the list of all output streamsa
   synchronized( outputStreams ) {
     // Tell the world
     System.out.println( "Removing connection to "+s );
     // Remove it from our hashtable/list
     outputStreams.remove( s );
     // Make sure it's closed
     try {
       s.close();
     } catch( IOException ie ) {
       System.out.println( "Error closing "+s );
       ie.printStackTrace();
     }
  } //synchronized
}

// Main routine
// Usage: java Server <port>
static public void main( String args[] ) throws Exception {
 // Get the port # from the command line
 int port = Integer.parseInt( args[0] );
 // Create a Server object, which will automatically begin
 // accepting connections.
 new Server( port );
  } //main
} //Server
