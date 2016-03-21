// $Id$
 import java.util.Scanner;
 import java.io.*;
 import java.net.*;
 public class Client implements Runnable
 {
   public static final String ANSI_RESET = "\u001B[0m";
   public static final String ANSI_RED = "\u001B[31m";
   // The socket connecting us to the server
   private Socket socket;
   // The streams we communicate to the server; these come
   // from the socket
   private DataOutputStream dout;
   private DataInputStream din;
   private Scanner sc;

   // Constructor
   public Client( String host, int port ) {
     // Connect to the server
     try {
       sc = new Scanner(System.in);
       // Initiate the connection
       socket = new Socket( host, port );
       // Let's grab the streams and create DataInput/Output streams
       // from them
       din = new DataInputStream( socket.getInputStream() );
       dout = new DataOutputStream( socket.getOutputStream() );
       // Start a background thread for receiving messages
       new Thread( this ).start(); 
     } catch( IOException ie ) { System.out.println( ie ); }
} //Constructor
   
   // Gets called when the user types something
   private void processMessage( String message ) {
    try {
       // Send it to the server
       dout.writeUTF( message );
        } catch( IOException ie ) { System.out.println( ie ); }
    }

    public static void main(String[] args)  {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Client client = new Client(host, port);
        while (true) {
           String next = client.sc.nextLine();
           client.processMessage(next);
       }
    }

   // Display messages from server
   public void run() {
    try {
       // Receive messages one-by-one, forever
       while (true) {
         // Get the next message
         String message = din.readUTF();
         // Print messages from the server in red
         System.out.println(ANSI_RED + message + ANSI_RESET);
       }
     } catch (EOFException eof)   {
         System.exit(0);
     } catch( IOException ie ) { System.out.println( ie ); }
   }
}
