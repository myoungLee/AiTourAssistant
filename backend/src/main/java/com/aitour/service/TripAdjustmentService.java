/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.common.dto.TripDtos;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 行程调整服务接口，定义对既有行程进行流式二次调整的能力。
 *
 * @author myoung
 */
public interface TripAdjustmentService {

    /**
     * 根据用户调整指令流式输出调整结果。
     */
    void streamAdjust(Long userId, Long planId, TripDtos.AdjustTripRequest request, SseEmitter emitter);
}
