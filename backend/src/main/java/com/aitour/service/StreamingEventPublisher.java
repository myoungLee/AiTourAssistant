/*
 * @author myoung
 */
package com.aitour.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 封装 SSE 事件发送细节，让业务服务只关心事件名称和事件数据。
 *
 * @author myoung
 */
@Component
public class StreamingEventPublisher {

    /**
     * 发送一个命名 SSE 事件，发送失败时抛出运行时异常交给上层流式流程统一收口。
     */
    public void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException ex) {
            throw new IllegalStateException("SSE 事件发送失败: " + eventName, ex);
        }
    }

    /**
     * 正常结束 SSE 响应。
     */
    public void complete(SseEmitter emitter) {
        emitter.complete();
    }

    /**
     * 以异常状态结束 SSE 响应，通常用于连接级别错误。
     */
    public void completeWithError(SseEmitter emitter, Throwable error) {
        emitter.completeWithError(error);
    }
}
