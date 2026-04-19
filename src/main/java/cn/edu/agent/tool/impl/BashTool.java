package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

public class BashTool implements AgentTool {
    @Override
    public String getName() {
        return "bash";
    }

    @Override
    public String getDescription() {
        return "执行 Windows 命令行 (cmd) 指令。可以用来查看目录、读写文件等。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        // 按照 Anthropic 的 JSON Schema 格式定义参数
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of("type", "string", "description", "要执行的 cmd 命令")
                ),
                "required", new String[]{"command"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String command = (String) input.get("command");
        System.out.println("\n[BashTool] 🤖 执行命令: " + command);

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true); // 合并正确和错误输出
        Process process = pb.start();

        // Windows 下 cmd 的输出通常是 GBK 编码
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        process.waitFor(); // 等待命令执行完

        String result = output.toString().trim();
        return result.isEmpty() ? "命令执行成功，无输出。" : result;
    }
}