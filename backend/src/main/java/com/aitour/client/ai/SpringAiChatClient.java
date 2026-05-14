/*
 * @author myoung
 */
package com.aitour.client.ai;

import com.aitour.common.exception.ApiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 基于 Spring AI 官方 ChatClient 的模型客户端。
 *
 * @author myoung
 */
@Component
public class SpringAiChatClient implements AiChatClient {
    private final ChatClient chatClient;

    /**
     * 使用 Spring AI 自动配置的 ChatClient.Builder 创建客户端。
     */
    public SpringAiChatClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 执行非流式模型调用，返回模型生成文本。
     */
    @Override
    public String chat(ChatRequest request) {
        try {
            return chatClient.prompt()
                    .messages(toSpringAiMessages(request))
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", "AI 供应商调用失败");
        }
    }

    /**
     * 执行流式模型调用，将 Spring AI 输出片段转发给业务层 SSE 发布器。
     */
    @Override
    public void streamChat(ChatRequest request, Consumer<String> onDelta) {
        try {
            chatClient.prompt()
                    .messages(toSpringAiMessages(request))
                    .stream()
                    .content()
                    .toStream()
                    .filter(text -> text != null && !text.isBlank())
                    .forEach(onDelta);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", "AI 供应商流式调用失败");
        }
    }

    /**
     * 将项目内部消息结构转换为 Spring AI 消息列表。
     */
    private List<Message> toSpringAiMessages(ChatRequest request) {
        return request.messages().stream()
                .map(this::toSpringAiMessage)
                .toList();
    }

    /**
     * 将 OpenAI 风格 role 映射到 Spring AI 消息类型。
     */
    private Message toSpringAiMessage(ChatRequest.Message message) {
        if ("system".equalsIgnoreCase(message.role())) {
            return new SystemMessage(message.content());
        }
        return new UserMessage(message.content());
    }
}
