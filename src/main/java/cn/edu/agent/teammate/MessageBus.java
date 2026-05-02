package cn.edu.agent.teammate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MessageBus {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path inboxDir;
    
    public MessageBus(Path teamDir) {
        this.inboxDir = teamDir.resolve("inbox");
        try {
            Files.createDirectories(inboxDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create inbox directory: " + inboxDir, e);
        }
    }
    
    public String send(String from, String to, String content, String msgType) {
        return send(from, to, content, msgType, null);
    }

    public String send(String from, String to, String content, String msgType, java.util.Map<String, Object> metadata) {
        Message msg = new Message();
        msg.setType(msgType);
        msg.setFrom(from);
        msg.setContent(content);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setProtocolVersion("1.0");
        msg.setMetadata(metadata);

        Path inboxPath = inboxDir.resolve(to + ".jsonl");

        try {
            String jsonLine = msg.toJson() + "\n";
            Files.writeString(inboxPath, jsonLine,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
            return "Sent " + msgType + " to " + to;
        } catch (IOException e) {
            throw new RuntimeException("Failed to send message to " + to, e);
        }
    }
    
    public List<Message> readInbox(String name) {
        Path inboxPath = inboxDir.resolve(name + ".jsonl");
        
        if (!Files.exists(inboxPath)) {
            return List.of();
        }
        
        try {
            List<String> lines = Files.readAllLines(inboxPath);
            List<Message> messages = lines.stream()
                .filter(line -> !line.trim().isEmpty())
                .map(Message::fromJson)
                .collect(Collectors.toList());
            
            // Drain: clear the file
            Files.writeString(inboxPath, "");
            
            return messages;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read inbox for " + name, e);
        }
    }
    
    public String broadcast(String from, String content, List<String> teammates) {
        int count = 0;
        for (String name : teammates) {
            if (!name.equals(from)) {
                send(from, name, content, "BROADCAST");
                count++;
            }
        }
        return "Broadcast to " + count + " teammates";
    }
}
