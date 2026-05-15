/*
 * @author myoung
 */
package com.aitour.service.impl;

import com.aitour.client.ai.AiChatClient;
import com.aitour.client.ai.ChatRequest;
import com.aitour.client.ai.PromptTemplateService;
import com.aitour.client.mcp.McpToolRegistry;
import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.common.dto.SseDtos;
import com.aitour.common.dto.TripDtos;
import com.aitour.common.entity.BudgetBreakdown;
import com.aitour.common.entity.LlmCallLog;
import com.aitour.common.entity.ToolCallLog;
import com.aitour.common.entity.TripDay;
import com.aitour.common.entity.TripItem;
import com.aitour.common.entity.TripPlan;
import com.aitour.domain.TripPlanStatus;
import com.aitour.domain.planning.AttractionRanker;
import com.aitour.domain.planning.BudgetEstimator;
import com.aitour.domain.planning.DaySchedulePlanner;
import com.aitour.domain.planning.WeatherRiskAnalyzer;
import com.aitour.mapper.BudgetBreakdownMapper;
import com.aitour.mapper.LlmCallLogMapper;
import com.aitour.mapper.ToolCallLogMapper;
import com.aitour.mapper.TripDayMapper;
import com.aitour.mapper.TripItemMapper;
import com.aitour.mapper.TripPlanMapper;
import com.aitour.service.StreamingEventPublisher;
import com.aitour.service.TripDraftService;
import com.aitour.service.TripPlanningService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 流式行程规划服务，串联草稿创建、本地 MCP 工具、规则规划、AI 摘要和最终持久化。
 *
 * @author myoung
 */
@Service
public class TripPlanningServiceImpl implements TripPlanningService {
    private static final Logger log = LoggerFactory.getLogger(TripPlanningServiceImpl.class);

    private final TripDraftService tripDraftService;
    private final StreamingEventPublisher publisher;
    private final McpToolRegistry toolRegistry;
    private final AiChatClient aiChatClient;
    private final PromptTemplateService promptTemplateService;
    private final OpenAiChatProperties openAiChatProperties;
    private final AttractionRanker attractionRanker;
    private final DaySchedulePlanner daySchedulePlanner;
    private final BudgetEstimator budgetEstimator;
    private final WeatherRiskAnalyzer weatherRiskAnalyzer;
    private final TripPlanMapper tripPlanMapper;
    private final TripDayMapper tripDayMapper;
    private final TripItemMapper tripItemMapper;
    private final BudgetBreakdownMapper budgetBreakdownMapper;
    private final ToolCallLogMapper toolCallLogMapper;
    private final LlmCallLogMapper llmCallLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 注入规划所需的业务服务、工具注册表、规则组件和持久化 mapper。
     */
    public TripPlanningServiceImpl(
            TripDraftService tripDraftService,
            StreamingEventPublisher publisher,
            McpToolRegistry toolRegistry,
            AiChatClient aiChatClient,
            PromptTemplateService promptTemplateService,
            OpenAiChatProperties openAiChatProperties,
            AttractionRanker attractionRanker,
            DaySchedulePlanner daySchedulePlanner,
            BudgetEstimator budgetEstimator,
            WeatherRiskAnalyzer weatherRiskAnalyzer,
            TripPlanMapper tripPlanMapper,
            TripDayMapper tripDayMapper,
            TripItemMapper tripItemMapper,
            BudgetBreakdownMapper budgetBreakdownMapper,
            ToolCallLogMapper toolCallLogMapper,
            LlmCallLogMapper llmCallLogMapper,
            ObjectMapper objectMapper
    ) {
        this.tripDraftService = tripDraftService;
        this.publisher = publisher;
        this.toolRegistry = toolRegistry;
        this.aiChatClient = aiChatClient;
        this.promptTemplateService = promptTemplateService;
        this.openAiChatProperties = openAiChatProperties;
        this.attractionRanker = attractionRanker;
        this.daySchedulePlanner = daySchedulePlanner;
        this.budgetEstimator = budgetEstimator;
        this.weatherRiskAnalyzer = weatherRiskAnalyzer;
        this.tripPlanMapper = tripPlanMapper;
        this.tripDayMapper = tripDayMapper;
        this.tripItemMapper = tripItemMapper;
        this.budgetBreakdownMapper = budgetBreakdownMapper;
        this.toolCallLogMapper = toolCallLogMapper;
        this.llmCallLogMapper = llmCallLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行主规划流程，并按阶段向 SSE 连接发送 progress、tool_result、plan_snapshot、ai_delta 和 completed 事件。
     */
    @Override
    public void streamPlan(Long userId, TripDtos.CreateTripRequest request, SseEmitter emitter) {
        Long planId = null;
        try {
            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("parse", "正在解析出行需求", 10));
            planId = tripDraftService.createDraft(userId, request);
            markPlanGenerating(planId);

            ToolResult weather = executeToolWithLog("weather.query", userId, planId, Map.of("city", request.destination()));
            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("weather", "正在查询天气", 25));
            publisher.send(emitter, "tool_result", new SseDtos.ToolResultEvent(weather.toolName(), weather.summary(), weather.data()));

            ToolResult places = executeToolWithLog("place.search", userId, planId, Map.of(
                    "city", request.destination(),
                    "preferences", safePreferences(request)
            ));
            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("places", "正在推荐景点", 45));
            publisher.send(emitter, "tool_result", new SseDtos.ToolResultEvent(places.toolName(), places.summary(), places.data()));

            List<Map<String, Object>> rankedPlaces = attractionRanker.rank(extractPlaces(places));
            ToolResult route = executeRouteTool(userId, planId, request.destination(), rankedPlaces);
            publisher.send(emitter, "tool_result", new SseDtos.ToolResultEvent(route.toolName(), route.summary(), route.data()));

            BudgetEstimator.BudgetDraft budget = budgetEstimator.estimate(request.days(), request.peopleCount(), request.budget());
            ToolResult budgetTool = executeToolWithLog("budget.estimate", userId, planId, Map.of(
                    "days", request.days(),
                    "peopleCount", request.peopleCount()
            ));
            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("budget", "正在估算预算", 65));
            publisher.send(emitter, "tool_result", new SseDtos.ToolResultEvent(
                    budgetTool.toolName(),
                    appendBudgetWarning(budgetTool.summary(), budget),
                    enrichBudgetToolData(budgetTool.data(), budget)
            ));

            String weatherSummary = weatherRiskAnalyzer.summarize(weather.summary());
            List<DaySchedulePlanner.DaySchedule> schedules = daySchedulePlanner.plan(
                    request.startDate(),
                    request.days(),
                    request.destination(),
                    rankedPlaces,
                    request.peopleCount(),
                    safePreferences(request),
                    budget
            );
            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("compose", "正在生成每日行程", 80));
            for (DaySchedulePlanner.DaySchedule schedule : schedules) {
                publisher.send(emitter, "plan_snapshot", new SseDtos.PlanSnapshotEvent(schedule.dayIndex(), schedule.items()));
            }

            String aiSummary = streamAiSummary(userId, request, planId, budget, weatherSummary, emitter);
            String finalSummary = appendBudgetWarning(aiSummary, budget);
            persistGeneratedPlan(planId, schedules, budget, weatherSummary, finalSummary, List.of(weather, places, route, budgetTool));

            publisher.send(emitter, "completed", new SseDtos.CompletedEvent(planId, TripPlanStatus.GENERATED.name()));
            publisher.complete(emitter);
        } catch (Exception ex) {
            log.warn("行程生成流程失败，planId={}", planId, ex);
            markPlanFailed(planId);
            publisher.send(emitter, "error", new SseDtos.ErrorEvent("PLAN_FAILED", safeErrorMessage(ex)));
            publisher.complete(emitter);
        }
    }

    /**
     * 将行程状态切换为生成中，让历史列表能显示真实处理状态。
     */
    private void markPlanGenerating(Long planId) {
        TripPlan plan = tripPlanMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus(TripPlanStatus.GENERATING.name());
            plan.setUpdatedAt(Instant.now());
            tripPlanMapper.updateById(plan);
        }
    }

    /**
     * 统一执行工具并记录调用日志，避免工具调用散落在编排流程里无法追踪。
     */
    private ToolResult executeToolWithLog(String toolName, Long userId, Long planId, Map<String, Object> arguments) throws JsonProcessingException {
        Instant start = Instant.now();
        try {
            ToolResult result = toolRegistry.execute(toolName, new ToolRequest(userId, planId, arguments));
            recordToolLog(userId, planId, toolName, arguments, result.summary(), start, true, null);
            return result;
        } catch (RuntimeException ex) {
            recordToolLog(userId, planId, toolName, arguments, null, start, false, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 基于前两个景点生成一条路线建议；景点不足时退化为城市内交通建议。
     */
    private ToolResult executeRouteTool(Long userId, Long planId, String city, List<Map<String, Object>> places) throws JsonProcessingException {
        String from = places.isEmpty() ? city + "酒店" : String.valueOf(places.get(0).getOrDefault("name", city + "酒店"));
        String to = places.size() < 2 ? city + "精选景点" : String.valueOf(places.get(1).getOrDefault("name", city + "精选景点"));
        return executeToolWithLog("route.plan", userId, planId, Map.of("from", from, "to", to));
    }

    /**
     * 从工具返回结构中提取景点列表，并把 Map key 统一转换为 String。
     */
    private List<Map<String, Object>> extractPlaces(ToolResult places) {
        Object rawPlaces = places.data().get("places");
        if (!(rawPlaces instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::stringKeyMap)
                .toList();
    }

    /**
     * 将未知 key 类型的 Map 转换为 String key，便于后续规划组件安全读取。
     */
    private Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(String.valueOf(key), value));
        return target;
    }

    /**
     * 调用 AI Client 输出行程摘要；响应同时作为 ai_delta 事件推给前端并写入调用日志。
     */
    private String streamAiSummary(
            Long userId,
            TripDtos.CreateTripRequest request,
            Long planId,
            BudgetEstimator.BudgetDraft budget,
            String weatherSummary,
            SseEmitter emitter
    ) {
        String prompt = promptTemplateService.buildPlanSummaryPrompt("""
                destination=%s
                days=%s
                people=%s
                budget=%s
                recommendedBudget=%s
                budgetWarning=%s
                weather=%s
                userInput=%s
                """.formatted(
                request.destination(),
                request.days(),
                request.peopleCount(),
                budget.total(),
                budget.recommendedTotal(),
                budget.warningMessage(),
                weatherSummary,
                request.userInput()
        ));
        Instant start = Instant.now();
        StringBuilder response = new StringBuilder();
        try {
            aiChatClient.streamChat(new ChatRequest(List.of(new ChatRequest.Message("user", prompt)), true), text -> {
                response.append(text);
                publisher.send(emitter, "ai_delta", new SseDtos.AiDeltaEvent(text));
            });
            recordLlmLog(userId, planId, prompt, response.toString(), start, true, null);
            return response.toString();
        } catch (RuntimeException ex) {
            recordLlmLog(userId, planId, prompt, response.toString(), start, false, ex.getMessage());
            throw ex;
        }
    }

    /**
     * 将预算提醒确定性追加到摘要中，避免完全依赖模型临场输出。
     */
    private String appendBudgetWarning(String summary, BudgetEstimator.BudgetDraft budget) {
        if (budget == null || !budget.insufficient() || budget.warningMessage() == null || budget.warningMessage().isBlank()) {
            return summary;
        }
        if (summary == null || summary.isBlank()) {
            return budget.warningMessage();
        }
        if (summary.contains(budget.warningMessage())) {
            return summary;
        }
        return summary + System.lineSeparator() + budget.warningMessage();
    }

    /**
     * 把预算风险状态并入预算工具结果，便于前端直接展示预算紧张提示。
     */
    private Map<String, Object> enrichBudgetToolData(Map<String, Object> source, BudgetEstimator.BudgetDraft budget) {
        Map<String, Object> target = new LinkedHashMap<>();
        if (source != null) {
            target.putAll(source);
        }
        target.put("recommendedTotal", budget.recommendedTotal());
        target.put("insufficient", budget.insufficient());
        target.put("warningMessage", budget.warningMessage());
        return target;
    }

    /**
     * 持久化生成完成的行程主表、每日安排、条目和预算明细。
     */
    private void persistGeneratedPlan(
            Long planId,
            List<DaySchedulePlanner.DaySchedule> schedules,
            BudgetEstimator.BudgetDraft budget,
            String weatherSummary,
            String finalSummary,
            List<ToolResult> toolResults
    ) throws JsonProcessingException {
        TripPlan plan = tripPlanMapper.selectById(planId);
        if (plan == null) {
            return;
        }
        plan.setStatus(TripPlanStatus.GENERATED.name());
        plan.setSummary(finalSummary);
        plan.setTotalBudget(budget.total());
        plan.setRawAiResultJson(objectMapper.writeValueAsString(Map.of(
                "weatherSummary", weatherSummary,
                "budget", budget,
                "schedules", schedules,
                "tools", toolResults
        )));
        plan.setUpdatedAt(Instant.now());
        tripPlanMapper.updateById(plan);

        persistDaysAndItems(planId, schedules, budget, weatherSummary);
        persistBudget(planId, budget);
    }

    /**
     * 将每日行程草案落到 trip_day 和 trip_item，供历史详情后续读取。
     */
    private void persistDaysAndItems(
            Long planId,
            List<DaySchedulePlanner.DaySchedule> schedules,
            BudgetEstimator.BudgetDraft budget,
            String weatherSummary
    ) {
        BigDecimal dailyBudget = budget.total().divide(BigDecimal.valueOf(Math.max(schedules.size(), 1)), 2, RoundingMode.HALF_UP);
        for (DaySchedulePlanner.DaySchedule schedule : schedules) {
            TripDay day = new TripDay();
            day.setId(newPositiveId());
            day.setPlanId(planId);
            day.setDayIndex(schedule.dayIndex());
            day.setDate(schedule.date());
            day.setCity(schedule.city());
            day.setWeatherSummary(weatherSummary);
            day.setDailyBudget(dailyBudget);
            tripDayMapper.insert(day);

            for (DaySchedulePlanner.PlanItemDraft itemDraft : schedule.items()) {
                TripItem item = new TripItem();
                item.setId(newPositiveId());
                item.setDayId(day.getId());
                item.setTimeSlot(itemDraft.timeSlot());
                item.setPlaceName(itemDraft.placeName());
                item.setPlaceType(itemDraft.placeType());
                item.setDurationMinutes(itemDraft.durationMinutes());
                item.setTransportSuggestion("建议公共交通优先，必要时使用网约车衔接。");
                item.setEstimatedCost(estimateItemCost(dailyBudget, schedule.items().size()));
                item.setReason(itemDraft.reason());
                tripItemMapper.insert(item);
            }
        }
    }

    /**
     * 按单日预算和条目数量粗略拆分单条活动成本，避免历史详情里成本信息全部为零。
     */
    private BigDecimal estimateItemCost(BigDecimal dailyBudget, int itemCount) {
        if (dailyBudget == null || itemCount <= 0) {
            return BigDecimal.ZERO;
        }
        return dailyBudget.divide(BigDecimal.valueOf(itemCount), 2, RoundingMode.HALF_UP);
    }

    /**
     * 保存预算拆分明细，便于行程详情页展示成本结构。
     */
    private void persistBudget(Long planId, BudgetEstimator.BudgetDraft budget) throws JsonProcessingException {
        BudgetBreakdown breakdown = new BudgetBreakdown();
        breakdown.setId(newPositiveId());
        breakdown.setPlanId(planId);
        breakdown.setHotelCost(budget.hotel());
        breakdown.setFoodCost(budget.food());
        breakdown.setTransportCost(budget.transport());
        breakdown.setTicketCost(budget.ticket());
        breakdown.setOtherCost(budget.other());
        breakdown.setDetailJson(objectMapper.writeValueAsString(budget));
        budgetBreakdownMapper.insert(breakdown);
    }

    /**
     * 记录一次 MCP 工具调用日志。
     */
    private void recordToolLog(
            Long userId,
            Long planId,
            String toolName,
            Map<String, Object> arguments,
            String responseSummary,
            Instant start,
            boolean success,
            String errorMessage
    ) throws JsonProcessingException {
        ToolCallLog log = new ToolCallLog();
        log.setId(newPositiveId());
        log.setUserId(userId);
        log.setPlanId(planId);
        log.setToolName(toolName);
        log.setRequestJson(objectMapper.writeValueAsString(arguments));
        log.setResponseSummary(responseSummary);
        log.setLatencyMs(Duration.between(start, Instant.now()).toMillis());
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setCreatedAt(Instant.now());
        toolCallLogMapper.insert(log);
    }

    /**
     * 记录一次 LLM 调用日志。
     */
    private void recordLlmLog(
            Long userId,
            Long planId,
            String prompt,
            String response,
            Instant start,
            boolean success,
            String errorMessage
    ) {
        LlmCallLog log = new LlmCallLog();
        log.setId(newPositiveId());
        log.setUserId(userId);
        log.setPlanId(planId);
        log.setProvider("spring-ai-openai");
        log.setModel(openAiChatProperties.getOptions().getModel());
        log.setPromptSummary(truncate(prompt));
        log.setResponseSummary(truncate(response));
        log.setTokenUsageJson("{}");
        log.setLatencyMs(Duration.between(start, Instant.now()).toMillis());
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        log.setCreatedAt(Instant.now());
        llmCallLogMapper.insert(log);
    }

    /**
     * 规划失败时将主表状态改为 FAILED，避免历史列表长期显示生成中。
     */
    private void markPlanFailed(Long planId) {
        if (planId == null) {
            return;
        }
        TripPlan plan = tripPlanMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus(TripPlanStatus.FAILED.name());
            plan.setUpdatedAt(Instant.now());
            tripPlanMapper.updateById(plan);
        }
    }

    /**
     * 将偏好空值统一为空列表，避免下游工具收到 null。
     */
    private List<String> safePreferences(TripDtos.CreateTripRequest request) {
        return request.preferences() == null ? List.of() : request.preferences();
    }

    /**
     * 限制日志摘要长度，避免大提示词直接撑大日志表。
     */
    private String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    /**
     * 给前端返回稳定错误提示，避免空异常消息导致 UI 无法展示。
     */
    private String safeErrorMessage(Exception ex) {
        return ex.getMessage() == null ? "行程生成失败" : ex.getMessage();
    }

    /**
     * 生成正数主键，沿用当前项目的本地 UUID 主键策略。
     */
    private Long newPositiveId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
