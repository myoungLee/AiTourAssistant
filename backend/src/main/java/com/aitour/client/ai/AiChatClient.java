/*
 * @author myoung
 */
package com.aitour.client.ai;

import java.util.function.Consumer;

/**
 * AI 客户端抽象，业务层只依赖接口，后续可替换供应商。
 *
 * @author myoung
 */
public interface AiChatClient {
    String chat(ChatRequest request);

    void streamChat(ChatRequest request, Consumer<String> onDelta);
}
