// $Id$
 import java.io.*;
 import java.net.*;
 import java.util.*;
 import java.security.MessageDigest;
 import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

 public class Server
 {
   // The ServerSocket we'll use for accepting new connections
   //private static final String credentialFilePath = "/Users/emchen/Dropbox/columbia/computer_networks/programming/chatserver_cli/user_pass.txt";
   private static final String credentialFilePath = "/home/ec2805/computer_networks/chatserver_cli/user_pass.txt";
   private ServerSocket ss;
   private Map<String, String> credentials;
   private Map<String, HashMap<InetAddress, Integer>> blockedIPs;
   // A mapping from sockets to DataOutputStreams.  This will
   // help us avoid having to create a DataOutputStream each time
   // we want to write to a stream.
   private Hashtable outputStreams = new Hashtable();
   private Map<Socket, String> connectedUsers;

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
     connectedUsers = new ConcurrentHashMap<Socket, String>();
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
       DataOutputStream dout = null;
       DataInputStream din = null;
       try  {
           dout = new DataOutputStream( s.getOutputStream() );
            din = new DataInputStream( s.getInputStream() );
       } catch( IOException ie ) {ie.printStackTrace();}
    
        String username = null;
        try {
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
        } catch( IOException ie ) {ie.printStackTrace();}
       // check for duplicate user login
        if (dout != null && outputStreams.get(username) != null)   {
            // send error msg
            try  {
                dout.writeUTF("duplicate connection, can't log you in. closing connection.");
            } catch( IOException ie ) {ie.printStackTrace();}
                s.close();
       }
       // check credentials
       else {
           if (authenticated(s, username))   {
               connectedUsers.put(s, username);
                outputStreams.put( username, dout );
                // send welcome msg
                try  {
                    dout.writeUTF("Login successful!");
                } catch( IOException ie ) {ie.printStackTrace();}
  
               
               /*
               // Tell the world we've got it
               // Create a DataOutputStream for writing data to the
               // other side
               DataOutputStream dout = new DataOutputStream( s.getOutputStream() );
               // Save this stream so we don't need to make it again
               
               // Create a new thread for this connection, and then forget
               // about it
           new ServerThread( this, s );
           */
           }
           else {System.out.println("auth failed");}
       }
     }
}

static String sha1(String input) throws NoSuchAlgorithmException {
    MessageDigest mDigest = MessageDigest.getInstance("SHA1");
    byte[] result = mDigest.digest(input.getBytes());
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < result.length; i++) {
        sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
    }
    return sb.toString();
}

private boolean authenticated(Socket s, String username)  { 
    String password = null;
    boolean pwMatch = false;
    InetAddress ip = s.getInetAddress();
    System.out.println(ip);
    try { 
        DataOutputStream dout = new DataOutputStream( s.getOutputStream() );
        DataInputStream din = new DataInputStream( s.getInputStream() );

        int maxConsecutiveAttempts = 3;
        for (int i=0; i<maxConsecutiveAttempts; i++)    {
            // get SHA-1 hash of password
            String hashed = null;
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
            try {
                hashed = sha1(password);
            } catch(NoSuchAlgorithmException e) { e.printStackTrace();}
            if (hashed.equals(credentials.get(username)))    {
                pwMatch = true;
                break;
            }
            else {password = null;}
        }
    } catch( IOException ie ) { System.out.println( ie ); }
    if (!(pwMatch)) {
        // TODO add to list of blocked IPs
    }
    return pwMatch;
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
