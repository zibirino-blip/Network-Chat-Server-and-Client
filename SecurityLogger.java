package server;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logs security-relevant events to both the console and a persistent
 * log file (security.log). Events include:
 *   - Failed login attempts
 *   - Rate-limit triggers
 *   - New connections / disconnections
 *   - Successful authentications
 */
public class SecurityLogger {

    private static final String LOG_FILE = "security.log";
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrintWriter fileWriter;

    public SecurityLogger() {
        PrintWriter writer;
        try {
            // append=true so log survives server restarts
            writer = new PrintWriter(new FileWriter(LOG_FILE, true), true);
        } catch (IOException e) {
            System.err.println("[SecurityLogger] Could not open log file: " + e.getMessage());
            writer = null;
        }
        this.fileWriter = writer;
    }

    // Convenience methods 

    public void logConnection(String ip) {
        write("CONNECTION", ip, "New TCP connection");
    }

    public void logLoginSuccess(String username, String ip) {
        write("AUTH_OK", ip, "User '" + username + "' authenticated");
    }

    public void logLoginFailure(String username, String ip) {
        write("AUTH_FAIL", ip, "Failed login attempt for user '" + username + "'");
    }

    public void logRegistration(String username, String ip) {
        write("REGISTER", ip, "New user registered: '" + username + "'");
    }

    public void logRateLimited(String ip, String context) {
        write("RATE_LIMIT", ip, "Rate limit triggered [" + context + "]");
    }

    public void logDisconnect(String username, String ip) {
        write("DISCONNECT", ip, "User '" + username + "' disconnected");
    }

    public void logWarning(String ip, String detail) {
        write("WARNING", ip, detail);
    }

    //  Core write 

    private void write(String event, String ip, String detail) {
        String line = String.format("[%s] %-12s %-20s %s",
                LocalDateTime.now().format(FMT), event, ip, detail);
        System.out.println(line);
        if (fileWriter != null) {
            fileWriter.println(line);
        }
    }
}
