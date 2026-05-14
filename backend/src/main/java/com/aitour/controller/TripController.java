/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.common.dto.TripDtos;
import com.aitour.config.security.CurrentUser;
import com.aitour.service.TripAdjustmentService;
import com.aitour.service.TripDraftService;
import com.aitour.service.TripPlanningService;
import com.aitour.service.TripQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 行程草稿创建和历史行程查询接口。
 *
 * @author myoung
 */
@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final TripDraftService tripDraftService;
    private final TripQueryService tripQueryService;
    private final TripPlanningService tripPlanningService;
    private final TripAdjustmentService tripAdjustmentService;

    /**
     * 注入行程草稿、查询、流式生成和二次调整服务。
     */
    public TripController(
            TripDraftService tripDraftService,
            TripQueryService tripQueryService,
            TripPlanningService tripPlanningService,
            TripAdjustmentService tripAdjustmentService
    ) {
        this.tripDraftService = tripDraftService;
        this.tripQueryService = tripQueryService;
        this.tripPlanningService = tripPlanningService;
        this.tripAdjustmentService = tripAdjustmentService;
    }

    /**
     * 创建待生成的行程草稿，适用于非流式调试或后续异步任务入口。
     */
    @PostMapping("/draft")
    public Map<String, Long> createDraft(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody TripDtos.CreateTripRequest request
    ) throws JsonProcessingException {
        return Map.of("planId", tripDraftService.createDraft(currentUser.id(), request));
    }

    /**
     * 启动 SSE 流式行程生成，立即返回 emitter，后台线程持续推送事件。
     */
    @PostMapping(value = "/stream-plan", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPlan(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody TripDtos.CreateTripRequest request
    ) {
        SseEmitter emitter = new SseEmitter(120_000L);
        Long userId = currentUser.id();
        CompletableFuture.runAsync(() -> tripPlanningService.streamPlan(userId, request, emitter));
        return emitter;
    }

    /**
     * 启动 SSE 流式行程调整，根据用户指令返回调整进度和 AI 增量。
     */
    @PostMapping(value = "/{id}/adjust-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adjustStream(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody TripDtos.AdjustTripRequest request
    ) {
        SseEmitter emitter = new SseEmitter(120_000L);
        Long userId = currentUser.id();
        CompletableFuture.runAsync(() -> tripAdjustmentService.streamAdjust(userId, id, request, emitter));
        return emitter;
    }

    /**
     * 查询当前用户的历史行程概要列表。
     */
    @GetMapping
    public List<TripDtos.TripSummaryResponse> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return tripQueryService.listTrips(currentUser.id());
    }

    /**
     * 查询当前用户指定行程的详情。
     */
    @GetMapping("/{id}")
    public TripDtos.TripDetailResponse detail(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
        return tripQueryService.getTrip(currentUser.id(), id);
    }
}
