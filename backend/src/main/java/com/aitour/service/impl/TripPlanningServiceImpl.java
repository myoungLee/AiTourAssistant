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

/**
 * 流式行程规划服务，负责草稿创建、MCP 工具调用、规则编排、AI 总结和结果持久化。
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
     * 注入规划所需的业务服务、规则组件和持久化组件。
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
     * 执行完整的流式规划流程，并通过 SSE 按阶段推送事件。
     */
    @Override
    public void streamPlan(Long userId, TripDtos.CreateTripRequest request, SseEmitter emitter) {
        Long planId = null;
        Instant overallStart = Instant.now();
        log.info("流式行程规划开始，userId={} destination={} startDate={} days={} peopleCount={} budget={} preferencesCount={} mcpMode={}",
                userId,
                request.destination(),
                request.startDate(),
                request.days(),
                request.peopleCount(),
                request.budget(),
                safePreferences(request).size(),
                toolRegistry.mode());
        try {
            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("parse", "正在解析出行需求", 10));
            planId = tripDraftService.createDraft(userId, request);
            markPlanGenerating(planId);
            log.info("流式行程草稿准备完成，planId={} userId={}", planId, userId);

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
            log.info("景点排序完成，planId={} rankedPlaceCount={}", planId, rankedPlaces.size());
            ToolResult route = executeRouteTool(userId, planId, request.destination(), rankedPlaces);
            publisher.send(emitter, "tool_result", new SseDtos.ToolResultEvent(route.toolName(), route.summary(), route.data()));

            BudgetEstimator.BudgetDraft budget = budgetEstimator.estimate(request.days(), request.peopleCount(), request.budget());
            log.info("预算估算完成，planId={} total={} recommendedTotal={} insufficient={}",
                    planId,
                    budget.total(),
                    budget.recommendedTotal(),
                    budget.insufficient());
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
            log.info("日程编排完成，planId={} dayCount={} itemCount={}",
                    planId,
                    schedules.size(),
                    schedules.stream().mapToInt(schedule -> schedule.items().size()).sum());
            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("compose", "正在生成每日行程", 80));
            for (DaySchedulePlanner.DaySchedule schedule : schedules) {
                publisher.send(emitter, "plan_snapshot", new SseDtos.PlanSnapshotEvent(schedule.dayIndex(), schedule.items()));
            }

            String aiSummary = streamAiSummary(userId, request, planId, budget, weatherSummary, emitter);
            String finalSummary = appendBudgetWarning(aiSummary, budget);
            persistGeneratedPlan(planId, schedules, budget, weatherSummary, finalSummary, List.of(weather, places, route, budgetTool));

            publisher.send(emitter, "completed", new SseDtos.CompletedEvent(planId, TripPlanStatus.GENERATED.name()));
            publisher.complete(emitter);
            log.info("流式行程规划完成，planId={} userId={} durationMs={}",
                    planId,
                    userId,
                    Duration.between(overallStart, Instant.now()).toMillis());
        } catch (Exception ex) {
            log.warn("流式行程规划失败，planId={} userId={} durationMs={}",
                    planId,
                    userId,
                    Duration.between(overallStart, Instant.now()).toMillis(),
                    ex);
            markPlanFailed(planId);
            publisher.send(emitter, "error", new SseDtos.ErrorEvent("PLAN_FAILED", safeErrorMessage(ex)));
            publisher.complete(emitter);
        }
    }

    /**
     * 将计划状态切换为生成中。
     */
    private void markPlanGenerating(Long planId) {
        TripPlan plan = tripPlanMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus(TripPlanStatus.GENERATING.name());
            plan.setUpdatedAt(Instant.now());
            tripPlanMapper.updateById(plan);
            log.info("行程状态更新为生成中，planId={}", planId);
        } else {
            log.warn("行程状态更新为生成中失败，planId={} reason=PLAN_NOT_FOUND", planId);
        }
    }

    /**
     * 统一执行工具并记录工具调用日志。
     */
    private ToolResult executeToolWithLog(String toolName, Long userId, Long planId, Map<String, Object> arguments) throws JsonProcessingException {
        Instant start = Instant.now();
        log.info("MCP 工具调用开始，planId={} userId={} toolName={} arguments={}", planId, userId, toolName, arguments);
        try {
            ToolResult result = toolRegistry.execute(toolName, new ToolRequest(userId, planId, arguments));
            recordToolLog(userId, planId, toolName, arguments, result.summary(), start, true, null);
            log.info("MCP 工具调用成功，planId={} toolName={} durationMs={} success={} summaryLength={}",
                    planId,
                    toolName,
                    Duration.between(start, Instant.now()).toMillis(),
                    result.success(),
                    result.summary() == null ? 0 : result.summary().length());
            return result;
        } catch (RuntimeException ex) {
            recordToolLog(userId, planId, toolName, arguments, null, start, false, ex.getMessage());
            log.warn("MCP 工具调用失败，planId={} toolName={} durationMs={} message={}",
                    planId,
                    toolName,
                    Duration.between(start, Instant.now()).toMillis(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    /**
     * 基于候选景点生成一条路由建议。
     */
    private ToolResult executeRouteTool(Long userId, Long planId, String city, List<Map<String, Object>> places) throws JsonProcessingException {
        String from = places.isEmpty() ? city + "酒店" : String.valueOf(places.get(0).getOrDefault("name", city + "酒店"));
        String to = places.size() < 2 ? city + "精选景点" : String.valueOf(places.get(1).getOrDefault("name", city + "精选景点"));
        log.info("生成路线工具入参，planId={} from={} to={}", planId, from, to);
        return executeToolWithLog("route.plan", userId, planId, Map.of("from", from, "to", to));
    }

    /**
     * 从工具结果中提取景点列表。
     */
    private List<Map<String, Object>> extractPlaces(ToolResult places) {
        Object rawPlaces = places.data().get("places");
        if (!(rawPlaces instanceof List<?> list)) {
            log.warn("景点工具未返回列表结构，toolName={} dataKeys={}",
                    places.toolName(),
                    places.data() == null ? List.of() : places.data().keySet());
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::stringKeyMap)
                .toList();
    }

    /**
     * 统一将未知 key 类型的 Map 转为 String key。
     */
    private Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(String.valueOf(key), value));
        return target;
    }

    /**
     * 流式生成 AI 行程总结并同步推送前端增量内容。
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
        log.info("AI 总结生成开始，planId={} model={} promptLength={}",
                planId,
                openAiChatProperties.getOptions().getModel(),
                prompt.length());
        try {
            aiChatClient.streamChat(new ChatRequest(List.of(new ChatRequest.Message("user", prompt)), true), text -> {
                response.append(text);
                publisher.send(emitter, "ai_delta", new SseDtos.AiDeltaEvent(text));
            });
            recordLlmLog(userId, planId, prompt, response.toString(), start, true, null);
            log.info("AI 总结生成完成，planId={} durationMs={} responseLength={}",
                    planId,
                    Duration.between(start, Instant.now()).toMillis(),
                    response.length());
            return response.toString();
        } catch (RuntimeException ex) {
            recordLlmLog(userId, planId, prompt, response.toString(), start, false, ex.getMessage());
            log.warn("AI 总结生成失败，planId={} durationMs={} partialResponseLength={} message={}",
                    planId,
                    Duration.between(start, Instant.now()).toMillis(),
                    response.length(),
                    ex.getMessage(),
                    ex);
            throw ex;
        }
    }

    /**
     * 将预算提醒附加到总结末尾。
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
     * 丰富预算工具结构化结果，给前端直接展示预算风险使用。
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
     * 持久化已生成的计划、日程、条目和预算明细。
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
            log.warn("持久化生成结果失败，planId={} reason=PLAN_NOT_FOUND", planId);
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
        log.info("行程主表更新完成，planId={} status={} totalBudget={}", planId, plan.getStatus(), plan.getTotalBudget());

        persistDaysAndItems(planId, schedules, budget, weatherSummary);
        persistBudget(planId, budget);
    }

    /**
     * 持久化每日行程和行程条目。
     */
    private void persistDaysAndItems(
            Long planId,
            List<DaySchedulePlanner.DaySchedule> schedules,
            BudgetEstimator.BudgetDraft budget,
            String weatherSummary
    ) {
        BigDecimal dailyBudget = budget.total().divide(BigDecimal.valueOf(Math.max(schedules.size(), 1)), 2, RoundingMode.HALF_UP);
        int persistedItemCount = 0;
        for (DaySchedulePlanner.DaySchedule schedule : schedules) {
            TripDay day = new TripDay();
            day.setPlanId(planId);
            day.setDayIndex(schedule.dayIndex());
            day.setDate(schedule.date());
            day.setCity(schedule.city());
            day.setWeatherSummary(weatherSummary);
            day.setDailyBudget(dailyBudget);
            tripDayMapper.insert(day);
            log.info("行程天已保存，planId={} dayId={} dayIndex={} itemCount={}",
                    planId,
                    day.getId(),
                    schedule.dayIndex(),
                    schedule.items().size());

            for (DaySchedulePlanner.PlanItemDraft itemDraft : schedule.items()) {
                TripItem item = new TripItem();
                item.setDayId(day.getId());
                item.setTimeSlot(itemDraft.timeSlot());
                item.setPlaceName(itemDraft.placeName());
                item.setPlaceType(itemDraft.placeType());
                item.setDurationMinutes(itemDraft.durationMinutes());
                item.setTransportSuggestion("建议公共交通优先，必要时使用网约车衔接。");
                item.setEstimatedCost(estimateItemCost(dailyBudget, schedule.items().size()));
                item.setReason(itemDraft.reason());
                tripItemMapper.insert(item);
                persistedItemCount++;
            }
        }
        log.info("行程天和条目保存完成，planId={} dayCount={} itemCount={} dailyBudget={}",
                planId,
                schedules.size(),
                persistedItemCount,
                dailyBudget);
    }

    /**
     * 按单日预算和条目数估算单条活动成本。
     */
    private BigDecimal estimateItemCost(BigDecimal dailyBudget, int itemCount) {
        if (dailyBudget == null || itemCount <= 0) {
            return BigDecimal.ZERO;
        }
        return dailyBudget.divide(BigDecimal.valueOf(itemCount), 2, RoundingMode.HALF_UP);
    }

    /**
     * 保存预算拆分明细。
     */
    private void persistBudget(Long planId, BudgetEstimator.BudgetDraft budget) throws JsonProcessingException {
        BudgetBreakdown breakdown = new BudgetBreakdown();
        breakdown.setPlanId(planId);
        breakdown.setHotelCost(budget.hotel());
        breakdown.setFoodCost(budget.food());
        breakdown.setTransportCost(budget.transport());
        breakdown.setTicketCost(budget.ticket());
        breakdown.setOtherCost(budget.other());
        breakdown.setDetailJson(objectMapper.writeValueAsString(budget));
        budgetBreakdownMapper.insert(breakdown);
        log.info("预算明细保存完成，planId={} budgetId={} total={} insufficient={}",
                planId,
                breakdown.getId(),
                budget.total(),
                budget.insufficient());
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
        ToolCallLog toolCallLog = new ToolCallLog();
        toolCallLog.setUserId(userId);
        toolCallLog.setPlanId(planId);
        toolCallLog.setToolName(toolName);
        toolCallLog.setRequestJson(objectMapper.writeValueAsString(arguments));
        toolCallLog.setResponseSummary(responseSummary);
        toolCallLog.setLatencyMs(Duration.between(start, Instant.now()).toMillis());
        toolCallLog.setSuccess(success);
        toolCallLog.setErrorMessage(errorMessage);
        toolCallLog.setCreatedAt(Instant.now());
        toolCallLogMapper.insert(toolCallLog);
    }

    /**
     * 记录一次大模型调用日志。
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
        LlmCallLog llmCallLog = new LlmCallLog();
        llmCallLog.setUserId(userId);
        llmCallLog.setPlanId(planId);
        llmCallLog.setProvider("spring-ai-openai");
        llmCallLog.setModel(openAiChatProperties.getOptions().getModel());
        llmCallLog.setPromptSummary(truncate(prompt));
        llmCallLog.setResponseSummary(truncate(response));
        llmCallLog.setTokenUsageJson("{}");
        llmCallLog.setLatencyMs(Duration.between(start, Instant.now()).toMillis());
        llmCallLog.setSuccess(success);
        llmCallLog.setErrorMessage(errorMessage);
        llmCallLog.setCreatedAt(Instant.now());
        llmCallLogMapper.insert(llmCallLog);
    }

    /**
     * 规划失败时将主表状态改为 FAILED。
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
            log.info("行程状态更新为失败，planId={}", planId);
        } else {
            log.warn("行程状态更新为失败失败，planId={} reason=PLAN_NOT_FOUND", planId);
        }
    }

    /**
     * 将偏好空值统一为不可变空列表。
     */
    private List<String> safePreferences(TripDtos.CreateTripRequest request) {
        return request.preferences() == null ? List.of() : request.preferences();
    }

    /**
     * 截断日志摘要长度，避免大段提示词或响应撑爆日志。
     */
    private String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    /**
     * 返回稳定的错误消息给前端。
     */
    private String safeErrorMessage(Exception ex) {
        return ex.getMessage() == null ? "行程生成失败" : ex.getMessage();
    }
}
