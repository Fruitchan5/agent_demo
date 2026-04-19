package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.tool.utils.WorkspaceEnv;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * WriteFile 工具 - 安全写入文件
 */
public class WriteFileTool implements AgentTool {

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "安全地写入文件内容。如果文件已存在会覆盖，如果不存在会创建。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "要写入的文件路径"
                        ),
                        "content", Map.of(
                                "type", "string",
                                "description", "要写入的文件内容"
                        )
            ),
        "required", new String[]{"path", "content"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String path = (String) input.get("path");
        String content = (String) input.get("content");

        if (path == null || path.trim().isEmpty()) {
            return "❌ 错误: 必须指定文件路径";
        }

        if (content == null) {
            return "❌ 错误: 必须指定文件内容";
        }

        System.out.println("\n[WriteFileTool] ✍️  写入文件: " + path);
        System.out.println("   工作目录: " + WorkspaceEnv.WORKDIR);
        System.out.println("   内容长度: " + content.length() + " 字符");

        try {
            // 使用 WorkspaceEnv 的 safePath
            Path safePath = WorkspaceEnv.safePath(path.trim());

            // 创建父目录（如果不存在）
            Path parentDir = safePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                System.out.println("   已创建目录: " + parentDir);
            }

            // 写入文件
            Files.writeString(safePath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("✅ 文件写入成功");
            return "✅ 文件已成功写入: " + path;

        } catch (IllegalArgumentException e) {
            return "❌ 安全错误: " + e.getMessage();
        } catch (Exception e) {
            return "❌ 写入文件失败: " + e.getMessage();
        }
    }
}
