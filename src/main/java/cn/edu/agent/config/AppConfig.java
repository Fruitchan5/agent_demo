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

    /** 返回 MCP server 启动命令，未配置则返回 null */
    public static String getMcpCommand() {
        return getEnv("MCP_COMMAND", null);
    }

    /** 返回后台任务输出目录，默认 .task_outputs */
    public static String getBackgroundTaskOutputDir() {
        return getEnv("BACKGROUND_TASK_OUTPUT_DIR", ".task_outputs");
    }

    /** 返回后台任务线程池最大大小，默认 4 */
    public static int getBackgroundTaskMaxPoolSize() {
        String val = getEnv("BACKGROUND_TASK_MAX_POOL_SIZE", "4");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    /** 返回后台任务清理时间（小时），默认 1 */
    public static int getBackgroundTaskCleanupHours() {
        String val = getEnv("BACKGROUND_TASK_CLEANUP_HOURS", "1");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** 返回后台任务最大并发数，默认 3 */
    public static int getBackgroundMaxTasks() {
        String val = getEnv("BACKGROUND_MAX_TASKS", "3");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    /** 返回后台任务默认超时时间（秒），默认 300 */
    public static int getBackgroundTimeoutSeconds() {
        String val = getEnv("BACKGROUND_TIMEOUT_SECONDS", "300");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 300;
        }
    }

    /** 返回后台任务优雅关闭等待时间（秒），默认 10 */
    public static int getBackgroundGracefulShutdownSeconds() {
        String val = getEnv("BACKGROUND_GRACEFUL_SHUTDOWN_SECONDS", "10");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    /** 返回后台任务输出最大字符数，默认 10000 */
    public static int getBackgroundOutputMaxChars() {
        String val = getEnv("BACKGROUND_OUTPUT_MAX_CHARS", "10000");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 10000;
        }
    }

    /** 返回后台任务通知摘要最大字符数，默认 500 */
    public static int getBackgroundNotificationSummaryChars() {
        String val = getEnv("BACKGROUND_NOTIFICATION_SUMMARY_CHARS", "500");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 500;
        }
    }

    /** 返回是否使用线程池执行后台任务，默认 true */
    public static boolean useThreadPool() {
        String val = getEnv("BACKGROUND_USE_THREAD_POOL", "true");
        return Boolean.parseBoolean(val);
    }

    /** 返回LLM HTTP超时时间（秒），默认 180 */
    public static int getLlmTimeoutSeconds() {
        String val = getEnv("LLM_TIMEOUT_SECONDS", "180");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 180;
        }
    }

    // ── s11: autonomous agent configuration ─────────────────────────────────

    /** 返回空闲轮询间隔（秒），默认 5 */
    public static int getIdlePollInterval() {
        String val = getEnv("IDLE_POLL_INTERVAL", "5");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    /** 返回空闲超时时间（秒），默认 60 */
    public static int getIdleTimeout() {
        String val = getEnv("IDLE_TIMEOUT", "60");
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 60;
        }
    }
}
