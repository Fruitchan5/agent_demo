package cn.edu.agent.config;

import io.github.cdimascio.dotenv.Dotenv;

public class AppConfig {
    // 单例模式加载 dotenv
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing() // 如果没找到文件不报错
            .load();

    private static String getEnv(String key, String defaultValue) {
        String systemEnv = System.getenv(key);
        if (systemEnv != null && !systemEnv.isBlank()) {
            return systemEnv;
        }
        return dotenv.get(key, defaultValue);
    }

    public static String getApiKey() {
        return getEnv("ANTHROPIC_API_KEY", null);
    }

    public static String getBaseUrl() {
        return getEnv("ANTHROPIC_BASE_URL", "https://api.anthropic.com/v1");
    }

    public static String getModelId() {
        return getEnv("MODEL_ID", "claude-3-5-sonnet-20240620");
    }

    public static int getSubagentMaxIterations() {
        return Integer.parseInt(getEnv("SUBAGENT_MAX_ITERATIONS", "30"));
    }

    public static int getCompactTokenThreshold() {
        return Integer.parseInt(getEnv("COMPACT_TOKEN_THRESHOLD", "50000"));
    }

    public static int getCompactKeepRecent() {
        return Integer.parseInt(getEnv("COMPACT_KEEP_RECENT", "3"));
    }

    public static String getTranscriptDir() {
        return getEnv("TRANSCRIPT_DIR", ".transcripts");
    }
}