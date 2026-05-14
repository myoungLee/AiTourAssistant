/*
 * @author myoung
 */
package com.aitour.service.impl;

import com.aitour.client.ai.AiChatClient;
import com.aitour.client.ai.ChatRequest;
import com.aitour.common.dto.SseDtos;
import com.aitour.common.dto.TripDtos;
import com.aitour.common.entity.TripPlan;
import com.aitour.mapper.TripPlanMapper;
import com.aitour.service.StreamingEventPublisher;
import com.aitour.service.TripAdjustmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 流式行程调整服务，根据用户反馈对既有行程输出调整建议。
 *
 * @author myoung
 */
@Service
public class TripAdjustmentServiceImpl implements TripAdjustmentService {
    private final AiChatClient aiChatClient;
    private final StreamingEventPublisher publisher;
    private final TripPlanMapper tripPlanMapper;

    /**
     * 注入 AI 客户端和 SSE 发布器。
     */
    public TripAdjustmentServiceImpl(AiChatClient aiChatClient, StreamingEventPublisher publisher, TripPlanMapper tripPlanMapper) {
        this.aiChatClient = aiChatClient;
        this.publisher = publisher;
        this.tripPlanMapper = tripPlanMapper;
    }

    /**
     * 根据用户调整指令流式输出 AI 增量，并在完成后发送 completed 事件。
     */
    @Override
    public void streamAdjust(Long userId, Long planId, TripDtos.AdjustTripRequest request, SseEmitter emitter) {
        try {
            ensurePlanBelongsToUser(userId, planId);
            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("adjust", "正在根据反馈调整行程", 20));
            aiChatClient.streamChat(new ChatRequest(List.of(
                    new ChatRequest.Message("user", "请调整行程 " + planId + "，用户 " + userId + " 的要求：" + request.instruction())
            ), true), text -> publisher.send(emitter, "ai_delta", new SseDtos.AiDeltaEvent(text)));
            publisher.send(emitter, "completed", new SseDtos.CompletedEvent(planId, "GENERATED"));
            publisher.complete(emitter);
        } catch (RuntimeException ex) {
            publisher.send(emitter, "error", new SseDtos.ErrorEvent("ADJUST_FAILED", ex.getMessage()));
            publisher.complete(emitter);
        }
    }

    /**
     * 校验二次调整目标行程归属当前用户，避免跨用户操作行程资源。
     */
    private void ensurePlanBelongsToUser(Long userId, Long planId) {
        TripPlan plan = tripPlanMapper.selectOne(new LambdaQueryWrapper<TripPlan>()
                .eq(TripPlan::getId, planId)
                .eq(TripPlan::getUserId, userId)
                .last("limit 1"));
        if (plan == null) {
            throw new IllegalArgumentException("行程不存在或无权调整");
        }
    }
}
