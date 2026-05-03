package cn.edu.agent.auth;

import cn.edu.agent.tool.impl.LoginTool;
import cn.edu.agent.tool.impl.LogoutTool;
import cn.edu.agent.tool.impl.RegisterTool;
import cn.edu.agent.tool.impl.ValidateSessionTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for authentication tools
 */
class AuthenticationToolsTest {
    private AuthenticationService authService;
    private RegisterTool registerTool;
    private LoginTool loginTool;
    private LogoutTool logoutTool;
    private ValidateSessionTool validateSessionTool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Path storageFile = tempDir.resolve("test_users.json");
        UserRepository userRepository = new UserRepository(storageFile.toString());
        authService = new AuthenticationService(userRepository);
        
        registerTool = new RegisterTool(authService);
        loginTool = new LoginTool(authService);
        logoutTool = new LogoutTool(authService);
        validateSessionTool = new ValidateSessionTool(authService);
    }

    @Test
    void testRegisterToolSuccess() {
        Map<String, Object> input = new HashMap<>();
        input.put("username", "alice");
        input.put("password", "password123");
        input.put("email", "alice@example.com");
        
        String result = registerTool.execute(input);
        
        assertThat(result).contains("Successfully registered");
        assertThat(result).contains("alice");
    }

    @Test
    void testRegisterToolDuplicate() {
        Map<String, Object> input = new HashMap<>();
        input.put("username", "alice");
        input.put("password", "password123");
        input.put("email", "alice@example.com");
        
        registerTool.execute(input);
        String result = registerTool.execute(input);
        
        assertThat(result).contains("Failed to register");
    }

    @Test
    void testRegisterToolMissingUsername() {
        Map<String, Object> input = new HashMap<>();
        input.put("password", "password123");
        input.put("email", "alice@example.com");
        
        String result = registerTool.execute(input);
        
        assertThat(result).contains("Error");
        assertThat(result).contains("Username is required");
    }

    @Test
    void testLoginToolSuccess() {
        // Register first
        Map<String, Object> registerInput = new HashMap<>();
        registerInput.put("username", "alice");
        registerInput.put("password", "password123");
        registerInput.put("email", "alice@example.com");
        registerTool.execute(registerInput);
        
        // Then login
        Map<String, Object> loginInput = new HashMap<>();
        loginInput.put("username", "alice");
        loginInput.put("password", "password123");
        
        String result = loginTool.execute(loginInput);
        
        assertThat(result).contains("Login successful");
        assertThat(result).contains("Session ID:");
    }

    @Test
    void testLoginToolWrongPassword() {
        // Register first
        Map<String, Object> registerInput = new HashMap<>();
        registerInput.put("username", "alice");
        registerInput.put("password", "password123");
        registerInput.put("email", "alice@example.com");
        registerTool.execute(registerInput);
        
        // Try login with wrong password
        Map<String, Object> loginInput = new HashMap<>();
        loginInput.put("username", "alice");
        loginInput.put("password", "wrongpassword");
        
        String result = loginTool.execute(loginInput);
        
        assertThat(result).contains("Login failed");
    }

    @Test
    void testLogoutToolSuccess() {
        // Register and login
        authService.register("alice", "password123", "alice@example.com");
        Session session = authService.login("alice", "password123").orElseThrow();
        
        // Logout
        Map<String, Object> input = new HashMap<>();
        input.put("session_id", session.getSessionId());
        
        String result = logoutTool.execute(input);
        
        assertThat(result).contains("Successfully logged out");
    }

    @Test
    void testLogoutToolInvalidSession() {
        Map<String, Object> input = new HashMap<>();
        input.put("session_id", "invalid-session-id");
        
        String result = logoutTool.execute(input);
        
        assertThat(result).contains("Failed to logout");
    }

    @Test
    void testValidateSessionToolSuccess() {
        // Register and login
        authService.register("alice", "password123", "alice@example.com");
        Session session = authService.login("alice", "password123").orElseThrow();
        
        // Validate
        Map<String, Object> input = new HashMap<>();
        input.put("session_id", session.getSessionId());
        
        String result = validateSessionTool.execute(input);
        
        assertThat(result).contains("Session is valid");
        assertThat(result).contains("alice");
    }

    @Test
    void testValidateSessionToolInvalid() {
        Map<String, Object> input = new HashMap<>();
        input.put("session_id", "invalid-session-id");
        
        String result = validateSessionTool.execute(input);
        
        assertThat(result).contains("Session is invalid or expired");
    }

    @Test
    void testCompleteAuthenticationFlow() {
        // 1. Register
        Map<String, Object> registerInput = new HashMap<>();
        registerInput.put("username", "alice");
        registerInput.put("password", "password123");
        registerInput.put("email", "alice@example.com");
        String registerResult = registerTool.execute(registerInput);
        assertThat(registerResult).contains("Successfully registered");
        
        // 2. Login
        Map<String, Object> loginInput = new HashMap<>();
        loginInput.put("username", "alice");
        loginInput.put("password", "password123");
        String loginResult = loginTool.execute(loginInput);
        assertThat(loginResult).contains("Login successful");
        
        // Extract session ID from login result
        String sessionId = loginResult.split("Session ID: ")[1].split(" ")[0];
        
        // 3. Validate session
        Map<String, Object> validateInput = new HashMap<>();
        validateInput.put("session_id", sessionId);
        String validateResult = validateSessionTool.execute(validateInput);
        assertThat(validateResult).contains("Session is valid");
        
        // 4. Logout
        Map<String, Object> logoutInput = new HashMap<>();
        logoutInput.put("session_id", sessionId);
        String logoutResult = logoutTool.execute(logoutInput);
        assertThat(logoutResult).contains("Successfully logged out");
        
        // 5. Validate again (should fail)
        String validateResult2 = validateSessionTool.execute(validateInput);
        assertThat(validateResult2).contains("Session is invalid or expired");
    }

    @Test
    void testToolSchemas() {
        assertThat(registerTool.getName()).isEqualTo("register");
        assertThat(loginTool.getName()).isEqualTo("login");
        assertThat(logoutTool.getName()).isEqualTo("logout");
        assertThat(validateSessionTool.getName()).isEqualTo("validate_session");
        
        assertThat(registerTool.getInputSchema()).containsKey("properties");
        assertThat(loginTool.getInputSchema()).containsKey("properties");
        assertThat(logoutTool.getInputSchema()).containsKey("properties");
        assertThat(validateSessionTool.getInputSchema()).containsKey("properties");
    }
}
