package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.tool.utils.WorkspaceEnv;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * ReadFile 工具 - 安全读取文件
 */
public class ReadFileTool implements AgentTool {

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "安全地读取文件内容。支持限制读取行数，防止读取过大文件。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "要读取的文件路径"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "最大读取行数（可选，默认读取全部）"
                        )
                ),
                "required", new String[]{"path"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String path = (String) input.get("path");
        Integer limit = (Integer) input.get("limit");

        if (path == null || path.trim().isEmpty()) {
            return "❌ 错误: 必须指定文件路径";
        }

        System.out.println("\n[ReadFileTool] 📖 读取文件: " + path);
        System.out.println("   工作目录: " + WorkspaceEnv.WORKDIR);

        try {
            // 使用 WorkspaceEnv 的 safePath
            Path safePath = WorkspaceEnv.safePath(path.trim());

            // 读取文件内容
            String content = Files.readString(safePath, StandardCharsets.UTF_8);

            // 限制行数
            if (limit != null && limit > 0) {
                String[] lines = content.split("\n");
                int maxLines = Math.min(limit, lines.length);
                StringBuilder limitedContent = new StringBuilder();
                for (int i = 0; i < maxLines; i++) {
                    limitedContent.append(lines[i]).append("\n");
                }

                String result = limitedContent.toString();

                if (lines.length > limit) {
                    result += String.format("\n... (还有 %d 行未显示)", lines.length - limit);
                }

                return result;
            }

            // 限制大小（最多 50KB）
            if (content.length() > 50000) {
                content = content.substring(0, 50000);
                content += "\n... (文件过大，已截断)";
            }

            System.out.println("✅ 文件读取成功");
            return content;

        } catch (FileNotFoundException e) {
            return "❌ 错误: 文件不存在: " + path;
        } catch (IllegalArgumentException e) {
            return "❌ 安全错误: " + e.getMessage();
        } catch (Exception e) {
            return "❌ 读取文件失败: " + e.getMessage();
        }
    }
}
