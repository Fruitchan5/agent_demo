package cn.edu.agent.auth;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

/**
 * Session entity for tracking authenticated users
 */
@Data
public class Session {
    private String sessionId;
    private String username;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean active;

    public Session() {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.active = true;
    }

    public Session(String username, long durationSeconds) {
        this();
        this.username = username;
        this.expiresAt = Instant.now().plusSeconds(durationSeconds);
    }

    /**
     * Check if session is still valid
     */
    public boolean isValid() {
        return active && Instant.now().isBefore(expiresAt);
    }

    /**
     * Invalidate this session
     */
    public void invalidate() {
        this.active = false;
    }
}
