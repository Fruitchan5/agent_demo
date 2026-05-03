package cn.edu.agent.tool.impl;

import cn.edu.agent.tool.AgentTool;
import cn.edu.agent.tool.utils.WorkspaceEnv;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReadFile 工具 - 安全读取文件（带缓存）
 */
public class ReadFileTool implements AgentTool {

    // 文件内容缓存（会话级别）
    private static final Map<String, CachedFile> fileCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 50; // 最多缓存50个文件

    static class CachedFile {
        String content;
        long lastModified;
        long readTime;

        CachedFile(String content, long lastModified) {
            this.content = content;
            this.lastModified = lastModified;
            this.readTime = System.currentTimeMillis();
        }
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "安全地读取文件内容。支持限制读取行数，防止读取过大文件。" +
               "已读取的文件会被缓存，避免重复读取浪费token。";
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
                        ),
                        "force_reload", Map.of(
                                "type", "boolean",
                                "description", "强制重新读取，忽略缓存（可选，默认false）"
                        )
                ),
                "required", new String[]{"path"}
        );
    }

    @Override
    public String execute(Map<String, Object> input) throws Exception {
        String path = (String) input.get("path");
        Integer limit = (Integer) input.get("limit");
        Boolean forceReload = (Boolean) input.get("force_reload");
        if (forceReload == null) forceReload = false;

        if (path == null || path.trim().isEmpty()) {
            return "❌ 错误: 必须指定文件路径";
        }

        String normalizedPath = path.trim();
        System.out.println("\n[ReadFileTool] 📖 读取文件: " + normalizedPath);
        System.out.println("   工作目录: " + WorkspaceEnv.WORKDIR);

        try {
            // 使用 WorkspaceEnv 的 safePath
            Path safePath = WorkspaceEnv.safePath(normalizedPath);
            long currentModified = Files.getLastModifiedTime(safePath).toMillis();

            // 检查缓存
            boolean fromCache = false;
            String content;
            CachedFile cached = fileCache.get(normalizedPath);

            if (!forceReload && cached != null && cached.lastModified == currentModified) {
                // 使用缓存
                content = cached.content;
                fromCache = true;
                System.out.println("✅ 使用缓存内容（文件未修改）");
            } else {
                // 从磁盘读取
                content = Files.readString(safePath, StandardCharsets.UTF_8);
                
                // 更新缓存
                if (fileCache.size() >= MAX_CACHE_SIZE) {
                    // 清理最旧的缓存项
                    String oldestKey = fileCache.entrySet().stream()
                            .min((e1, e2) -> Long.compare(e1.getValue().readTime, e2.getValue().readTime))
                            .map(Map.Entry::getKey)
                            .orElse(null);
                    if (oldestKey != null) {
                        fileCache.remove(oldestKey);
                    }
                }
                fileCache.put(normalizedPath, new CachedFile(content, currentModified));
                System.out.println("✅ 文件读取成功并已缓存");
            }

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

                // 添加缓存提示
                if (fromCache) {
                    result = "[📋 从缓存读取] " + result;
                }

                return result;
            }

            // 限制大小（最多 50KB）
            if (content.length() > 50000) {
                content = content.substring(0, 50000);
                content += "\n... (文件过大，已截断)";
            }

            // 添加缓存提示
            if (fromCache) {
                return "[📋 从缓存读取 - 此文件在本次会话中已读取过，无需重复阅读]\n\n" + content;
            }

            return content;

        } catch (FileNotFoundException e) {
            return "❌ 错误: 文件不存在: " + normalizedPath;
        } catch (IllegalArgumentException e) {
            return "❌ 安全错误: " + e.getMessage();
        } catch (Exception e) {
            return "❌ 读取文件失败: " + e.getMessage();
        }
    }

    /**
     * 清除所有缓存（用于测试或重置）
     */
    public static void clearCache() {
        fileCache.clear();
        System.out.println("[ReadFileTool] 缓存已清空");
    }

    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats() {
        return String.format("缓存文件数: %d/%d", fileCache.size(), MAX_CACHE_SIZE);
    }
}
