package com.example.llm.text.analysis.llm;

import com.example.llm.text.analysis.common.BusinessException;
import com.example.llm.text.analysis.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 大模型 HTTP 调用工具类
 * 封装 OpenAI 兼容的 Chat Completions 接口（智谱 GLM / DeepSeek 等均可接入）
 * 内置连接/响应超时控制，网络异常由 spring-retry 自动重试（指数退避）
 */
@Slf4j
@Component
public class LlmClient {

    @Value("${llm.api.base-url}")
    private String baseUrl;

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.api.model}")
    private String model;

    @Value("${llm.api.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${llm.api.response-timeout-ms:60000}")
    private int responseTimeoutMs;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用大模型对话接口
     *
     * @param prompt 用户提示词
     * @return 模型回复文本
     * @throws IOException 网络异常（触发重试）
     */
    @Retryable(retryFor = IOException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2.0))
    public String chat(String prompt) throws IOException {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.3
        );
        String body = objectMapper.writeValueAsString(requestBody);

        try (CloseableHttpClient httpClient = buildHttpClient()) {
            HttpPost httpPost = new HttpPost(baseUrl);
            httpPost.setHeader("Authorization", "Bearer " + apiKey);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

            return httpClient.execute(httpPost, response -> {
                int statusCode = response.getCode();
                String responseBody = readResponseBody(response);

                if (statusCode == 429) {
                    // 限流不重试，直接快速失败
                    throw new BusinessException(ErrorCode.LLM_RATE_LIMITED, "大模型接口限流(429)，请稍后重试");
                }
                if (statusCode >= 500) {
                    // 服务端错误抛 IOException，交给 spring-retry 重试
                    throw new IOException("大模型服务端错误: HTTP " + statusCode);
                }
                if (statusCode != 200) {
                    throw new BusinessException(ErrorCode.LLM_UNAVAILABLE, "大模型调用失败: HTTP " + statusCode);
                }
                return extractContent(responseBody);
            });
        }
    }

    /**
     * 重试耗尽后的兜底恢复（网络 IO 异常）
     */
    @Recover
    public String recover(IOException e, String prompt) {
        log.error("大模型调用重试3次后仍失败, prompt={}", prompt, e);
        throw new BusinessException(ErrorCode.LLM_UNAVAILABLE, "大模型服务暂不可用，请稍后重试");
    }

    /**
     * 业务异常的兜底恢复（限流、鉴权失败等不重试场景，原样抛出）
     * 注意：spring-retry 对不可重试的异常同样会触发恢复流程，
     * 若不提供对应类型的 @Recover 会报 "Cannot locate recovery method"
     */
    @Recover
    public String recover(BusinessException e, String prompt) {
        throw e;
    }

    private CloseableHttpClient buildHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMs))
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    private String readResponseBody(ClassicHttpResponse response) throws IOException {
        if (response.getEntity() == null) {
            return "";
        }
        try {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (ParseException e) {
            throw new IOException("响应体解析失败: " + e.getMessage(), e);
        }
    }

    private String extractContent(String responseBody) throws IOException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new BusinessException(ErrorCode.LLM_UNAVAILABLE, "大模型响应格式异常");
            }
            return content.asText().trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("大模型响应解析失败: " + e.getMessage(), e);
        }
    }
}
