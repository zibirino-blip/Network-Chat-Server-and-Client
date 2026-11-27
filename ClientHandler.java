package server;

import shared.Message;
import shared.Message.Type;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all communication with a single connected client.
 */
public class ClientHandler implements Runnable {

    //  Shared state (injected from ChatServer) 
    private final ConcurrentHashMap<String, ClientHandler> activeClients;
    private final AuthManager    auth;
    private final RateLimiter    rateLimiter;
    private final SecurityLogger logger;

    //  Per-client state 
    private final Socket socket;
    private final String clientIp;
    private String username = null;   // null until authenticated

    private ObjectOutputStream out;
    private ObjectInputStream  in;

    //  Constants     
    
    private static final int MAX_AUTH_ATTEMPTS = 3;

    public ClientHandler(Socket socket,
                         ConcurrentHashMap<String, ClientHandler> activeClients,
                         AuthManager auth,
                         RateLimiter rateLimiter,
                         SecurityLogger logger) {
        this.socket        = socket;
        this.activeClients = activeClients;
        this.auth          = auth;
        this.rateLimiter   = rateLimiter;
        this.logger        = logger;
        this.clientIp      = socket.getInetAddress().getHostAddress();
    }

    //  Main run loop 

    @Override
    public void run() {
        logger.logConnection(clientIp);
        try {
            // Streams must be opened in this order (output first) to avoid deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());

            if (!authenticate()) {
                closeConnection();
                return;
            }

            send(serverMsg("Welcome, " + username + "! Type /help for commands."));
            broadcast(serverMsg(username + " has joined the chat."), true);
            activeClients.put(username, this);

            //  Main message loop 
            Message msg;
            while ((msg = receive()) != null) {
                handleMessage(msg);
            }

        } catch (EOFException | java.net.SocketException ignored) {
            // Client closed the connection
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[ClientHandler] Error with " + clientIp + ": " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    //  Authentication phase 

    /**
     * Runs the LOGIN / REGISTER handshake.
     * The client must authenticate within MAX_AUTH_ATTEMPTS tries.
     */
    private boolean authenticate() throws IOException, ClassNotFoundException {
        send(serverMsg("Please LOGIN or REGISTER to continue."));
        int attempts = 0;

        while (attempts < MAX_AUTH_ATTEMPTS) {
            Message msg = receive();
            if (msg == null) return false;

            String[] parts = msg.getPayload().split(":", 2);
            if (parts.length != 2) {
                send(errorMsg("Invalid format. Use username:password"));
                continue;
            }
            String user = parts[0].trim();
            String pass = parts[1];

            if (msg.getType() == Type.REGISTER) {
                if (auth.register(user, pass)) {
                    logger.logRegistration(user, clientIp);
                    username = user;
                    send(serverMsg("Registration successful. You are now logged in."));
                    return true;
                } else {
                    send(errorMsg("Username '" + user + "' is already taken."));
                }

            } else if (msg.getType() == Type.LOGIN) {
                if (!rateLimiter.allowLoginAttempt(clientIp)) {
                    logger.logRateLimited(clientIp, "login");
                    send(errorMsg("Too many login attempts. Please wait 30 seconds."));
                    return false;
                }
                if (auth.login(user, pass)) {
                    if (activeClients.containsKey(user)) {
                        send(errorMsg("User '" + user + "' is already logged in."));
                    } else {
                        logger.logLoginSuccess(user, clientIp);
                        username = user;
                        send(serverMsg("Login successful."));
                        return true;
                    }
                } else {
                    attempts++;
                    logger.logLoginFailure(user, clientIp);
                    send(errorMsg("Invalid credentials. " +
                            (MAX_AUTH_ATTEMPTS - attempts) + " attempt(s) remaining."));
                }
            } else {
                send(errorMsg("Please LOGIN or REGISTER first."));
            }
        }

        send(errorMsg("Maximum authentication attempts reached. Disconnecting."));
        return false;
    }

    //  Message dispatch 

    private void handleMessage(Message msg) throws IOException {
        switch (msg.getType()) {
            case BROADCAST -> handleBroadcast(msg);
            case DM        -> handleDirectMessage(msg);
            case LIST      -> handleList();
            case QUIT      -> {
                send(serverMsg("Goodbye, " + username + "!"));
                disconnect();
            }
            default        -> send(errorMsg("Unknown command type."));
        }
    }

    /** Sends a message to every connected client (excluding or including self). */
    private void handleBroadcast(Message msg) throws IOException {
        if (!rateLimiter.allowMessage(clientIp)) {
            logger.logRateLimited(clientIp, "message flood");
            send(errorMsg("You are sending messages too quickly. Slow down."));
            return;
        }
        Message outMsg = new Message(Type.BROADCAST, username, msg.getPayload());
        broadcast(outMsg, false);
    }

    /**
     * Sends a private message.
     * Payload format: "targetUsername:message text"
     */
    private void handleDirectMessage(Message msg) throws IOException {
        String[] parts = msg.getPayload().split(":", 2);
        if (parts.length < 2) {
            send(errorMsg("DM format: /dm targetUser:your message"));
            return;
        }
        String target  = parts[0].trim();
        String content = parts[1];

        ClientHandler targetHandler = activeClients.get(target);
        if (targetHandler == null) {
            send(errorMsg("User '" + target + "' is not online."));
            return;
        }
        Message dm = new Message(Type.DM, username, content);
        targetHandler.send(dm);
        // Echo confirmation to sender
        send(new Message(Type.DM, "→ " + target, content));
    }

    /** Responds with a list of currently online users. */
    private void handleList() throws IOException {
        String userList = String.join(", ", activeClients.keySet());
        send(serverMsg("Online users: " + userList));
    }

    //  Broadcast helper 

    /**
     * Delivers a message to all active clients.
     * @param excludeSelf if true, the originating client does not receive it.
     */
    void broadcast(Message msg, boolean excludeSelf) {
        for (ClientHandler handler : activeClients.values()) {
            if (excludeSelf && handler == this) continue;
            try {
                handler.send(msg);
            } catch (IOException e) {
                System.err.println("[ClientHandler] Could not deliver to "
                        + handler.username + ": " + e.getMessage());
            }
        }
    }

    //  I/O helpers 

    /** Thread-safe write: serialises the Message object over the socket. */
    synchronized void send(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
        out.reset(); // prevent object-cache issues with repeated sends
    }

    private Message receive() throws IOException, ClassNotFoundException {
        return (Message) in.readObject();
    }

    //  Cleanup 

    private void disconnect() {
        if (username != null) {
            activeClients.remove(username);
            logger.logDisconnect(username, clientIp);
            broadcast(serverMsg(username + " has left the chat."), false);
        }
        closeConnection();
    }

    private void closeConnection() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    // Message factories 
    private Message serverMsg(String text) { return new Message(Type.SERVER, "SERVER", text); }
    private Message errorMsg(String text)  { return new Message(Type.ERROR,  "SERVER", text); }
}
