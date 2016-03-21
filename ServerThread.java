// $Id$
 import java.io.*;
 import java.net.*;
 import java.util.regex.Pattern;
 import java.util.regex.Matcher;

 public class ServerThread extends Thread
 {
   private static final Pattern PATTERN_WHO = Pattern.compile("^who$");
   private static final Pattern PATTERN_LAST = Pattern.compile("last \\d++");
   private static final Pattern PATTERN_BROADCAST = Pattern.compile("broadcast [^(\r\n)|(\n)]++");

   // The Server that spawned us
   private Server server;
   // The Socket connected to our client
   private Socket socket;
   public enum Command  {
       WHO(PATTERN_WHO),
       LAST(PATTERN_LAST),
       BROADCAST(PATTERN_BROADCAST);

       private final Pattern pattern;
       Command(Pattern p)   {
           this.pattern = p;
       }
       Pattern getPattern() {
           return this.pattern;
       }
   }
   
   // Constructor.
   public ServerThread( Server server, Socket socket ) {
     // Save the parameters
     this.server = server;
     this.socket = socket;
     // Start up the thread
      start();
    }

   // This runs in a separate thread when start() is called in the
   // constructor.
   public void run() {
      try {
       // Create a DataInputStream for communication; the client
       // is using a DataOutputStream to write to us
       DataInputStream din = new DataInputStream( socket.getInputStream() );
       // Over and over, forever ...
       while (true) {
         // ... read the next message ...
         String message = din.readUTF().trim();
         // ... tell the world ...
         // parse message for command
         // ...
         System.out.println(message);
        for (Command c : Command.values())    {
            if (c.getPattern().matcher(message).find())
                System.out.println(c);
        }
        
         //server.sendToAll( message );
       }
     } catch( EOFException ie ) {
       System.out.println("EOFException");
       // This doesn't need an error message
     } catch( IOException ie ) {
       // This does; tell the world!
         System.out.println("IOException");
       ie.printStackTrace();
     } finally {
       // The connection is closed for one reason or another,
       // so have the server dealing with it
       server.removeConnection( socket );
    } //finally
  } //run
} //ServerThread
