package cn.edu.agent.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication service handling user login, logout, and session management
 * Uses PBKDF2 for password hashing
 */
public class AuthenticationService {
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final long DEFAULT_SESSION_DURATION = 3600; // 1 hour in seconds

    private final UserRepository userRepository;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Register a new user
     */
    public boolean register(String username, String password, String email) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }

        if (userRepository.exists(username)) {
            return false;
        }

        String passwordHash = hashPassword(password);
        User user = new User(username, passwordHash, email);
        return userRepository.createUser(user);
    }

    /**
     * Authenticate user and create session
     */
    public Optional<Session> login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            return Optional.empty();
        }

        if (!verifyPassword(password, user.getPasswordHash())) {
            return Optional.empty();
        }

        // Update last login time
        user.setLastLoginAt(Instant.now());
        userRepository.updateUser(user);

        // Create session
        Session session = new Session(username, DEFAULT_SESSION_DURATION);
        sessions.put(session.getSessionId(), session);

        // Clean up expired sessions
        cleanupExpiredSessions();

        return Optional.of(session);
    }

    /**
     * Logout user by invalidating session
     */
    public boolean logout(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.invalidate();
            sessions.remove(sessionId);
            return true;
        }
        return false;
    }

    /**
     * Validate session
     */
    public boolean validateSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }

        if (!session.isValid()) {
            sessions.remove(sessionId);
            return false;
        }

        return true;
    }

    /**
     * Get session by ID
     */
    public Optional<Session> getSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session != null && session.isValid()) {
            return Optional.of(session);
        }
        return Optional.empty();
    }

    /**
     * Get user from session
     */
    public Optional<User> getUserFromSession(String sessionId) {
        return getSession(sessionId)
                .flatMap(session -> userRepository.findByUsername(session.getUsername()));
    }

    /**
     * Hash password using PBKDF2
     */
    private String hashPassword(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        byte[] hash = pbkdf2(password, salt);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hashBase64 = Base64.getEncoder().encodeToString(hash);

        return saltBase64 + ":" + hashBase64;
    }

    /**
     * Verify password against hash
     */
    private boolean verifyPassword(String password, String storedHash) {
        String[] parts = storedHash.split(":");
        if (parts.length != 2) {
            return false;
        }

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
        byte[] actualHash = pbkdf2(password, salt);

        return constantTimeEquals(expectedHash, actualHash);
    }

    /**
     * PBKDF2 key derivation
     */
    private byte[] pbkdf2(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    /**
     * Constant-time comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * Clean up expired sessions
     */
    private void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> !entry.getValue().isValid());
    }

    /**
     * Get active session count
     */
    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return sessions.size();
    }
}
