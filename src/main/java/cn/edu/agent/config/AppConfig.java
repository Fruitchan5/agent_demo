package cn.edu.agent.config;

import io.github.cdimascio.dotenv.Dotenv;

public class AppConfig {
    // 单例模式加载 dotenv
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing() // 如果没找到文件不报错
            .load();

    public static String getApiKey() {
        return dotenv.get("ANTHROPIC_API_KEY");
    }

    public static String getBaseUrl() {
        return dotenv.get("ANTHROPIC_BASE_URL", "https://api.anthropic.com/v1");
    }

    public static String getModelId() {
        return dotenv.get("MODEL_ID", "claude-3-5-sonnet-20240620");
    }
}