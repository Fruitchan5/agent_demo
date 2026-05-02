package cn.edu.agent.teammate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamConfig {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @JsonProperty("teamName")
    private String teamName = "default";
    
    @JsonProperty("members")
    private List<TeamMember> members = new ArrayList<>();
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMember {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("spawnedAt")
        private String spawnedAt;
    }
    
    public static TeamConfig load(Path configPath) {
        if (!Files.exists(configPath)) {
            return new TeamConfig();
        }
        try {
            String json = Files.readString(configPath);
            return MAPPER.readValue(json, TeamConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load TeamConfig from " + configPath, e);
        }
    }
    
    public void save(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
            Files.writeString(configPath, json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save TeamConfig to " + configPath, e);
        }
    }
    
    public TeamMember findMember(String name) {
        return members.stream()
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    public List<String> getMemberNames() {
        return members.stream()
            .map(TeamMember::getName)
            .toList();
    }
}
