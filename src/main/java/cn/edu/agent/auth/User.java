package cn.edu.agent.auth;

import lombok.Data;
import java.time.Instant;

/**
 * User entity for authentication system
 */
@Data
public class User {
    private String username;
    private String passwordHash;
    private String email;
    private boolean active;
    private Instant createdAt;
    private Instant lastLoginAt;

    public User() {
        this.active = true;
        this.createdAt = Instant.now();
    }

    public User(String username, String passwordHash, String email) {
        this();
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
    }

    /**
     * Returns a safe representation without password hash
     */
    public String toSafeString() {
        return String.format("User{username='%s', email='%s', active=%s, createdAt=%s, lastLoginAt=%s}",
                username, email, active, createdAt, lastLoginAt);
    }
}
