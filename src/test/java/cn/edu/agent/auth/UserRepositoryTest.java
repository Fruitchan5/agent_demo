package cn.edu.agent.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for UserRepository file-based storage
 */
class UserRepositoryTest {
    private UserRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Path storageFile = tempDir.resolve("users.json");
        repository = new UserRepository(storageFile.toString());
    }

    @Test
    void testCreateUser() {
        User user = new User("alice", "hash123", "alice@example.com");
        boolean result = repository.createUser(user);
        
        assertThat(result).isTrue();
        assertThat(repository.exists("alice")).isTrue();
    }

    @Test
    void testCreateDuplicateUser() {
        User user1 = new User("alice", "hash123", "alice@example.com");
        User user2 = new User("alice", "hash456", "alice2@example.com");
        
        repository.createUser(user1);
        boolean result = repository.createUser(user2);
        
        assertThat(result).isFalse();
    }

    @Test
    void testFindByUsername() {
        User user = new User("alice", "hash123", "alice@example.com");
        repository.createUser(user);
        
        Optional<User> found = repository.findByUsername("alice");
        
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void testFindNonExistentUser() {
        Optional<User> found = repository.findByUsername("nonexistent");
        
        assertThat(found).isEmpty();
    }

    @Test
    void testUpdateUser() {
        User user = new User("alice", "hash123", "alice@example.com");
        repository.createUser(user);
        
        user.setEmail("newemail@example.com");
        boolean result = repository.updateUser(user);
        
        assertThat(result).isTrue();
        Optional<User> updated = repository.findByUsername("alice");
        assertThat(updated).isPresent();
        assertThat(updated.get().getEmail()).isEqualTo("newemail@example.com");
    }

    @Test
    void testUpdateNonExistentUser() {
        User user = new User("alice", "hash123", "alice@example.com");
        boolean result = repository.updateUser(user);
        
        assertThat(result).isFalse();
    }

    @Test
    void testDeleteUser() {
        User user = new User("alice", "hash123", "alice@example.com");
        repository.createUser(user);
        
        boolean result = repository.deleteUser("alice");
        
        assertThat(result).isTrue();
        assertThat(repository.exists("alice")).isFalse();
    }

    @Test
    void testDeleteNonExistentUser() {
        boolean result = repository.deleteUser("nonexistent");
        
        assertThat(result).isFalse();
    }

    @Test
    void testCount() {
        assertThat(repository.count()).isEqualTo(0);
        
        repository.createUser(new User("alice", "hash1", "alice@example.com"));
        assertThat(repository.count()).isEqualTo(1);
        
        repository.createUser(new User("bob", "hash2", "bob@example.com"));
        assertThat(repository.count()).isEqualTo(2);
        
        repository.deleteUser("alice");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void testPersistence() {
        Path storageFile = tempDir.resolve("persistent_users.json");
        UserRepository repo1 = new UserRepository(storageFile.toString());
        
        User user = new User("alice", "hash123", "alice@example.com");
        repo1.createUser(user);
        
        // Create new repository instance with same file
        UserRepository repo2 = new UserRepository(storageFile.toString());
        
        Optional<User> found = repo2.findByUsername("alice");
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
    }

    @Test
    void testFileCreation() {
        Path storageFile = tempDir.resolve("subdir/users.json");
        UserRepository repo = new UserRepository(storageFile.toString());
        
        User user = new User("alice", "hash123", "alice@example.com");
        repo.createUser(user);
        
        assertThat(Files.exists(storageFile)).isTrue();
    }
}
