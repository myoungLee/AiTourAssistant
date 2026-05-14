/*
 * @author myoung
 */
package com.aitour.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 事件发布接口，隔离业务编排和具体事件发送实现。
 *
 * @author myoung
 */
public interface StreamingEventPublisher {

    /**
     * 发送一个命名 SSE 事件。
     */
    void send(SseEmitter emitter, String eventName, Object data);

    /**
     * 正常结束 SSE 响应。
     */
    void complete(SseEmitter emitter);

    /**
     * 以异常状态结束 SSE 响应。
     */
    void completeWithError(SseEmitter emitter, Throwable error);
}
