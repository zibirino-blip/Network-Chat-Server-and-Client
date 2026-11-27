package server;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket / sliding-window rate limiter.
 * Tracks how many events (login attempts, messages) an IP address has
 * produced within a rolling time window.
 */
public class RateLimiter {

    /** Sliding window for login attempts: max 5 tries in 30 seconds. */
    private static final int    LOGIN_MAX_ATTEMPTS  = 5;
    private static final long   LOGIN_WINDOW_MILLIS = 30_000;

    /** Sliding window for messages: max 20 messages in 5 seconds. */
    private static final int    MSG_MAX_ATTEMPTS    = 20;
    private static final long   MSG_WINDOW_MILLIS   = 5_000;

    // ip → timestamps of recent login attempts
    private final ConcurrentHashMap<String, Deque<Long>> loginTimestamps   = new ConcurrentHashMap<>();
    // ip → timestamps of recent messages
    private final ConcurrentHashMap<String, Deque<Long>> messageTimestamps = new ConcurrentHashMap<>();

    // Public API 

    /**
     * Records a login attempt from the given IP.
     * @return false if the IP is currently rate-limited (too many attempts).
     */
    public synchronized boolean allowLoginAttempt(String ip) {
        return allow(ip, loginTimestamps, LOGIN_MAX_ATTEMPTS, LOGIN_WINDOW_MILLIS);
    }

    /**
     * Records a message event from the given IP.
     * @return false if the IP is sending messages too rapidly.
     */
    public synchronized boolean allowMessage(String ip) {
        return allow(ip, messageTimestamps, MSG_MAX_ATTEMPTS, MSG_WINDOW_MILLIS);
    }

    //  Core sliding-window logic 

    private boolean allow(String key,
                          ConcurrentHashMap<String, Deque<Long>> store,
                          int maxAttempts, long windowMillis) {
        long now = Instant.now().toEpochMilli();

        Deque<Long> timestamps = store.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Remove timestamps that have fallen outside the window
        while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > windowMillis) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxAttempts) {
            return false; // rate limit exceeded
        }

        timestamps.addLast(now);
        return true;
    }
}
