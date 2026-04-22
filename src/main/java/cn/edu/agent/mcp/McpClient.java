package cn.edu.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 极简 MCP stdio client。
 * 启动一个子进程，通过 stdin/stdout 收发 JSON-RPC 2.0 消息。
 * 支持：initialize → tools/list → tools/call
 */
public class McpClient implements Closeable {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger idGen = new AtomicInteger(1);

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    /** command 示例："npx -y @modelcontextprotocol/server-filesystem D:/workspace" */
    public McpClient(String command) throws IOException {
        String[] parts = command.trim().split("\\s+");
        ProcessBuilder pb = new ProcessBuilder(parts);
        pb.redirectErrorStream(false); // stderr 不混入 stdout
        this.process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        initialize();
    }

    /** 握手 */
    private void initialize() throws IOException {
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        ObjectNode clientInfo = mapper.createObjectNode();
        clientInfo.put("name", "agent_demo");
        clientInfo.put("version", "1.0");
        params.set("clientInfo", clientInfo);
        params.set("capabilities", mapper.createObjectNode());

        sendRequest("initialize", params);

        // 发 initialized 通知（无需等响应）
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        notification.set("params", mapper.createObjectNode());
        writeLine(mapper.writeValueAsString(notification));
    }

    /** 获取 MCP server 提供的工具列表 */
    public List<McpToolDef> listTools() throws IOException {
        JsonNode result = sendRequest("tools/list", mapper.createObjectNode());
        List<McpToolDef> tools = new ArrayList<>();
        JsonNode toolsNode = result.path("tools");
        for (JsonNode t : toolsNode) {
            String name = t.path("name").asText();
            String description = t.path("description").asText("");
            JsonNode schema = t.path("inputSchema");
            tools.add(new McpToolDef(name, description, schema));
        }
        return tools;
    }

    /** 调用工具，返回文本结果 */
    public String callTool(String toolName, Map<String, Object> input) throws IOException {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", mapper.valueToTree(input));

        JsonNode result = sendRequest("tools/call", params);

        // MCP tools/call 返回 { content: [{type:"text", text:"..."}], isError: bool }
        boolean isError = result.path("isError").asBoolean(false);
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : result.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        String text = sb.toString();
        if (isError) throw new IOException("MCP tool error: " + text);
        return text;
    }

    /** 发送 JSON-RPC 请求，阻塞等待响应 */
    private JsonNode sendRequest(String method, ObjectNode params) throws IOException {
        int id = idGen.getAndIncrement();
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        req.set("params", params);

        writeLine(mapper.writeValueAsString(req));

        // 读响应，跳过通知消息（无 id 字段）
        while (true) {
            String line = reader.readLine();
            if (line == null) throw new IOException("MCP server closed unexpectedly");
            line = line.trim();
            if (line.isEmpty()) continue;

            JsonNode resp = mapper.readTree(line);
            if (!resp.has("id")) continue; // 跳过 server 推送的通知

            if (resp.has("error")) {
                throw new IOException("MCP error: " + resp.path("error").toString());
            }
            return resp.path("result");
        }
    }

    private void writeLine(String json) throws IOException {
        writer.write(json);
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() {
        try { writer.close(); } catch (Exception ignored) {}
        try { reader.close(); } catch (Exception ignored) {}
        if (process != null && process.isAlive()) process.destroyForcibly();
    }

    /** MCP tool 元数据 */
    public record McpToolDef(String name, String description, JsonNode inputSchema) {}
}
