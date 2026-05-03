# Authentication Module Documentation

## Overview

The authentication module provides a complete user authentication system with login/logout functionality, session management, and secure password hashing. It integrates seamlessly with the existing agent tool system.

## Architecture

### Components

1. **User** - Entity representing a user account with username, email, password hash, and metadata
2. **Session** - Entity representing an authenticated session with expiration tracking
3. **UserRepository** - File-based storage for user data with JSON persistence
4. **AuthenticationService** - Core service handling registration, login, logout, and session validation
5. **Authentication Tools** - Four tools that expose authentication functionality to the agent

### Security Features

- **PBKDF2 Password Hashing**: Uses PBKDF2WithHmacSHA256 with 10,000 iterations and 256-bit key length
- **Salt Generation**: Each password gets a unique 16-byte random salt
- **Constant-Time Comparison**: Prevents timing attacks during password verification
- **Session Expiration**: Sessions automatically expire after 1 hour
- **Thread-Safe**: All operations are thread-safe using ConcurrentHashMap

## Tools

### 1. register

Register a new user account.

**Parameters:**
- `username` (string, required): Username for the new account
- `password` (string, required): Password for the new account
- `email` (string, required): Email address for the new account

**Example:**
```json
{
  "username": "alice",
  "password": "securePassword123",
  "email": "alice@example.com"
}
```

**Returns:**
- Success: "Successfully registered user 'alice' with email 'alice@example.com'"
- Failure: "Failed to register user 'alice'. Username may already exist."

### 2. login

Authenticate a user and create a session.

**Parameters:**
- `username` (string, required): Username to authenticate
- `password` (string, required): Password for authentication

**Example:**
```json
{
  "username": "alice",
  "password": "securePassword123"
}
```

**Returns:**
- Success: "Login successful for user 'alice'. Session ID: {uuid} (expires at {timestamp})"
- Failure: "Login failed for user 'alice'. Invalid credentials or account inactive."

### 3. logout

Invalidate a user session.

**Parameters:**
- `session_id` (string, required): Session ID to invalidate

**Example:**
```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Returns:**
- Success: "Successfully logged out session: {session_id}"
- Failure: "Failed to logout. Session not found: {session_id}"

### 4. validate_session

Validate a session and retrieve user information.

**Parameters:**
- `session_id` (string, required): Session ID to validate

**Example:**
```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Returns:**
- Success: Detailed session and user information
- Failure: "Session is invalid or expired: {session_id}"

## Data Storage

User data is stored in `.auth/users.json` in JSON format:

```json
{
  "alice": {
    "username": "alice",
    "passwordHash": "base64salt:base64hash",
    "email": "alice@example.com",
    "active": true,
    "createdAt": "2026-05-02T12:00:00Z",
    "lastLoginAt": "2026-05-02T12:30:00Z"
  }
}
```

## Integration

The authentication tools are automatically registered in `ToolRegistry` as base tools, making them available to both parent and child agents.

### ToolRegistry Integration

```java
// In ToolRegistry constructor
UserRepository userRepository = new UserRepository(".auth/users.json");
AuthenticationService authenticationService = new AuthenticationService(userRepository);

registerBase(new LoginTool(authenticationService));
registerBase(new LogoutTool(authenticationService));
registerBase(new RegisterTool(authenticationService));
registerBase(new ValidateSessionTool(authenticationService));
```

## Usage Example

```java
// Create authentication service
UserRepository repo = new UserRepository(".auth/users.json");
AuthenticationService authService = new AuthenticationService(repo);

// Register a user
boolean registered = authService.register("alice", "password123", "alice@example.com");

// Login
Optional<Session> session = authService.login("alice", "password123");
if (session.isPresent()) {
    String sessionId = session.get().getSessionId();
    
    // Validate session
    boolean isValid = authService.validateSession(sessionId);
    
    // Get user from session
    Optional<User> user = authService.getUserFromSession(sessionId);
    
    // Logout
    authService.logout(sessionId);
}
```

## Testing

The module includes comprehensive test coverage:

- **UserRepositoryTest** (11 tests): Tests file-based storage, CRUD operations, and persistence
- **AuthenticationServiceTest** (17 tests): Tests registration, login, logout, session management, and security features
- **AuthenticationToolsTest** (11 tests): Tests tool integration and complete authentication flows

**Total: 39 tests, all passing**

Run tests with:
```bash
mvn test -Dtest=AuthenticationServiceTest,UserRepositoryTest,AuthenticationToolsTest
```

## Security Considerations

1. **Password Storage**: Passwords are never stored in plain text. Only salted PBKDF2 hashes are persisted.
2. **Session Management**: Sessions expire after 1 hour and can be manually invalidated.
3. **Thread Safety**: All operations are thread-safe for concurrent access.
4. **Timing Attacks**: Constant-time comparison prevents timing-based password guessing.
5. **File Permissions**: Ensure `.auth/users.json` has appropriate file permissions in production.

## Future Enhancements

Potential improvements for production use:

1. Password complexity requirements
2. Account lockout after failed login attempts
3. Password reset functionality
4. Email verification
5. Two-factor authentication
6. Session refresh tokens
7. Audit logging
8. Database backend option
9. Role-based access control
10. OAuth2/OpenID Connect integration

## Dependencies

- Jackson Databind 2.17.0 (JSON serialization)
- Jackson Datatype JSR310 2.17.0 (Java 8 time support)
- Lombok 1.18.32 (boilerplate reduction)
- JUnit 5.10.2 (testing)
- AssertJ 3.25.3 (test assertions)
