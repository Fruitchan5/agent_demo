package cn.edu.agent.core;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.monitor.LlmInvocationRecord;
import cn.edu.agent.monitor.SessionStats;
import cn.edu.agent.pojo.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LlmClient {
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final int MAX_RETRIES = 3;

    public LlmClient() {
        int timeoutSeconds = AppConfig.getLlmTimeoutSeconds();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    public LlmResponse call(List<Map<String, Object>> messages, List<Map<String, Object>> tools) throws IOException {
        return call(messages, tools, "你是 Claude，一个高级软工 AI 助手。你可以使用工具来完成任务。");
    }

    public LlmResponse call(List<Map<String, Object>> messages,
                            List<Map<String, Object>> tools,
                            String systemPrompt) throws IOException {
        Instant start = Instant.now();
        int retryCount = 0;
        IOException lastException = null;

        while (retryCount <= MAX_RETRIES) {
            try {
                LlmResponse response = doCall(messages, tools, systemPrompt);
                long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
                int blockCount = response.getContent() == null ? 0 : response.getContent().size();
                SessionStats.get().recordLlm(new LlmInvocationRecord(
                        start, ms, true, null, response.getStopReason(), blockCount, retryCount));
                return response;
            } catch (IOException e) {
                lastException = e;
                retryCount++;
                if (retryCount > MAX_RETRIES) break;
                System.err.printf("[LlmClient] 调用失败，%ds 后重试 (%d/%d): %s%n",
                        retryCount, retryCount, MAX_RETRIES, e.getMessage());
                try { Thread.sleep(retryCount * 1000L); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        long ms = Instant.now().toEpochMilli() - start.toEpochMilli();
        SessionStats.get().recordLlm(new LlmInvocationRecord(
                start, ms, false, lastException.getMessage(), null, 0, retryCount - 1));
        throw lastException;
    }

    private LlmResponse doCall(List<Map<String, Object>> messages,
                               List<Map<String, Object>> tools,
                               String systemPrompt) throws IOException {
        String apiKey = AppConfig.getApiKey();
        String baseUrl = AppConfig.getBaseUrl();

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", AppConfig.getModelId());
        requestMap.put("max_tokens", 1024);
        requestMap.put("system", systemPrompt);
        requestMap.put("messages", messages);
        requestMap.put("tools", tools); // 把工具列表告诉大模型

        String jsonBody = mapper.writeValueAsString(requestMap);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
                // 1. 🔥 必须加上 /v1，否则会 405
                .url(baseUrl + "/v1/messages")
                .post(body);

// 2. 🔥 核心逻辑：判断是否为中转
        if (baseUrl != null && !baseUrl.isEmpty()) {
            // 中转模式 (LuminAI)：只加 x-api-key
            requestBuilder.addHeader("x-api-key", apiKey);
        } else {
            // 直连官方模式：加 Authorization 和 version
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
            requestBuilder.addHeader("anthropic-version", "2023-06-01");
        }

// 3. 通用 Content-Type (已经在 RequestBody.create 里指定了，加不加都行，加上更保险)
        requestBuilder.addHeader("Content-Type", "application/json");

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respStr = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("API Error: " + response.code() + " " + respStr);
            }
            return mapper.readValue(respStr, LlmResponse.class);
        }
    }
}
