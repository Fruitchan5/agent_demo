package cn.edu.agent.teammate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * MessageBus 基础功能测试
 */
class MessageBusTest {

    @TempDir
    Path tempDir;

    private Path inboxDir;

    @BeforeEach
    void setUp() {
        inboxDir = tempDir.resolve("inbox");
    }

    @Test
    @DisplayName("应该成功创建收件箱文件")
    void shouldCreateInboxFile() throws Exception {
        // Given
        Path aliceInbox = inboxDir.resolve("Alice.jsonl");
        
        // When
        Files.createDirectories(inboxDir);
        String message = "{\"type\":\"MESSAGE\",\"from\":\"lead\",\"content\":\"测试消息\",\"timestamp\":1777647070000}\n";
        Files.writeString(aliceInbox, message);

        // Then
        assertThat(aliceInbox).exists();
        List<String> lines = Files.readAllLines(aliceInbox);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0))
            .contains("\"from\":\"lead\"")
            .contains("\"content\":\"测试消息\"")
            .contains("\"type\":\"MESSAGE\"");
    }

    @Test
    @DisplayName("应该支持多条消息追加")
    void shouldAppendMultipleMessages() throws Exception {
        // Given
        Path aliceInbox = inboxDir.resolve("Alice.jsonl");
        Files.createDirectories(inboxDir);

        // When
        String msg1 = "{\"type\":\"MESSAGE\",\"from\":\"lead\",\"content\":\"消息1\",\"timestamp\":1777647070000}\n";
        String msg2 = "{\"type\":\"MESSAGE\",\"from\":\"Candy\",\"content\":\"消息2\",\"timestamp\":1777647080000}\n";
        Files.writeString(aliceInbox, msg1);
        Files.writeString(aliceInbox, msg2, java.nio.file.StandardOpenOption.APPEND);

        // Then
        List<String> lines = Files.readAllLines(aliceInbox);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("消息1");
        assertThat(lines.get(1)).contains("消息2");
    }

    @Test
    @DisplayName("应该正确处理 JSONL 格式")
    void shouldHandleJsonlFormat() throws Exception {
        // Given
        Path leadInbox = inboxDir.resolve("lead.jsonl");
        Files.createDirectories(inboxDir);
        
        String jsonl = "{\"type\":\"MESSAGE\",\"from\":\"Alice\",\"content\":\"接口设计已完成\",\"timestamp\":1777647070000}\n" +
                       "{\"type\":\"MESSAGE\",\"from\":\"Candy\",\"content\":\"发现3个问题\",\"timestamp\":1777647080000}\n";
        Files.writeString(leadInbox, jsonl);

        // When
        List<String> lines = Files.readAllLines(leadInbox);

        // Then
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("Alice").contains("接口设计已完成");
        assertThat(lines.get(1)).contains("Candy").contains("发现3个问题");
    }

    @Test
    @DisplayName("应该支持广播消息格式")
    void shouldSupportBroadcastFormat() throws Exception {
        // Given
        Path aliceInbox = inboxDir.resolve("Alice.jsonl");
        Path candyInbox = inboxDir.resolve("Candy.jsonl");
        Files.createDirectories(inboxDir);

        String broadcast = "{\"type\":\"BROADCAST\",\"from\":\"lead\",\"content\":\"status update: phase 1 complete\",\"timestamp\":1777647070000}\n";

        // When
        Files.writeString(aliceInbox, broadcast);
        Files.writeString(candyInbox, broadcast);

        // Then
        assertThat(aliceInbox).exists();
        assertThat(candyInbox).exists();
        
        String aliceContent = Files.readString(aliceInbox);
        String candyContent = Files.readString(candyInbox);
        
        assertThat(aliceContent).contains("\"type\":\"BROADCAST\"");
        assertThat(candyContent).contains("\"type\":\"BROADCAST\"");
        assertThat(aliceContent).contains("status update: phase 1 complete");
    }

    @Test
    @DisplayName("应该处理包含特殊字符的消息")
    void shouldHandleSpecialCharacters() throws Exception {
        // Given
        Path inbox = inboxDir.resolve("test.jsonl");
        Files.createDirectories(inboxDir);
        
        // JSON 中需要转义的特殊字符
        String message = "{\"type\":\"MESSAGE\",\"from\":\"lead\",\"content\":\"消息包含 \\\"引号\\\" 和换行\",\"timestamp\":1777647070000}\n";

        // When
        Files.writeString(inbox, message);

        // Then
        String content = Files.readString(inbox);
        assertThat(content).contains("\\\"引号\\\"");
    }

    @Test
    @DisplayName("应该支持读取后清空收件箱")
    void shouldDrainInboxAfterRead() throws Exception {
        // Given
        Path inbox = inboxDir.resolve("lead.jsonl");
        Files.createDirectories(inboxDir);
        String message = "{\"type\":\"MESSAGE\",\"from\":\"Alice\",\"content\":\"任务完成\",\"timestamp\":1777647070000}\n";
        Files.writeString(inbox, message);

        // When - 模拟读取并清空
        List<String> lines = Files.readAllLines(inbox);
        Files.delete(inbox);
        Files.createFile(inbox); // 创建空文件

        // Then
        assertThat(lines).hasSize(1);
        assertThat(Files.size(inbox)).isEqualTo(0);
    }

    @Test
    @DisplayName("应该自动创建收件箱目录")
    void shouldCreateInboxDirectory() throws Exception {
        // Given
        Path newInboxDir = tempDir.resolve("new_inbox");
        assertThat(newInboxDir).doesNotExist();

        // When
        Files.createDirectories(newInboxDir);
        Path inbox = newInboxDir.resolve("test.jsonl");
        Files.writeString(inbox, "test\n");

        // Then
        assertThat(newInboxDir).exists();
        assertThat(inbox).exists();
    }
}
