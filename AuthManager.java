package server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user registration and login.
 */
public class AuthManager {

    // username → "saltB64:hashB64"
    private final ConcurrentHashMap<String, String> userStore = new ConcurrentHashMap<>();

    private final SecureRandom random = new SecureRandom();

    //  Registration 

    /**
     * Registers a new user.
     */
    public boolean register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.length() < 4) {
            return false;
        }
        // putIfAbsent returns null only if the key was not already present
        String hashed = hashPassword(password);
        return userStore.putIfAbsent(username.toLowerCase(), hashed) == null;
    }

    //  Login 

    /**
     * Verifies credentials.
     */
    public boolean login(String username, String password) {
        if (username == null || password == null) return false;
        String stored = userStore.get(username.toLowerCase());
        if (stored == null) return false;
        return verifyPassword(password, stored);
    }

    //  Helpers
    /** Produces "saltB64:hashB64" from a plain-text password. */
    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = sha256(salt, password);
        return Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Re-derives the hash using the salt embedded in storedValue and
     * compares it in constant time to prevent timing attacks.
     */
    private boolean verifyPassword(String password, String storedValue) {
        try {
            String[] parts = storedValue.split(":", 2);
            byte[] salt  = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            byte[] actual   = sha256(salt, password);
            return MessageDigest.isEqual(expected, actual); // constant-time compare
        } catch (Exception e) {
            return false;
        }
    }

    /** SHA-256 */
    private byte[] sha256(byte[] salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
