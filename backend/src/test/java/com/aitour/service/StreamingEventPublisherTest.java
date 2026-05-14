/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.common.dto.SseDtos;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 验证 SSE 发布器在连接打开状态下可以正常发送事件。
 *
 * @author myoung
 */
class StreamingEventPublisherTest {

    /**
     * 发送 progress 事件时不应抛出异常，保证服务层可以直接复用发布器。
     */
    @Test
    void shouldSendEventWithoutThrowingWhenEmitterIsOpen() {
        StreamingEventPublisher publisher = new StreamingEventPublisher();
        SseEmitter emitter = new SseEmitter(1000L);

        assertThatCode(() -> publisher.send(
                emitter,
                "progress",
                new SseDtos.ProgressEvent("parse", "正在解析出行需求", 10)
        )).doesNotThrowAnyException();
    }
}
