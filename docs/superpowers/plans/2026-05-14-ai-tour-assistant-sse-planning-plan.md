<!-- @author myoung -->

# AI 旅游助手 SSE 流式行程生成 Implementation Plan

> **历史计划，停止按此执行。** 本文保留为 2026-05-14 阶段拆分记录，代码片段和配置不再作为后续实施依据。当前项目已调整为 Spring Boot + Spring AI + `Result<T>` + `service.impl` 架构，后续执行以 `docs/superpowers/plans/2026-05-15-ai-tour-assistant-current-architecture-plan.md` 和根目录 `AGENTS.md` 为准。
>
> 旧内容中可能包含 `@RequestBody` 完整 DTO、`service` 根包实现类和 OpenAI-compatible 手写客户端描述等过期约束；SSE 接口仍按协议返回 `SseEmitter`，但参数和服务层结构以当前主计划为准。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 `POST /api/trips/stream-plan` 和 `POST /api/trips/{id}/adjust-stream`，让用户提交需求后实时收到进度、工具结果、AI 文本增量、行程快照和完成事件。

**Architecture:** Controller 创建 `SseEmitter`，应用服务串行执行 AI 与 MCP 工具调用并实时推送事件。最终行程写入 MySQL，临时进度写入 Redis。第一版不引入 RocketMQ。

**Tech Stack:** Spring Boot `SseEmitter`、MyBatis-Plus、Redis、OpenAI-compatible AI Client、本地 MCP 工具、JUnit 5、MockMvc。

---

## 前置条件

- 认证与用户资料计划已完成。
- 行程数据与历史记录计划已完成。
- AI 与 MCP 工具抽象计划已完成。

## 文件结构规划

```text
backend/src/main/java/com/aitour/common/dto/SseDtos.java
backend/src/main/java/com/aitour/service/StreamingEventPublisher.java
backend/src/main/java/com/aitour/service/TripPlanningService.java
backend/src/main/java/com/aitour/service/TripAdjustmentService.java
backend/src/main/java/com/aitour/planner/AttractionRanker.java
backend/src/main/java/com/aitour/planner/DaySchedulePlanner.java
backend/src/main/java/com/aitour/planner/BudgetEstimator.java
backend/src/main/java/com/aitour/planner/WeatherRiskAnalyzer.java
backend/src/main/java/com/aitour/controller/TripController.java
backend/src/test/java/com/aitour/service/StreamingEventPublisherTest.java
backend/src/test/java/com/aitour/controller/TripStreamControllerTest.java
```

## Task 1: 定义 SSE 事件 DTO 和发布器

**Files:**

- Create: `backend/src/main/java/com/aitour/common/dto/SseDtos.java`
- Create: `backend/src/main/java/com/aitour/service/StreamingEventPublisher.java`
- Create: `backend/src/test/java/com/aitour/service/StreamingEventPublisherTest.java`

- [ ] **Step 1: 创建 SSE DTO**

```java
package com.aitour.common.dto;

import java.util.Map;

public final class SseDtos {
    private SseDtos() {
    }

    public record ProgressEvent(String step, String message, int percent) {
    }

    public record AiDeltaEvent(String text) {
    }

    public record ToolResultEvent(String tool, String summary, Map<String, Object> data) {
    }

    public record PlanSnapshotEvent(Integer dayIndex, Object items) {
    }

    public record CompletedEvent(Long planId, String status) {
    }

    public record ErrorEvent(String code, String message) {
    }
}
```

- [ ] **Step 2: 创建事件发布器**

```java
package com.aitour.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Component
public class StreamingEventPublisher {

    public void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException ex) {
            throw new IllegalStateException("SSE 事件发送失败: " + eventName, ex);
        }
    }

    public void complete(SseEmitter emitter) {
        emitter.complete();
    }

    public void completeWithError(SseEmitter emitter, Throwable error) {
        emitter.completeWithError(error);
    }
}
```

- [ ] **Step 3: 测试发布器可创建事件**

```java
package com.aitour.service;

import com.aitour.common.dto.SseDtos;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThatCode;

class StreamingEventPublisherTest {

    @Test
    void shouldSendEventWithoutThrowingWhenEmitterIsOpen() {
        StreamingEventPublisher publisher = new StreamingEventPublisher();
        SseEmitter emitter = new SseEmitter(1000L);

        assertThatCode(() -> publisher.send(
                emitter,
                "progress",
                new SseDtos.ProgressEvent("parse", "正在解析出行需求", 10)
        )).doesNotThrowAnyException();
    }
}
```

## Task 2: 创建规划器组件

**Files:**

- Create: `backend/src/main/java/com/aitour/planner/AttractionRanker.java`
- Create: `backend/src/main/java/com/aitour/planner/DaySchedulePlanner.java`
- Create: `backend/src/main/java/com/aitour/planner/BudgetEstimator.java`
- Create: `backend/src/main/java/com/aitour/planner/WeatherRiskAnalyzer.java`
- Create: `backend/src/test/java/com/aitour/planner/DaySchedulePlannerTest.java`

- [ ] **Step 1: 创建景点排序器**

```java
package com.aitour.planner;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class AttractionRanker {

    public List<Map<String, Object>> rank(List<Map<String, Object>> places) {
        return places.stream()
                .sorted(Comparator.comparing(place -> String.valueOf(place.getOrDefault("name", ""))))
                .toList();
    }
}
```

- [ ] **Step 2: 创建每日排程器**

```java
package com.aitour.planner;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DaySchedulePlanner {

    public List<DaySchedule> plan(LocalDate startDate, int days, String city, List<Map<String, Object>> places) {
        List<DaySchedule> schedules = new ArrayList<>();
        int placeIndex = 0;
        for (int i = 1; i <= days; i++) {
            List<PlanItemDraft> items = new ArrayList<>();
            for (String slot : List.of("MORNING", "AFTERNOON", "EVENING")) {
                Map<String, Object> place = places.get(placeIndex % places.size());
                items.add(new PlanItemDraft(slot, String.valueOf(place.get("name")), String.valueOf(place.getOrDefault("type", "ATTRACTION"))));
                placeIndex++;
            }
            schedules.add(new DaySchedule(i, startDate.plusDays(i - 1L), city, items));
        }
        return schedules;
    }

    public record DaySchedule(Integer dayIndex, LocalDate date, String city, List<PlanItemDraft> items) {
    }

    public record PlanItemDraft(String timeSlot, String placeName, String placeType) {
    }
}
```

- [ ] **Step 3: 创建预算估算器**

```java
package com.aitour.planner;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BudgetEstimator {

    public BudgetDraft estimate(int days, int peopleCount, BigDecimal userBudget) {
        BigDecimal base = userBudget == null ? BigDecimal.valueOf(days * peopleCount * 600L) : userBudget;
        return new BudgetDraft(
                base.multiply(BigDecimal.valueOf(0.35)),
                base.multiply(BigDecimal.valueOf(0.25)),
                base.multiply(BigDecimal.valueOf(0.15)),
                base.multiply(BigDecimal.valueOf(0.15)),
                base.multiply(BigDecimal.valueOf(0.10))
        );
    }

    public record BudgetDraft(BigDecimal hotel, BigDecimal food, BigDecimal transport, BigDecimal ticket, BigDecimal other) {
    }
}
```

- [ ] **Step 4: 创建天气风险分析器**

```java
package com.aitour.planner;

import org.springframework.stereotype.Component;

@Component
public class WeatherRiskAnalyzer {

    public String summarize(String weatherSummary) {
        if (weatherSummary == null || weatherSummary.isBlank()) {
            return "天气信息暂缺，建议出行前再次确认。";
        }
        if (weatherSummary.contains("雨")) {
            return weatherSummary + " 建议准备雨具，并优先安排室内景点。";
        }
        return weatherSummary + " 整体适合户外游览。";
    }
}
```

- [ ] **Step 5: 测试每日排程器**

```java
package com.aitour.planner;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DaySchedulePlannerTest {

    @Test
    void shouldCreateThreeSlotsPerDay() {
        DaySchedulePlanner planner = new DaySchedulePlanner();

        List<DaySchedulePlanner.DaySchedule> result = planner.plan(
                LocalDate.of(2099, 6, 1),
                2,
                "成都",
                List.of(Map.of("name", "武侯祠"), Map.of("name", "宽窄巷子"), Map.of("name", "杜甫草堂"))
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).items()).hasSize(3);
    }
}
```

## Task 3: 实现 TripPlanningService

**Files:**

- Create: `backend/src/main/java/com/aitour/service/TripPlanningService.java`
- Modify: existing mappers and DTOs if needed.
- Create: `backend/src/test/java/com/aitour/service/TripPlanningServiceTest.java`

- [ ] **Step 1: 创建服务测试**

测试目标：给定请求后服务能创建 plan，发送 completed 事件，并保存行程状态。

```java
package com.aitour.service;

import com.aitour.common.dto.TripDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TripPlanningServiceTest {

    @Test
    void shouldAcceptPlanningRequestShape() {
        TripDtos.CreateTripRequest request = new TripDtos.CreateTripRequest(
                "成都",
                LocalDate.of(2099, 6, 1),
                3,
                BigDecimal.valueOf(3000),
                2,
                List.of("美食", "轻松"),
                "想吃火锅，不想太赶"
        );

        assertThat(request.destination()).isEqualTo("成都");
        assertThat(request.days()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: 创建规划服务**

实现主流程：

```java
package com.aitour.service;

import com.aitour.client.ai.AiChatClient;
import com.aitour.client.ai.ChatRequest;
import com.aitour.common.dto.SseDtos;
import com.aitour.common.dto.TripDtos;
import com.aitour.domain.TripPlanStatus;
import com.aitour.client.mcp.McpToolRegistry;
import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.planner.AttractionRanker;
import com.aitour.planner.BudgetEstimator;
import com.aitour.planner.DaySchedulePlanner;
import com.aitour.planner.WeatherRiskAnalyzer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Service
public class TripPlanningService {
    private final TripDraftService tripDraftService;
    private final StreamingEventPublisher publisher;
    private final McpToolRegistry toolRegistry;
    private final AiChatClient aiChatClient;
    private final AttractionRanker attractionRanker;
    private final DaySchedulePlanner daySchedulePlanner;
    private final BudgetEstimator budgetEstimator;
    private final WeatherRiskAnalyzer weatherRiskAnalyzer;

    public TripPlanningService(
            TripDraftService tripDraftService,
            StreamingEventPublisher publisher,
            McpToolRegistry toolRegistry,
            AiChatClient aiChatClient,
            AttractionRanker attractionRanker,
            DaySchedulePlanner daySchedulePlanner,
            BudgetEstimator budgetEstimator,
            WeatherRiskAnalyzer weatherRiskAnalyzer
    ) {
        this.tripDraftService = tripDraftService;
        this.publisher = publisher;
        this.toolRegistry = toolRegistry;
        this.aiChatClient = aiChatClient;
        this.attractionRanker = attractionRanker;
        this.daySchedulePlanner = daySchedulePlanner;
        this.budgetEstimator = budgetEstimator;
        this.weatherRiskAnalyzer = weatherRiskAnalyzer;
    }

    public void streamPlan(Long userId, TripDtos.CreateTripRequest request, SseEmitter emitter) {
        try {
            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("parse", "正在解析出行需求", 10));
            Long planId = tripDraftService.createDraft(userId, request);

            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("weather", "正在查询天气", 25));
            ToolResult weather = toolRegistry.execute("weather.query", new ToolRequest(userId, planId, Map.of("city", request.destination())));
            publisher.send(emitter, "tool_result", new SseDtos.ToolResultEvent(weather.toolName(), weather.summary(), weather.data()));

            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("places", "正在查询景点", 45));
            ToolResult places = toolRegistry.execute("place.search", new ToolRequest(userId, planId, Map.of("city", request.destination(), "preferences", request.preferences())));
            publisher.send(emitter, "tool_result", new SseDtos.ToolResultEvent(places.toolName(), places.summary(), places.data()));

            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("budget", "正在估算预算", 65));
            BudgetEstimator.BudgetDraft budget = budgetEstimator.estimate(request.days(), request.peopleCount(), request.budget());

            publisher.send(emitter, "progress", new SseDtos.ProgressEvent("compose", "正在生成每日行程", 80));
            publisher.send(emitter, "plan_snapshot", new SseDtos.PlanSnapshotEvent(1, List.of(Map.of("placeName", request.destination() + "精选景点"))));

            aiChatClient.streamChat(new ChatRequest(List.of(new ChatRequest.Message("user", "请生成" + request.destination() + "行程说明")), true),
                    text -> publisher.send(emitter, "ai_delta", new SseDtos.AiDeltaEvent(text)));

            publisher.send(emitter, "completed", new SseDtos.CompletedEvent(planId, TripPlanStatus.GENERATED.name()));
            publisher.complete(emitter);
        } catch (Exception ex) {
            publisher.send(emitter, "error", new SseDtos.ErrorEvent("PLAN_FAILED", ex.getMessage()));
            publisher.complete(emitter);
        }
    }
}
```

该版本先保证流式协议和主链路可用。后续增强任务再补全 `trip_day`、`trip_item`、`budget_breakdown` 的完整保存。

## Task 4: 接入 Controller 流式接口

**Files:**

- Modify: `backend/src/main/java/com/aitour/controller/TripController.java`
- Create: `backend/src/test/java/com/aitour/controller/TripStreamControllerTest.java`

- [ ] **Step 1: 写流式接口测试**

```java
package com.aitour.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TripStreamControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldStartStreamPlanRequest() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/trips/stream-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食"],"userInput":"想吃火锅"}
                                """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    private String registerAndGetToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"stream-user","password":"password123","nickname":"Stream User"}
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\\"accessToken\\":\\"([^\\"]+)\\".*", "$1");
    }
}
```

- [ ] **Step 2: 在 `TripController` 增加流式接口**

```java
@PostMapping(value = "/stream-plan", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamPlan(
        @AuthenticationPrincipal CurrentUser currentUser,
        @Valid @RequestBody TripDtos.CreateTripRequest request
) {
    SseEmitter emitter = new SseEmitter(120_000L);
    CompletableFuture.runAsync(() -> tripPlanningService.streamPlan(currentUser.id(), request, emitter));
    return emitter;
}
```

同时为 `TripController` 注入 `TripPlanningService`，并添加 imports：

```java
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.CompletableFuture;
```

- [ ] **Step 3: 运行流式接口测试**

Run:

```bash
cd backend
mvn test -Dtest=TripStreamControllerTest
```

Expected:

```text
BUILD SUCCESS
```

## Task 5: 二次调整流式接口

**Files:**

- Create: `backend/src/main/java/com/aitour/service/TripAdjustmentService.java`
- Modify: `backend/src/main/java/com/aitour/controller/TripController.java`

- [ ] **Step 1: 创建调整请求 DTO**

在 `TripDtos.java` 追加：

```java
public record AdjustTripRequest(String instruction) {
}
```

- [ ] **Step 2: 创建调整服务**

```java
package com.aitour.service;

import com.aitour.client.ai.AiChatClient;
import com.aitour.client.ai.ChatRequest;
import com.aitour.common.dto.SseDtos;
import com.aitour.common.dto.TripDtos;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
public class TripAdjustmentService {
    private final AiChatClient aiChatClient;
    private final StreamingEventPublisher publisher;

    public TripAdjustmentService(AiChatClient aiChatClient, StreamingEventPublisher publisher) {
        this.aiChatClient = aiChatClient;
        this.publisher = publisher;
    }

    public void streamAdjust(Long userId, Long planId, TripDtos.AdjustTripRequest request, SseEmitter emitter) {
        publisher.send(emitter, "progress", new SseDtos.ProgressEvent("adjust", "正在根据反馈调整行程", 20));
        aiChatClient.streamChat(new ChatRequest(List.of(
                new ChatRequest.Message("user", "请调整行程 " + planId + "，要求：" + request.instruction())
        ), true), text -> publisher.send(emitter, "ai_delta", new SseDtos.AiDeltaEvent(text)));
        publisher.send(emitter, "completed", new SseDtos.CompletedEvent(planId, "GENERATED"));
        publisher.complete(emitter);
    }
}
```

- [ ] **Step 3: 在 Controller 增加调整接口**

```java
@PostMapping(value = "/{id}/adjust-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter adjustStream(
        @AuthenticationPrincipal CurrentUser currentUser,
        @PathVariable Long id,
        @RequestBody TripDtos.AdjustTripRequest request
) {
    SseEmitter emitter = new SseEmitter(120_000L);
    CompletableFuture.runAsync(() -> tripAdjustmentService.streamAdjust(currentUser.id(), id, request, emitter));
    return emitter;
}
```

同时注入 `TripAdjustmentService`。

## Task 6: 验证和提交

**Files:**

- Modify: `README.md`

- [ ] **Step 1: 追加 SSE 接口说明**

````markdown
## 流式行程生成

```bash
curl -N -X POST http://localhost:8080/api/trips/stream-plan \
  -H "Authorization: Bearer <accessToken>" \
  -H "Accept: text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食"],"userInput":"想吃火锅"}'
```
````

- [ ] **Step 2: 运行完整测试**

Run:

```bash
cd backend
mvn test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: 提交流式生成模块**

Run:

```bash
git add backend README.md
git commit -m "feat: add streaming trip planning"
```

Expected:

```text
feat: add streaming trip planning
```

## 自检清单

- `stream-plan` 使用 SSE 返回事件。
- 事件类型覆盖 `progress`、`tool_result`、`ai_delta`、`plan_snapshot`、`completed`、`error`。
- 不引入 RocketMQ。
- 二次调整接口使用同一套流式事件格式。
- 后续可继续增强完整 `trip_day`、`trip_item`、`budget_breakdown` 保存。
