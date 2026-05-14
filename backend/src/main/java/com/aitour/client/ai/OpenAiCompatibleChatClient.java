/*
 * @author myoung
 */
package com.aitour.client.ai;

import com.aitour.common.exception.ApiException;
import com.aitour.config.ai.AiProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI-compatible chat/completions 客户端，默认非流式，流式能力在 SSE 阶段扩展。
 *
 * @author myoung
 */
@Component
@EnableConfigurationProperties(AiProperties.class)
public class OpenAiCompatibleChatClient implements AiChatClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行非流式 Chat Completions 调用；未配置密钥时返回本地摘要，保证开发环境可离线冒烟。
     */
    @Override
    public String chat(ChatRequest request) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return buildLocalFallbackResponse(request);
        }
        Map<String, Object> body = buildChatCompletionBody(request);
        try {
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .build()
                    .send(buildHttpRequest(body), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", "AI 供应商调用失败");
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_REQUEST_SERIALIZE_ERROR", "AI 请求序列化失败");
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_UNAVAILABLE", "AI 供应商暂不可用");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AI_REQUEST_INTERRUPTED", "AI 请求被中断");
        }
    }

    /**
     * 第一版流式实现先把完整响应作为一个增量发送，后续接入真实 SSE 响应体解析。
     */
    @Override
    public void streamChat(ChatRequest request, Consumer<String> onDelta) {
        String response = chat(new ChatRequest(request.messages(), false));
        onDelta.accept(response);
    }

    /**
     * 构造 OpenAI-compatible HTTP 请求，统一追加 chat/completions 路径。
     */
    private HttpRequest buildHttpRequest(Map<String, Object> body) throws JsonProcessingException {
        return HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl().replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
    }

    /**
     * 构造 Chat Completions 请求体，并按配置透传模型推理等级。
     */
    private Map<String, Object> buildChatCompletionBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("stream", false);
        body.put("messages", request.messages().stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());
        if (properties.reasoningEffort() != null && !properties.reasoningEffort().isBlank()) {
            body.put("reasoning_effort", properties.reasoningEffort());
        }
        return body;
    }

    /**
     * 根据最后一条用户消息生成本地兜底摘要，避免没有外部 AI Key 时阻塞主流程验证。
     */
    private String buildLocalFallbackResponse(ChatRequest request) {
        String lastUserMessage = request.messages().stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(ChatRequest.Message::content)
                .reduce((first, second) -> second)
                .orElse("请生成旅行说明");
        return "本地 AI 摘要：" + lastUserMessage;
    }
}
