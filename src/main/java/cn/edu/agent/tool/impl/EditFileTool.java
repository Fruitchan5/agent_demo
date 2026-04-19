package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.tool.utils.WorkspaceEnv;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * EditFile 工具 - 编辑文件内容
 */
public class EditFileTool implements AgentTool {

    @Override
    public String getName() {
        return "edit_file";
    }

    @Override
    public String getDescription() {
        return "编辑文件内容。用新文本替换旧文本。只替换第一次匹配的内容。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "要编辑的文件路径"
                        ),
                        "old_text", Map.of(
                                "type", "string",
                                "description", "要替换的旧文本"
                        ),
                        "new_text", Map.of(
                                "type", "string",
                                "description", "新文本"
                        )
                ),
                "required", new String[]{"path", "old_text", "new_text"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String path = (String) input.get("path");
        String oldText = (String) input.get("old_text");
        String newText = (String) input.get("new_text");

        if (path == null || path.trim().isEmpty()) {
            return " 错误: 必须指定文件路径";
        }

        if (oldText == null || oldText.trim().isEmpty()) {
            return " 错误: 必须指定要替换的旧文本";
        }

        if (newText == null) {
            return " 错误: 必须指定新文本";
        }

        System.out.println("\n[EditFileTool] ✏  编辑文件: " + path);
        System.out.println("   工作目录: " + WorkspaceEnv.WORKDIR);
        System.out.println("   旧文本长度: " + oldText.length() + " 字符");
        System.out.println("   新文本长度: " + newText.length() + " 字符");

        try {
            // 使用 WorkspaceEnv 的 safePath
            Path safePath = WorkspaceEnv.safePath(path.trim());

            // 读取文件内容
            String content = Files.readString(safePath, StandardCharsets.UTF_8);

            // 查找并替换文本
            if (!content.contains(oldText)) {
                return " 错误: 文件中找不到指定的旧文本\n" +
                        "旧文本: " + oldText.substring(0, Math.min(50, oldText.length())) + "...";
            }

            // 只替换第一次匹配
            String newContent = content.replaceFirst(java.util.regex.Pattern.quote(oldText),
                    java.util.regex.Matcher.quoteReplacement(newText));

            // 写回文件
            Files.writeString(safePath, newContent, StandardCharsets.UTF_8);

            System.out.println(" 文件编辑成功");
            return " 文件已成功编辑: " + path;

        } catch (IllegalArgumentException e) {
            return " 安全错误: " + e.getMessage();
        } catch (Exception e) {
            return " 编辑文件失败: " + e.getMessage();
        }
    }
}
