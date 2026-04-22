package cn.edu.agent.compact;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 负责将完整对话历史序列化为 JSONL 并写入 .transcripts/ 目录。
 * 文件名格式：transcript_{unix_timestamp}.jsonl
 */
public class TranscriptManager {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 将完整对话历史以 JSONL 格式写入 transcriptDir 目录。
     *
     * @param messages      完整对话历史
     * @param transcriptDir 存储目录（默认 ".transcripts"）
     * @return 写入的文件路径
     */
    public String save(List<Map<String, Object>> messages, String transcriptDir) throws IOException {
        // 确保目录存在
        Path dir = Paths.get(transcriptDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        // 生成文件名
        long timestamp = System.currentTimeMillis() / 1000L;
        String fileName = "transcript_" + timestamp + ".jsonl";
        Path filePath = dir.resolve(fileName);

        // 逐行写入 JSONL
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            for (Map<String, Object> message : messages) {
                String line = mapper.writeValueAsString(message);
                writer.write(line);
                writer.newLine();
            }
        }

        return filePath.toAbsolutePath().toString();
    }
}
