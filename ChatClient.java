package client;

import shared.Message;
import shared.Message.Type;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Chat client application.
 * Commands typed at prompt:
 *   /login  <user> <pass>   – authenticate with existing account
 *   /reg    <user> <pass>   – register and log in
 *   /dm     <user> <msg>    – send a private message
 *   /list                   – list online users
 *   /quit                   – disconnect gracefully
 *   <anything else>         – broadcast message to everyone
 * Usage:  java client.ChatClient [host] [port]
 */
public class ChatClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 5000;

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    private volatile boolean running = false;

    //  Connect

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        // Output stream must be created before input stream (same as server side)
        out = new ObjectOutputStream(socket.getOutputStream());
        in  = new ObjectInputStream(socket.getInputStream());
        running = true;
        System.out.println("Connected to " + host + ":" + port);
        System.out.println("Commands: /login <user> <pass>  |  /reg <user> <pass>  |  " +
                           "/dm <user> <msg>  |  /list  |  /quit\n");
    }

    //  Reader thread

    /**
     * Spawns a daemon thread that continuously reads Messages from the server
     * and prints them.
     */
    private void startReaderThread() {
        Thread reader = new Thread(() -> {
            try {
                while (running) {
                    Message msg = (Message) in.readObject();
                    System.out.println(msg);          
                    System.out.print("> ");           
                    System.out.flush();
                }
            } catch (EOFException | java.net.SocketException ignored) {
                // Server closed the connection
            } catch (IOException | ClassNotFoundException e) {
                if (running) {
                    System.err.println("[Client] Lost connection: " + e.getMessage());
                }
            } finally {
                running = false;
            }
        });
        reader.setDaemon(true);
        reader.setName("reader-thread");
        reader.start();
    }

    // Input loop 

    public void inputLoop() {
        startReaderThread();
        Scanner scanner = new Scanner(System.in);

        while (running) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            try {
                processInput(line);
            } catch (IOException e) {
                System.err.println("[Client] Send error: " + e.getMessage());
                break;
            }
        }
        close();
    }

    //  Command parsing 

    private void processInput(String line) throws IOException {
        if (line.startsWith("/login ")) {
            String[] parts = line.substring(7).split(" ", 2);
            if (parts.length < 2) { System.out.println("Usage: /login <user> <pass>"); return; }
            send(new Message(Type.LOGIN, null, parts[0] + ":" + parts[1]));

        } else if (line.startsWith("/reg ")) {
            String[] parts = line.substring(5).split(" ", 2);
            if (parts.length < 2) { System.out.println("Usage: /reg <user> <pass>"); return; }
            send(new Message(Type.REGISTER, null, parts[0] + ":" + parts[1]));

        } else if (line.startsWith("/dm ")) {
            // /dm targetUser the message text
            String rest = line.substring(4);
            String[] parts = rest.split(" ", 2);
            if (parts.length < 2) { System.out.println("Usage: /dm <user> <message>"); return; }
            send(new Message(Type.DM, null, parts[0] + ":" + parts[1]));

        } else if (line.equals("/list")) {
            send(new Message(Type.LIST, null, ""));

        } else if (line.equals("/quit")) {
            send(new Message(Type.QUIT, null, ""));
            running = false;

        } else if (line.equals("/help")) {
            printHelp();

        } else {
            // Plain text → broadcast
            send(new Message(Type.BROADCAST, null, line));
        }
    }

    // I/O helpers 

    private synchronized void send(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
        out.reset();
    }

    private void close() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        System.out.println("Disconnected.");
    }

    //  Help text 

    private void printHelp() {
        System.out.println("─────────────────────────────────────────────");
        System.out.println("│              Available Commands              │");
        System.out.println("─────────────────────────────────────────────");
        System.out.println("│ /login <user> <pass>  – Log in              │");
        System.out.println("│ /reg   <user> <pass>  – Register            │");
        System.out.println("│ /dm    <user> <msg>   – Private message     │");
        System.out.println("│ /list                 – Show online users   │");
        System.out.println("│ /quit                 – Disconnect          │");
        System.out.println("│ <text>                – Broadcast to all    │");
        System.out.println("────────────────────────────────────────────┘");
    }

    //  Main 

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int    port = DEFAULT_PORT;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) { System.err.println("Invalid port, using " + DEFAULT_PORT); }
        }

        ChatClient client = new ChatClient();
        try {
            client.connect(host, port);
            client.inputLoop();
        } catch (IOException e) {
            System.err.println("[Client] Cannot connect to " + host + ":" + port
                    + " — " + e.getMessage());
        }
    }
}
