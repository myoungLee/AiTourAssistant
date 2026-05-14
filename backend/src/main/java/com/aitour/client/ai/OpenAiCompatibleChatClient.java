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

    @Override
    public String chat(ChatRequest request) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "stream", false,
                "messages", request.messages().stream()
                        .map(message -> Map.of("role", message.role(), "content", message.content()))
                        .toList()
        );
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

    @Override
    public void streamChat(ChatRequest request, Consumer<String> onDelta) {
        String response = chat(new ChatRequest(request.messages(), false));
        onDelta.accept(response);
    }

    private HttpRequest buildHttpRequest(Map<String, Object> body) throws JsonProcessingException {
        return HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl().replaceAll("/+$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
    }
}
