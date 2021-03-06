import java.io.*;
import java.net.*;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private final Long BLOCK_TIME, TIME_OUT;
    private static final String credentialFilePath = new File("user_pass.txt").getAbsolutePath();
    private ServerSocket ss;
    private Map<String, String> credentials;
    private Map<String, HashMap<InetAddress, Long>> blockedIPs;
    // store DataOutputStreams so we don't have to create a new DataOutputStream
    // instance each time we want to write to a stream
    private Map<String, DataOutputStream> outputStreams;
    private Map<Socket, String> connectedUsers;
    private Map<Socket, Long> lastActive;
    private Map<String, Long> lastLogoutTime;
    
    // Constructor 
    public Server(int port) throws IOException {
        try  {
            BLOCK_TIME = Long.parseLong(System.getenv().get("BLOCK_TIME"));
        } catch (NumberFormatException e) {throw new RuntimeException("BLOCK_TIME environment variable is not set");}
        try  {
            TIME_OUT = Long.parseLong(System.getenv().get("TIME_OUT"));
        } catch (NumberFormatException e) {throw new RuntimeException("TIME_OUT environment variable is not set");}
        // read credentials from file and store for authentication
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
        blockedIPs = new HashMap<String, HashMap<InetAddress, Long>>();
        connectedUsers = new ConcurrentHashMap<Socket, String>();
        outputStreams = new ConcurrentHashMap<String, DataOutputStream>();
        lastActive = new ConcurrentHashMap<Socket, Long>();
        lastLogoutTime = new ConcurrentHashMap<String, Long>();
        // start garbage collection thread that closes inactive connections
        GarbageCollector gc = new GarbageCollector(this);
        new Thread(gc).start();
        listen( port );
    }

    public void collectGarbage()    {
        Long currentTimestamp = new Long(System.currentTimeMillis() / 1000L);
        for (Socket client: lastActive.keySet())  {
            // this connection has been inactive for longer than the TIME_OUT period
            try {
                if (moreThanNSecondsAgo(currentTimestamp, lastActive.get(client), TIME_OUT))    {
                    String username = connectedUsers.get(client);
                    DataOutputStream doutOfInactiveClient = outputStreams.get(username);
                    logout(doutOfInactiveClient, client);
                }
            } catch (NullPointerException np)   {
                System.out.println("NullPointerException during garbage collection");
            }
        }
    }

    public void updateActivityTime(Socket socket, Long currentTimestamp) {
        lastActive.put(socket, currentTimestamp);
    }

    private boolean moreThanNSecondsAgo(Long currentTimestamp, Long eventTimestamp, Long n) {
        return (currentTimestamp - eventTimestamp > n);
    }

    // This method is synchronized because it's part of a test-and-set operation.
    // Don't want a concurrent addition or removal of this user's connection 
    // to be interleaved with this check.
    private synchronized boolean duplicateLogin(String username)    {
        return (outputStreams.get(username) != null);
    }

    // This method is synchronized because it's part of a test-and-set operation.
    // Don't want a concurrent removal or check of this user's connection 
    // to be interleaved with this addition.
    private synchronized void addConnection(Socket s, String username, Long timestamp, DataOutputStream dout)   {
        connectedUsers.put(s, username);
        outputStreams.put( username, dout );
        lastActive.put(s, timestamp);
        try  {
            dout.writeUTF("Login successful!");
        } catch( IOException ie ) {ie.printStackTrace();}
    }

    public void who(DataOutputStream dout)  {
        StringBuilder result = new StringBuilder();
        for (String username: connectedUsers.values())  {
            result.append(username).append(" ");
        }
        try {
            dout.writeUTF(result.toString());
        } catch( IOException ie ) {ie.printStackTrace();}
    }

    public void last(DataOutputStream dout, Long n) {
        Long currentTimestamp = new Long(System.currentTimeMillis() / 1000L);
        StringBuilder result = new StringBuilder();
        for (String username: connectedUsers.values())  {
            result.append(username).append(" ");
        }
        for (String loggedoutUsername: lastLogoutTime.keySet())   {
            Long logoutTime = lastLogoutTime.get(loggedoutUsername);
            if (outputStreams.get(loggedoutUsername) == null && !(moreThanNSecondsAgo(currentTimestamp, logoutTime, n)))   {
                result.append(loggedoutUsername).append(" ");
            }
        }
        try {
            dout.writeUTF(result.toString());
        } catch( IOException ie ) {ie.printStackTrace();}
    }

    public void broadcast(DataOutputStream senderOut, Socket s, String msg)  {
        String senderUsername = connectedUsers.get(s);
        for (String username: outputStreams.keySet())   {
            if (username.equals(senderUsername))
                continue;
            else
                send(s, senderOut, username, msg);
        }
    }

    public void sendMulti(Socket senderSocket, DataOutputStream senderOut, String[] recipientUsernames, String msg)  {
        for (int i=0; i<recipientUsernames.length; i++)
            send(senderSocket, senderOut, recipientUsernames[i], msg);
    }

    private String getUsername(Socket s)    {
        return connectedUsers.get(s);
    }

    // synchronize this method to prevent a potentially concurrent removal of this connection
    public synchronized void send(Socket senderSocket, DataOutputStream senderOut, String recipient, String msg)  {
        DataOutputStream recipientOut = outputStreams.get(recipient);
        // get sender's username
        String senderName = getUsername(senderSocket);
        try {
            if (senderName != null)
                recipientOut.writeUTF(senderName + " says: " + msg);
            else
                recipientOut.writeUTF(msg);
        } catch (NullPointerException npe) {
            try {
                senderOut.writeUTF(recipient + " is not connected");
            } catch( IOException ie ) {ie.printStackTrace();}
        } catch( IOException ie2 ) {ie2.printStackTrace();}
    }

    // This method is synchronized because it's part of a test-and-set operation.
    // Don't want a concurrent removal or check of this user's connection 
    // to be interleaved with this addition.
    synchronized void removeConnection(Socket s) {
        Long currentTimestamp = new Long(System.currentTimeMillis() / 1000L);
        String username = connectedUsers.remove(s);
        outputStreams.remove(username);
        lastActive.remove(s);
        lastLogoutTime.put(username, currentTimestamp);
        try {
            s.close();
        } catch( IOException ie ) { 
            System.out.println("removeConnection IOException");
            ie.printStackTrace();
        }
    }

    public void logout(DataOutputStream dout, Socket s)    {
        try {
            dout.writeUTF("Logging out...");
        } catch( IOException ie ) {ie.printStackTrace();}
        removeConnection(s);
    }

    private void listen( int port ) throws IOException {
        // Create the ServerSocket
        ss = new ServerSocket( port );
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
            if (dout != null && duplicateLogin(username))   {
                // send error msg
                try  {
                    dout.writeUTF("duplicate connection, can't log you in. closing connection.");
                } catch( IOException ie ) {
                    ie.printStackTrace();
                }
                s.close();
            }
            // check credentials
            else {
                // check if this connection request comes from a blocked IP for that user
                HashMap<InetAddress, Long> IPblockTimes = blockedIPs.get(username);
                InetAddress currentIP = s.getInetAddress();
                Long currentTimestamp = new Long(System.currentTimeMillis() / 1000L);
                Long blockTimestamp = null;

                if (IPblockTimes != null && (blockTimestamp = IPblockTimes.get(currentIP)) != null) {
                    if (moreThanNSecondsAgo(currentTimestamp, blockTimestamp, BLOCK_TIME))    {
                        IPblockTimes.remove(currentIP);
                        blockedIPs.put(username, IPblockTimes);
                        System.out.println(blockedIPs.get(username).get(currentIP));
                    }
                    else    {
                        // this IP is still blocked for that user
                        try  {
                            dout.writeUTF("This IP is blocked for this user.");
                        } catch( IOException ie ) {ie.printStackTrace();}
                        s.close();
                    }
                }
                if (authenticated(s, username))   {
                    addConnection(s, username, currentTimestamp, dout);
                    new ServerThread( this, s );
                }
                else {
                    // credential check failed
                    try  {
                        dout.writeUTF("Login failed. Wrong username or password.");
                    } catch( IOException ie ) {ie.printStackTrace();}
                    s.close();
                    // update blockedIPs
                    if (IPblockTimes == null)
                        IPblockTimes = new HashMap<InetAddress, Long>();
                    IPblockTimes.put(currentIP, currentTimestamp);
                    blockedIPs.put(username, IPblockTimes);
                }    // credential check
            }    // not duplicate login
        }  // while
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
        return pwMatch;
    }

    // Usage: java Server <port>
    static public void main(String args[]) throws Exception {
        // Get the port number from the command line
        int port = Integer.parseInt(args[0]);
        // Create a Server object, which will automatically begin
        // accepting connections.
        new Server( port );
    }
}
