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
    private static final Pattern PATTERN_SENDMULTI = Pattern.compile("send \\([\\S++ &&[^\\)]]++\\) [^(\r\n)|(\n)]++");
    private static final Pattern PATTERN_SEND = Pattern.compile("send \\S++ [^(\r\n)|(\n)]++");
    private static final Pattern PATTERN_LOGOUT = Pattern.compile("^logout$");



   // The Server that spawned us
   private Server server;
   // The Socket connected to our client
   private Socket socket;
   public enum Command  {
       WHO(PATTERN_WHO),
       LAST(PATTERN_LAST),
       BROADCAST(PATTERN_BROADCAST),
       SENDMULTI(PATTERN_SENDMULTI),
       SEND(PATTERN_SEND),
       LOGOUT(PATTERN_LOGOUT);

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
       DataOutputStream dout = new DataOutputStream( socket.getOutputStream() );
       // Over and over, forever ...
       while (true) {
         // read client message
         String message = din.readUTF().trim(); 
         // parse message for command
         System.out.println(message);
         Command clientCommand = null;
        for (Command c : Command.values())    {
            if (c.getPattern().matcher(message).find()) {
                clientCommand = c;
                System.out.println(c);
                break;
            }
        }
        if (clientCommand == null)
            dout.writeUTF("Invalid Command: " + message);
        else    {
            Long currentTimestamp = new Long(System.currentTimeMillis() / 1000L);
            StringBuilder msg = null;
            String[] tokens = null;
            switch (clientCommand)  {
                case WHO:
                    server.who(dout);
                    server.updateActivityTime(socket, currentTimestamp);
                    break;
                case LAST:
                    Long n = (new Long(60)) * Long.parseLong(message.split(" ")[1]);
                    server.last(dout, n);
                    server.updateActivityTime(socket, currentTimestamp);
                    break;
                case BROADCAST:
                    tokens = message.split(" ");
                     msg = new StringBuilder();
                     for (int i=1; i<tokens.length-1; i++) {
                        msg.append(tokens[i]).append(" ");
                     }
                     msg.append(tokens[tokens.length-1]);
                    server.broadcast(dout, socket, msg.toString());
                    server.updateActivityTime(socket, currentTimestamp);
                     break;
                case SENDMULTI:
                    Matcher matcher = Pattern.compile("\\([^\\)]++\\)").matcher(message); 
                    if (matcher.find()) {
                        int start = matcher.start();
                        int end = matcher.end();
                        String[] recipients = message.substring(start+1, end-1).trim().split(" ");  
                        tokens = message.substring(end).trim().split(" ");
                        msg = new StringBuilder();
                        for (int i=0; i<tokens.length-1; i++) {
                            msg.append(tokens[i]).append(" ");
                        }
                        msg.append(tokens[tokens.length-1]);
                        server.sendMulti(dout, recipients, msg.toString());
                    }
                    server.updateActivityTime(socket, currentTimestamp);
                     break;
                case SEND:
                     tokens = message.split(" ");
                     String recipient = tokens[1];
                     msg = new StringBuilder();
                     for (int i=2; i<tokens.length-1; i++) {
                        msg.append(tokens[i]).append(" ");
                     }
                     msg.append(tokens[tokens.length-1]);
                     server.send(dout, recipient, msg.toString());
                    server.updateActivityTime(socket, currentTimestamp);
                     break;
                case LOGOUT:
                     server.logout(dout, socket);
                     break;
            }   // switch
        }   //valid command
       }    //while
     } catch( EOFException ie ) {
       System.out.println("EOFException occurred in ServerThread");
        server.removeConnection( socket );
     } catch( IOException ie ) {
         System.out.println("IOException occurred in ServerThread");
     }
   } //run
} //ServerThread
