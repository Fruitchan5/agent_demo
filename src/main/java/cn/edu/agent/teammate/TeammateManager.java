package cn.edu.agent.teammate;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.core.LlmClient;
import cn.edu.agent.pojo.ContentBlock;
import cn.edu.agent.pojo.LlmResponse;
import cn.edu.agent.task.TaskManager;
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
    private final TaskManager taskManager;  // s11: for autonomous task claiming
    private final Map<String, TeammateSession> sessions = new ConcurrentHashMap<>();

    public TeammateManager(Path teamDir, ToolRegistry toolRegistry, TaskManager taskManager) {
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
        this.taskManager = taskManager;
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
        String teamName = config.getTeamName();
        String systemPrompt = String.format(
            "You are '%s', role: %s, team: %s, at %s. " +
            "Use idle tool when you have no more work. You will auto-claim new tasks.",
            name, role, teamName, System.getProperty("user.dir")
        );

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));

        List<Map<String, Object>> tools = toolRegistry.getToolsForLlm(AgentRole.TEAMMATE);

        while (true) {
            // ===== WORK 阶段 =====
            boolean idleRequested = false;

            for (int i = 0; i < MAX_ITERATIONS; i++) {
                try {
                    // 检查收件箱（每轮开始）
                    List<Message> inbox = messageBus.readInbox(name);
                    for (Message msg : inbox) {
                        if ("SHUTDOWN_REQUEST:v1".equals(msg.getType())) {
                            updateMemberStatus(name, "SHUTDOWN");
                            return;  // 立即退出
                        }
                        messages.add(Map.of("role", "user", "content", msg.toJson()));
                    }

                    LlmResponse response = llmClient.call(messages, tools, systemPrompt);
                    messages.add(Map.of("role", "assistant", "content", response.getContent()));

                    if (!"tool_use".equals(response.getStopReason())) {
                        return;  // 非工具调用结束，退出
                    }

                    List<Map<String, Object>> toolResults = new ArrayList<>();
                    for (ContentBlock block : response.getContent()) {
                        if ("tool_use".equals(block.getType())) {
                            if ("idle".equals(block.getName())) {
                                idleRequested = true;
                                toolResults.add(Map.of(
                                    "type", "tool_result",
                                    "tool_use_id", block.getId(),
                                    "content", "Entering idle phase. Will poll for new tasks."
                                ));
                            } else {
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
                    }
                    messages.add(Map.of("role", "user", "content", toolResults));

                    if (idleRequested) {
                        break;  // 进入 IDLE 阶段
                    }

                } catch (Exception e) {
                    System.err.println("[Teammate " + name + "] Error: " + e.getMessage());
                    return;
                }
            }

            if (!idleRequested) {
                // 达到最大迭代次数，退出
                return;
            }

            // ===== IDLE 阶段 =====
            updateMemberStatus(name, "IDLE");
            IdlePoller poller = new IdlePoller(name, messageBus, taskManager);
            IdlePollResult pollResult = poller.poll();

            switch (pollResult.getType()) {
                case MESSAGES:
                    // 收到消息，恢复工作
                    for (Message msg : pollResult.getMessages()) {
                        messages.add(Map.of("role", "user", "content", msg.toJson()));
                    }
                    updateMemberStatus(name, "WORKING");
                    continue;

                case TASK_CLAIMED:
                    // 认领到任务，恢复工作
                    cn.edu.agent.task.Task task = pollResult.getClaimedTask();

                    // 检查是否需要重注入身份
                    IdentityManager.reinjectIdentity(messages, name, role, teamName);

                    // 添加任务提示
                    String taskPrompt = String.format(
                        "<auto-claimed>Task #%d: %s\n%s</auto-claimed>",
                        task.getId(), task.getSubject(), task.getDescription()
                    );
                    messages.add(Map.of("role", "user", "content", taskPrompt));
                    messages.add(Map.of(
                        "role", "assistant",
                        "content", "Claimed task #" + task.getId() + ". Working on it."
                    ));

                    updateMemberStatus(name, "WORKING");
                    continue;

                case TIMEOUT:
                    // 超时，关闭线程
                    updateMemberStatus(name, "SHUTDOWN");
                    return;
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

        // s10: Handle teammate protocol tools that need sender context
        if ("plan_request".equals(toolName)) {
            try {
                String plan = (String) input.get("plan");
                String requestId = protocolManager.initiateRequest(
                    "plan_approval",
                    "lead",
                    Map.of("plan", plan, "from", sender),
                    java.time.Duration.ofMinutes(5)
                );

                messageBus.send(
                    sender,
                    "lead",
                    "Plan approval request:\n" + plan,
                    "PLAN_REQUEST:v1",
                    Map.of("request_id", requestId, "protocol_version", "1.0")
                );

                return "Plan request " + requestId + " submitted (status: pending). " +
                       "Waiting for lead approval before proceeding.";
            } catch (Exception e) {
                return "Error submitting plan request: " + e.getMessage();
            }
        }

        if ("shutdown_response".equals(toolName)) {
            try {
                String requestId = (String) input.get("request_id");
                boolean approve = (boolean) input.get("approve");
                String reason = (String) input.getOrDefault("reason", "");

                protocolManager.handleResponse(requestId, approve, reason);

                messageBus.send(
                    sender,
                    "lead",
                    reason,
                    "SHUTDOWN_RESPONSE:v1",
                    Map.of("request_id", requestId, "approve", approve, "protocol_version", "1.0")
                );

                if (approve) {
                    return "SHUTDOWN_APPROVED:" + requestId;
                } else {
                    return "Shutdown request rejected: " + reason;
                }
            } catch (Exception e) {
                return "Error responding to shutdown: " + e.getMessage();
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
