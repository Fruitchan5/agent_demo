package cn.edu.agent.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for AuthenticationService
 */
class AuthenticationServiceTest {
    private AuthenticationService authService;
    private UserRepository userRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Path storageFile = tempDir.resolve("test_users.json");
        userRepository = new UserRepository(storageFile.toString());
        authService = new AuthenticationService(userRepository);
    }

    @Test
    void testRegisterNewUser() {
        boolean result = authService.register("alice", "password123", "alice@example.com");
        
        assertThat(result).isTrue();
        assertThat(userRepository.exists("alice")).isTrue();
    }

    @Test
    void testRegisterDuplicateUser() {
        authService.register("alice", "password123", "alice@example.com");
        boolean result = authService.register("alice", "different", "alice2@example.com");
        
        assertThat(result).isFalse();
    }

    @Test
    void testRegisterWithBlankUsername() {
        boolean result = authService.register("", "password123", "test@example.com");
        
        assertThat(result).isFalse();
    }

    @Test
    void testRegisterWithBlankPassword() {
        boolean result = authService.register("alice", "", "alice@example.com");
        
        assertThat(result).isFalse();
    }

    @Test
    void testLoginSuccess() {
        authService.register("alice", "password123", "alice@example.com");
        Optional<Session> sessionOpt = authService.login("alice", "password123");
        
        assertThat(sessionOpt).isPresent();
        Session session = sessionOpt.get();
        assertThat(session.getUsername()).isEqualTo("alice");
        assertThat(session.isValid()).isTrue();
    }

    @Test
    void testLoginWithWrongPassword() {
        authService.register("alice", "password123", "alice@example.com");
        Optional<Session> sessionOpt = authService.login("alice", "wrongpassword");
        
        assertThat(sessionOpt).isEmpty();
    }

    @Test
    void testLoginNonExistentUser() {
        Optional<Session> sessionOpt = authService.login("nonexistent", "password123");
        
        assertThat(sessionOpt).isEmpty();
    }

    @Test
    void testLogoutSuccess() {
        authService.register("alice", "password123", "alice@example.com");
        Session session = authService.login("alice", "password123").orElseThrow();
        
        boolean result = authService.logout(session.getSessionId());
        
        assertThat(result).isTrue();
        assertThat(authService.validateSession(session.getSessionId())).isFalse();
    }

    @Test
    void testLogoutInvalidSession() {
        boolean result = authService.logout("invalid-session-id");
        
        assertThat(result).isFalse();
    }

    @Test
    void testValidateSessionSuccess() {
        authService.register("alice", "password123", "alice@example.com");
        Session session = authService.login("alice", "password123").orElseThrow();
        
        boolean isValid = authService.validateSession(session.getSessionId());
        
        assertThat(isValid).isTrue();
    }

    @Test
    void testValidateSessionInvalid() {
        boolean isValid = authService.validateSession("invalid-session-id");
        
        assertThat(isValid).isFalse();
    }

    @Test
    void testGetSessionSuccess() {
        authService.register("alice", "password123", "alice@example.com");
        Session originalSession = authService.login("alice", "password123").orElseThrow();
        
        Optional<Session> sessionOpt = authService.getSession(originalSession.getSessionId());
        
        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().getSessionId()).isEqualTo(originalSession.getSessionId());
    }

    @Test
    void testGetUserFromSession() {
        authService.register("alice", "password123", "alice@example.com");
        Session session = authService.login("alice", "password123").orElseThrow();
        
        Optional<User> userOpt = authService.getUserFromSession(session.getSessionId());
        
        assertThat(userOpt).isPresent();
        User user = userOpt.get();
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void testMultipleConcurrentSessions() {
        authService.register("alice", "password123", "alice@example.com");
        authService.register("bob", "password456", "bob@example.com");
        
        Session aliceSession = authService.login("alice", "password123").orElseThrow();
        Session bobSession = authService.login("bob", "password456").orElseThrow();
        
        assertThat(authService.validateSession(aliceSession.getSessionId())).isTrue();
        assertThat(authService.validateSession(bobSession.getSessionId())).isTrue();
        assertThat(authService.getActiveSessionCount()).isEqualTo(2);
    }

    @Test
    void testPasswordHashing() {
        authService.register("alice", "password123", "alice@example.com");
        
        Optional<User> userOpt = userRepository.findByUsername("alice");
        assertThat(userOpt).isPresent();
        
        User user = userOpt.get();
        // Password should be hashed, not stored in plain text
        assertThat(user.getPasswordHash()).isNotEqualTo("password123");
        assertThat(user.getPasswordHash()).contains(":");
    }

    @Test
    void testLastLoginTimestamp() throws InterruptedException {
        authService.register("alice", "password123", "alice@example.com");
        
        User userBefore = userRepository.findByUsername("alice").orElseThrow();
        assertThat(userBefore.getLastLoginAt()).isNull();
        
        Thread.sleep(10); // Small delay to ensure timestamp difference
        authService.login("alice", "password123");
        
        User userAfter = userRepository.findByUsername("alice").orElseThrow();
        assertThat(userAfter.getLastLoginAt()).isNotNull();
    }

    @Test
    void testActiveSessionCount() {
        authService.register("alice", "password123", "alice@example.com");
        
        assertThat(authService.getActiveSessionCount()).isEqualTo(0);
        
        Session session1 = authService.login("alice", "password123").orElseThrow();
        assertThat(authService.getActiveSessionCount()).isEqualTo(1);
        
        Session session2 = authService.login("alice", "password123").orElseThrow();
        assertThat(authService.getActiveSessionCount()).isEqualTo(2);
        
        authService.logout(session1.getSessionId());
        assertThat(authService.getActiveSessionCount()).isEqualTo(1);
    }
}
