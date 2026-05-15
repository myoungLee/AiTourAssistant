/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.common.Result;
import com.aitour.common.dto.TripDtos;
import com.aitour.config.security.CurrentUser;
import com.aitour.service.TripAdjustmentService;
import com.aitour.service.TripDraftService;
import com.aitour.service.TripPlanningService;
import com.aitour.service.TripQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 行程草稿创建和历史行程查询接口。
 *
 * @author myoung
 */
@RestController
@RequestMapping("/api/trips")
@Tag(name = "行程接口", description = "行程草稿、历史查询、SSE 流式生成和行程二次调整")
@SecurityRequirement(name = "bearerAuth")
@Validated
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
    @Operation(summary = "创建行程草稿", description = "保存用户出行需求，返回待生成行程的 planId。")
    @ApiResponse(responseCode = "200", description = "创建成功")
    @ApiResponse(responseCode = "401", description = "未登录或 token 无效")
    public Result<TripDtos.CreateDraftResponse> createDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = "目的地城市或区域") @RequestParam @NotBlank String destination,
            @Parameter(description = "出发日期") @RequestParam @NotNull @FutureOrPresent LocalDate startDate,
            @Parameter(description = "出行天数，范围 1-15") @RequestParam @NotNull @Min(1) @Max(15) Integer days,
            @Parameter(description = "总预算") @RequestParam(required = false) BigDecimal budget,
            @Parameter(description = "出行人数，范围 1-20") @RequestParam @NotNull @Min(1) @Max(20) Integer peopleCount,
            @Parameter(description = "偏好标签") @RequestParam(required = false) List<String> preferences,
            @Parameter(description = "用户补充需求") @RequestParam(required = false) String userInput
    ) throws JsonProcessingException {
        TripDtos.CreateTripRequest request = buildCreateTripRequest(destination, startDate, days, budget, peopleCount, preferences, userInput);
        Long planId = tripDraftService.createDraft(currentUser.id(), request);
        return Result.success(new TripDtos.CreateDraftResponse(planId));
    }

    /**
     * 启动 SSE 流式行程生成，立即返回 emitter，后台线程持续推送事件。
     */
    @PostMapping(value = "/stream-plan", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式生成行程", description = "通过 SSE 推送 progress、tool_result、plan_snapshot、ai_delta、completed 或 error 事件。")
    @ApiResponse(responseCode = "200", description = "SSE 连接创建成功")
    @ApiResponse(responseCode = "401", description = "未登录或 token 无效")
    public SseEmitter streamPlan(
            @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = "目的地城市或区域") @RequestParam @NotBlank String destination,
            @Parameter(description = "出发日期") @RequestParam @NotNull @FutureOrPresent LocalDate startDate,
            @Parameter(description = "出行天数，范围 1-15") @RequestParam @NotNull @Min(1) @Max(15) Integer days,
            @Parameter(description = "总预算") @RequestParam(required = false) BigDecimal budget,
            @Parameter(description = "出行人数，范围 1-20") @RequestParam @NotNull @Min(1) @Max(20) Integer peopleCount,
            @Parameter(description = "偏好标签") @RequestParam(required = false) List<String> preferences,
            @Parameter(description = "用户补充需求") @RequestParam(required = false) String userInput
    ) {
        SseEmitter emitter = new SseEmitter(120_000L);
        Long userId = currentUser.id();
        TripDtos.CreateTripRequest request = buildCreateTripRequest(destination, startDate, days, budget, peopleCount, preferences, userInput);
        CompletableFuture.runAsync(() -> tripPlanningService.streamPlan(userId, request, emitter));
        return emitter;
    }

    /**
     * 启动 SSE 流式行程调整，根据用户指令返回调整进度和 AI 增量。
     */
    @PostMapping(value = "/{id}/adjust-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式调整行程", description = "对指定行程发起二次调整，通过 SSE 返回调整进度和 AI 增量结果。")
    @ApiResponse(responseCode = "200", description = "SSE 连接创建成功")
    @ApiResponse(responseCode = "401", description = "未登录或 token 无效")
    public SseEmitter adjustStream(
            @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = "行程 ID") @PathVariable Long id,
            @Parameter(description = "调整指令") @RequestParam @NotBlank String instruction
    ) {
        SseEmitter emitter = new SseEmitter(120_000L);
        Long userId = currentUser.id();
        TripDtos.AdjustTripRequest request = new TripDtos.AdjustTripRequest(instruction);
        CompletableFuture.runAsync(() -> tripAdjustmentService.streamAdjust(userId, id, request, emitter));
        return emitter;
    }

    /**
     * 查询当前用户的历史行程概要列表。
     */
    @GetMapping
    @Operation(summary = "查询行程列表", description = "查询当前登录用户的历史行程概要。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或 token 无效")
    public Result<List<TripDtos.TripSummaryResponse>> list(@Parameter(hidden = true) @AuthenticationPrincipal CurrentUser currentUser) {
        return Result.success(tripQueryService.listTrips(currentUser.id()));
    }

    /**
     * 查询当前用户指定行程的详情。
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询行程详情", description = "查询当前登录用户指定行程的每日安排、条目和预算明细。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或 token 无效")
    @ApiResponse(responseCode = "404", description = "行程不存在")
    public Result<TripDtos.TripDetailResponse> detail(
            @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser currentUser,
            @Parameter(description = "行程 ID") @PathVariable Long id
    ) {
        return Result.success(tripQueryService.getTrip(currentUser.id(), id));
    }

    /**
     * 将 Controller 接收的独立字段组装为 Service 层使用的行程请求 DTO。
     */
    private TripDtos.CreateTripRequest buildCreateTripRequest(
            String destination,
            LocalDate startDate,
            Integer days,
            BigDecimal budget,
            Integer peopleCount,
            List<String> preferences,
            String userInput
    ) {
        return new TripDtos.CreateTripRequest(destination, startDate, days, budget, peopleCount, preferences, userInput);
    }
}
