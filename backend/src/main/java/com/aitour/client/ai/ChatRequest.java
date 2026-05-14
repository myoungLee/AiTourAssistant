/*
 * @author myoung
 */
package com.aitour.client.ai;

import java.util.List;

/**
 * AI 聊天请求，保持简洁 role/content 结构，由 Spring AI 适配具体模型协议。
 *
 * @author myoung
 */
public record ChatRequest(List<Message> messages, boolean stream) {
    public record Message(String role, String content) {
    }
}
