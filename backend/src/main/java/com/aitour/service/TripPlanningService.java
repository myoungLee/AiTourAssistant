/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.common.dto.TripDtos;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 行程规划服务接口，定义 SSE 流式生成完整行程的能力。
 *
 * @author myoung
 */
public interface TripPlanningService {

    /**
     * 执行流式行程规划并通过 SSE 推送阶段事件。
     */
    void streamPlan(Long userId, TripDtos.CreateTripRequest request, SseEmitter emitter);
}
