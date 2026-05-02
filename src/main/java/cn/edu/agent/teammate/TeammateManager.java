package cn.edu.agent.teammate;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.core.LlmClient;
import cn.edu.agent.pojo.ContentBlock;
import cn.edu.agent.pojo.LlmResponse;
import cn.edu.agent.teammate.protocol.ProtocolManager;
import cn.edu.agent.tool.AgentRole;
import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TeammateManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ITERATIONS = 50;

    private final Path teamDir;
    private final Path configPath;
    private final TeamConfig config;
    private final MessageBus messageBus;
    private final ProtocolManager protocolManager;
    private final ExecutorService executor;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final Map<String, TeammateSession> sessions = new ConcurrentHashMap<>();

    public TeammateManager(Path teamDir, ToolRegistry toolRegistry) {
        this.teamDir = teamDir;
        this.configPath = teamDir.resolve("config.json");
        this.config = TeamConfig.load(configPath);
        this.messageBus = new MessageBus(teamDir);
        try {
            this.protocolManager = new ProtocolManager();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize ProtocolManager", e);
        }
        this.toolRegistry = toolRegistry;
        this.llmClient = new LlmClient();

        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("teammate-" + System.currentTimeMillis());
            return t;
        });
    }
    
    public String spawn(String name, String role, String prompt) {
        TeamConfig.TeamMember member = config.findMember(name);
        
        if (member != null) {
            if (!"IDLE".equals(member.getStatus()) && !"SHUTDOWN".equals(member.getStatus())) {
                return "Error: '" + name + "' is currently " + member.getStatus();
            }
            member.setStatus("WORKING");
            member.setRole(role);
        } else {
            member = new TeamConfig.TeamMember(name, role, "WORKING", Instant.now().toString());
            config.getMembers().add(member);
        }
        config.save(configPath);
        
        TeammateSession session = sessions.computeIfAbsent(name, k -> new TeammateSession(name, role));
        session.status = SessionStatus.WORKING;
        
        executor.submit(() -> {
            teammateLoop(name, role, prompt);
            session.status = SessionStatus.IDLE;
            updateMemberStatus(name, "IDLE");
        });
        
        return "Spawned '" + name + "' (role: " + role + ")";
    }
    
    public String listAll() {
        if (config.getMembers().isEmpty()) {
            return "No teammates.";
        }
        StringBuilder sb = new StringBuilder("Team: " + config.getTeamName() + "\n");
        for (TeamConfig.TeamMember m : config.getMembers()) {
            sb.append("  ").append(m.getName())
              .append(" (").append(m.getRole()).append("): ")
              .append(m.getStatus()).append("\n");
        }
        return sb.toString();
    }
    
    public MessageBus getMessageBus() {
        return messageBus;
    }

    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    public List<String> getMemberNames() {
        return config.getMemberNames();
    }
    
    private void teammateLoop(String name, String role, String prompt) {
        String systemPrompt = String.format(
            "You are '%s', role: %s, at %s. Use send_message to communicate. Complete your task.",
            name, role, System.getProperty("user.dir")
        );
        
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        
        List<Map<String, Object>> tools = toolRegistry.getToolsForLlm(AgentRole.TEAMMATE);
        
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            try {
                List<Message> inbox = messageBus.readInbox(name);
                for (Message msg : inbox) {
                    messages.add(Map.of("role", "user", "content", msg.toJson()));
                }
                
                LlmResponse response = llmClient.call(messages, tools, systemPrompt);
                messages.add(Map.of("role", "assistant", "content", response.getContent()));
                
                if (!"tool_use".equals(response.getStopReason())) {
                    break;
                }
                
                List<Map<String, Object>> toolResults = new ArrayList<>();
                for (ContentBlock block : response.getContent()) {
                    if ("tool_use".equals(block.getType())) {
                        String output = executeTeammateTool(name, block.getName(), block.getInput());
                        System.out.println("  [" + name + "] " + block.getName() + ": " + 
                            (output.length() > 120 ? output.substring(0, 120) + "..." : output));
                        
                        toolResults.add(Map.of(
                            "type", "tool_result",
                            "tool_use_id", block.getId(),
                            "content", output
                        ));
                    }
                }
                messages.add(Map.of("role", "user", "content", toolResults));
                
            } catch (Exception e) {
                System.err.println("[Teammate " + name + "] Error: " + e.getMessage());
                break;
            }
        }
    }
    
    private String executeTeammateTool(String sender, String toolName, Map<String, Object> input) {
        AgentTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return "Unknown tool: " + toolName;
        }
        
        if ("send_message".equals(toolName)) {
            String to = (String) input.get("to");
            String content = (String) input.get("content");
            String msgType = (String) input.getOrDefault("msg_type", "MESSAGE");
            return messageBus.send(sender, to, content, msgType);
        }
        
        if ("read_inbox".equals(toolName)) {
            try {
                List<Message> inbox = messageBus.readInbox(sender);
                return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(inbox);
            } catch (Exception e) {
                return "Error reading inbox: " + e.getMessage();
            }
        }
        
        try {
            return tool.execute(input);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    private void updateMemberStatus(String name, String status) {
        TeamConfig.TeamMember member = config.findMember(name);
        if (member != null && !"SHUTDOWN".equals(member.getStatus())) {
            member.setStatus(status);
            config.save(configPath);
        }
    }
    
    public void shutdown() {
        executor.shutdownNow();
    }
    
    static class TeammateSession {
        String name;
        String role;
        SessionStatus status;
        
        TeammateSession(String name, String role) {
            this.name = name;
            this.role = role;
            this.status = SessionStatus.IDLE;
        }
    }
    
    enum SessionStatus {
        IDLE, WORKING
    }
}
