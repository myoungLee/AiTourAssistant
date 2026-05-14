/*
 * @author myoung
 */
package com.aitour.client.ai;

import java.util.List;

/**
 * AI 聊天请求，保持和 OpenAI-compatible chat/completions 结构接近。
 *
 * @author myoung
 */
public record ChatRequest(List<Message> messages, boolean stream) {
    public record Message(String role, String content) {
    }
}
