package cn.edu.agent.core;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.pojo.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class LlmClient {
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public LlmResponse call(List<Map<String, Object>> messages, List<Map<String, Object>> tools) throws IOException {
        String apiKey = AppConfig.getApiKey();
        String baseUrl = AppConfig.getBaseUrl();

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", AppConfig.getModelId());
        requestMap.put("max_tokens", 1024);
        requestMap.put("system", "你是 Kiro，一个高级软工 AI 助手。你可以使用工具来完成任务。");
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