package cn.edu.agent.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-based repository for user storage
 * Thread-safe implementation using ConcurrentHashMap
 */
public class UserRepository {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Path storageFile;
    private final ObjectMapper objectMapper;

    public UserRepository(String storagePath) {
        this.storageFile = Paths.get(storagePath);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        loadUsers();
    }

    /**
     * Load users from file
     */
    private void loadUsers() {
        if (!Files.exists(storageFile)) {
            return;
        }

        try {
            String json = Files.readString(storageFile);
            Map<String, User> loadedUsers = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, User.class));
            users.putAll(loadedUsers);
        } catch (IOException e) {
            System.err.println("[UserRepository] Failed to load users: " + e.getMessage());
        }
    }

    /**
     * Save users to file
     */
    private synchronized void saveUsers() {
        try {
            Files.createDirectories(storageFile.getParent());
            String json = objectMapper.writeValueAsString(users);
            Files.writeString(storageFile, json);
        } catch (IOException e) {
            System.err.println("[UserRepository] Failed to save users: " + e.getMessage());
        }
    }

    /**
     * Create a new user
     */
    public boolean createUser(User user) {
        if (users.containsKey(user.getUsername())) {
            return false;
        }
        users.put(user.getUsername(), user);
        saveUsers();
        return true;
    }

    /**
     * Find user by username
     */
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    /**
     * Update user
     */
    public boolean updateUser(User user) {
        if (!users.containsKey(user.getUsername())) {
            return false;
        }
        users.put(user.getUsername(), user);
        saveUsers();
        return true;
    }

    /**
     * Delete user
     */
    public boolean deleteUser(String username) {
        User removed = users.remove(username);
        if (removed != null) {
            saveUsers();
            return true;
        }
        return false;
    }

    /**
     * Check if user exists
     */
    public boolean exists(String username) {
        return users.containsKey(username);
    }

    /**
     * Get total user count
     */
    public int count() {
        return users.size();
    }
}
