/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.client.ai.AiChatClient;
import com.aitour.client.mcp.McpToolRegistry;
import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.common.dto.TripDtos;
import com.aitour.common.entity.LlmCallLog;
import com.aitour.common.entity.ToolCallLog;
import com.aitour.common.entity.TripDay;
import com.aitour.common.entity.TripPlan;
import com.aitour.domain.TripPlanStatus;
import com.aitour.mapper.LlmCallLogMapper;
import com.aitour.mapper.ToolCallLogMapper;
import com.aitour.mapper.TripDayMapper;
import com.aitour.mapper.TripPlanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 验证流式规划服务可以创建并持久化生成完成的行程，以及关键调用日志。
 *
 * @author myoung
 */
@SpringBootTest
class TripPlanningServiceTest {

    @Autowired
    private TripPlanningService tripPlanningService;

    @MockitoBean
    private AiChatClient aiChatClient;

    @MockitoBean
    private McpToolRegistry toolRegistry;

    @Autowired
    private TripPlanMapper tripPlanMapper;

    @Autowired
    private TripDayMapper tripDayMapper;

    @Autowired
    private ToolCallLogMapper toolCallLogMapper;

    @Autowired
    private LlmCallLogMapper llmCallLogMapper;

    /**
     * 给定完整出行需求后，应生成 GENERATED 状态的行程并保存每日安排。
     */
    @Test
    void shouldPersistGeneratedPlanWhenStreamingCompletes() {
        mockToolRegistrySuccessResponses();
        mockAiStreamResponse();
        TripDtos.CreateTripRequest request = new TripDtos.CreateTripRequest(
                "成都",
                LocalDate.of(2099, 6, 1),
                2,
                BigDecimal.valueOf(3000),
                2,
                List.of("美食", "轻松"),
                "想吃火锅，不想太赶"
        );

        tripPlanningService.streamPlan(10001L, request, new SseEmitter(1000L));

        TripPlan plan = tripPlanMapper.selectOne(new LambdaQueryWrapper<TripPlan>()
                .eq(TripPlan::getUserId, 10001L)
                .orderByDesc(TripPlan::getCreatedAt)
                .last("limit 1"));
        assertThat(plan).isNotNull();
        assertThat(plan.getStatus()).isEqualTo(TripPlanStatus.GENERATED.name());
        assertThat(tripDayMapper.selectCount(new LambdaQueryWrapper<TripDay>().eq(TripDay::getPlanId, plan.getId())).intValue())
                .isEqualTo(2);
    }

    /**
     * 当预算明显不足时，应在生成结果中写入明确预算提醒，并完整落库工具和大模型调用日志。
     */
    @Test
    void shouldPersistBudgetWarningAndCallLogsWhenBudgetIsInsufficient() {
        mockToolRegistrySuccessResponses();
        mockAiStreamResponse();
        TripDtos.CreateTripRequest request = new TripDtos.CreateTripRequest(
                "成都",
                LocalDate.of(2099, 7, 1),
                3,
                BigDecimal.valueOf(1000),
                2,
                List.of("轻松", "美食"),
                "希望行程不要太赶"
        );

        tripPlanningService.streamPlan(10002L, request, new SseEmitter(1000L));

        TripPlan plan = tripPlanMapper.selectOne(new LambdaQueryWrapper<TripPlan>()
                .eq(TripPlan::getUserId, 10002L)
                .orderByDesc(TripPlan::getCreatedAt)
                .last("limit 1"));
        assertThat(plan).isNotNull();
        assertThat(plan.getSummary()).contains("预算提醒");

        List<ToolCallLog> toolLogs = toolCallLogMapper.selectList(new LambdaQueryWrapper<ToolCallLog>()
                .eq(ToolCallLog::getPlanId, plan.getId())
                .orderByAsc(ToolCallLog::getCreatedAt));
        assertThat(toolLogs).hasSize(4);
        assertThat(toolLogs).allSatisfy(log -> {
            assertThat(log.getToolName()).isNotBlank();
            assertThat(log.getRequestJson()).isNotBlank();
            assertThat(log.getResponseSummary()).isNotBlank();
            assertThat(log.getLatencyMs()).isNotNull();
            assertThat(log.getSuccess()).isTrue();
            assertThat(log.getErrorMessage()).isNull();
        });

        List<LlmCallLog> llmLogs = llmCallLogMapper.selectList(new LambdaQueryWrapper<LlmCallLog>()
                .eq(LlmCallLog::getPlanId, plan.getId())
                .orderByAsc(LlmCallLog::getCreatedAt));
        assertThat(llmLogs).hasSize(1);
        assertThat(llmLogs.getFirst().getProvider()).isEqualTo("spring-ai-openai");
        assertThat(llmLogs.getFirst().getModel()).isEqualTo("gpt-5.4");
        assertThat(llmLogs.getFirst().getPromptSummary()).isNotBlank();
        assertThat(llmLogs.getFirst().getResponseSummary()).contains("测试 AI 摘要");
        assertThat(llmLogs.getFirst().getLatencyMs()).isNotNull();
        assertThat(llmLogs.getFirst().getSuccess()).isTrue();
        assertThat(llmLogs.getFirst().getErrorMessage()).isNull();
    }

    /**
     * 景点工具没有返回真实景点时，应标记行程失败而不是生成默认景点。
     */
    @Test
    void shouldMarkPlanFailedWhenPlaceToolReturnsEmptyPlaces() {
        mockToolRegistryEmptyPlacesResponse();
        TripDtos.CreateTripRequest request = new TripDtos.CreateTripRequest(
                "成都",
                LocalDate.of(2099, 8, 1),
                2,
                BigDecimal.valueOf(3000),
                2,
                List.of("美食"),
                "想吃火锅"
        );

        tripPlanningService.streamPlan(10003L, request, new SseEmitter(1000L));

        TripPlan plan = tripPlanMapper.selectOne(new LambdaQueryWrapper<TripPlan>()
                .eq(TripPlan::getUserId, 10003L)
                .orderByDesc(TripPlan::getCreatedAt)
                .last("limit 1"));
        assertThat(plan).isNotNull();
        assertThat(plan.getStatus()).isEqualTo(TripPlanStatus.FAILED.name());
        assertThat(tripDayMapper.selectCount(new LambdaQueryWrapper<TripDay>().eq(TripDay::getPlanId, plan.getId())).intValue())
                .isZero();
    }

    /**
     * 使用测试替身提供稳定的 MCP 工具响应，避免测试依赖运行时占位数据。
     */
    private void mockToolRegistrySuccessResponses() {
        when(toolRegistry.mode()).thenReturn("external-test");
        when(toolRegistry.execute(eq("weather.query"), any(ToolRequest.class)))
                .thenReturn(new ToolResult("weather.query", true, "成都天气晴朗，适合户外游览。", Map.of(
                        "city", "成都",
                        "condition", "晴"
                )));
        when(toolRegistry.execute(eq("place.search"), any(ToolRequest.class)))
                .thenReturn(new ToolResult("place.search", true, "返回成都真实测试景点。", Map.of(
                        "places", List.of(
                                Map.of("name", "成都武侯祠", "type", "culture", "durationMinutes", 120),
                                Map.of("name", "锦里古街", "type", "food", "durationMinutes", 90),
                                Map.of("name", "杜甫草堂", "type", "culture", "durationMinutes", 120)
                        )
                )));
        when(toolRegistry.execute(eq("route.plan"), any(ToolRequest.class)))
                .thenReturn(new ToolResult("route.plan", true, "成都武侯祠到锦里古街步行约 15 分钟。", Map.of(
                        "transport", "步行",
                        "durationMinutes", 15
                )));
        when(toolRegistry.execute(eq("budget.estimate"), any(ToolRequest.class)))
                .thenReturn(new ToolResult("budget.estimate", true, "外部 MCP 已返回预算估算。", Map.of(
                        "hotel", BigDecimal.valueOf(1000),
                        "food", BigDecimal.valueOf(700),
                        "transport", BigDecimal.valueOf(300),
                        "ticket", BigDecimal.valueOf(400),
                        "other", BigDecimal.valueOf(200),
                        "total", BigDecimal.valueOf(2600)
                )));
    }

    /**
     * 使用测试替身模拟外部景点工具返回空列表，验证业务会真实失败。
     */
    private void mockToolRegistryEmptyPlacesResponse() {
        when(toolRegistry.mode()).thenReturn("external-test");
        when(toolRegistry.execute(eq("weather.query"), any(ToolRequest.class)))
                .thenReturn(new ToolResult("weather.query", true, "成都天气晴朗，适合户外游览。", Map.of("city", "成都")));
        when(toolRegistry.execute(eq("place.search"), any(ToolRequest.class)))
                .thenReturn(new ToolResult("place.search", true, "外部 MCP 未返回景点。", Map.of("places", List.of())));
    }

    /**
     * 使用测试替身隔离真实 Spring AI 模型调用，避免单元测试访问外部中转服务。
     */
    private void mockAiStreamResponse() {
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(1);
            onDelta.accept("测试 AI 摘要");
            return null;
        }).when(aiChatClient).streamChat(any(), any());
    }
}
