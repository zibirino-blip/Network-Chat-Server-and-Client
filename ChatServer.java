package server;

import shared.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for the chat server.
 */
public class ChatServer {

    private static final int DEFAULT_PORT = 5000;

    // username → handler  (thread-safe; read/written from many handler threads)
    private final ConcurrentHashMap<String, ClientHandler> activeClients =
            new ConcurrentHashMap<>();

    // Shared services – one instance, used by all handlers
    private final AuthManager    authManager    = new AuthManager();
    private final RateLimiter    rateLimiter    = new RateLimiter();
    private final SecurityLogger securityLogger = new SecurityLogger();

    // Thread pool: one thread per active client connection
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    private final int port;

    public ChatServer(int port) {
        this.port = port;
    }

    //  Start 

    public void start() {
        System.out.println("===========================");
        System.out.println(" ||  Chat Server v1.0  || ");
        System.out.println("===========================");
        System.out.println("Listening on port " + port + "...\n");

        // Register a demo user so testers can log in immediately
        authManager.register("admin", "admin123");
        authManager.register("alice", "alice123");
        authManager.register("bob",   "bob123");

        // Add shutdown hook for graceful termination (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutting down...");
            threadPool.shutdownNow();
        }));

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept(); // blocks until connection
                ClientHandler handler = new ClientHandler(
                        clientSocket, activeClients, authManager,
                        rateLimiter, securityLogger);
                threadPool.execute(handler); // dispatch to thread pool
            }
        } catch (IOException e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
        }
    }

    //  Main 

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port. Using default " + DEFAULT_PORT);
            }
        }
        new ChatServer(port).start();
    }
}
