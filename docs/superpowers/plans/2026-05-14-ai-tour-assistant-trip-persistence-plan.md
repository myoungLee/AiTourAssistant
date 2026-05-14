# AI 旅游助手行程数据与历史记录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现行程请求、行程结果、每日安排、行程项、预算明细的数据库持久化，并提供用户隔离的历史行程查询接口。

**Architecture:** 行程模块采用 MySQL 作为唯一持久化来源。所有查询必须按当前登录用户隔离。第一版只实现数据结构、保存草稿计划、历史列表和详情查询，不接入 AI 生成。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、Flyway、MySQL/H2、Spring Security、JUnit 5。

---

## 前置条件

- 基础工程计划已完成。
- 认证与用户资料计划已完成。
- 可通过 JWT 获取当前用户。

## 文件结构规划

```text
backend/src/main/resources/db/migration/V2__init_trip_tables.sql
backend/src/main/java/com/aitour/domain/TripPlanStatus.java
backend/src/main/java/com/aitour/domain/TripRequest.java
backend/src/main/java/com/aitour/domain/TripPlan.java
backend/src/main/java/com/aitour/domain/TripDay.java
backend/src/main/java/com/aitour/domain/TripItem.java
backend/src/main/java/com/aitour/domain/BudgetBreakdown.java
backend/src/main/java/com/aitour/mapper/TripRequestMapper.java
backend/src/main/java/com/aitour/mapper/TripPlanMapper.java
backend/src/main/java/com/aitour/mapper/TripDayMapper.java
backend/src/main/java/com/aitour/mapper/TripItemMapper.java
backend/src/main/java/com/aitour/mapper/BudgetBreakdownMapper.java
backend/src/main/java/com/aitour/common/dto/TripDtos.java
backend/src/main/java/com/aitour/service/TripQueryService.java
backend/src/main/java/com/aitour/service/TripDraftService.java
backend/src/main/java/com/aitour/controller/TripController.java
backend/src/test/java/com/aitour/controller/TripControllerTest.java
```

## Task 1: 创建行程表结构

**Files:**

- Create: `backend/src/main/resources/db/migration/V2__init_trip_tables.sql`
- Create: `backend/src/test/java/com/aitour/TripMigrationTest.java`

- [ ] **Step 1: 创建 Flyway 迁移**

```sql
create table trip_request (
    id bigint primary key,
    user_id bigint not null,
    user_input text,
    destination varchar(128) not null,
    start_date date not null,
    days int not null,
    budget decimal(12, 2),
    people_count int not null,
    preferences_json text,
    created_at timestamp not null
);

create table trip_plan (
    id bigint primary key,
    user_id bigint not null,
    request_id bigint not null,
    title varchar(255) not null,
    summary text,
    status varchar(32) not null,
    total_budget decimal(12, 2),
    raw_ai_result_json text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table trip_day (
    id bigint primary key,
    plan_id bigint not null,
    day_index int not null,
    date date not null,
    city varchar(128) not null,
    weather_summary varchar(512),
    daily_budget decimal(12, 2)
);

create table trip_item (
    id bigint primary key,
    day_id bigint not null,
    time_slot varchar(32) not null,
    place_name varchar(255) not null,
    place_type varchar(64),
    address varchar(512),
    duration_minutes int,
    transport_suggestion varchar(512),
    estimated_cost decimal(12, 2),
    reason varchar(1024)
);

create table budget_breakdown (
    id bigint primary key,
    plan_id bigint not null,
    hotel_cost decimal(12, 2),
    food_cost decimal(12, 2),
    transport_cost decimal(12, 2),
    ticket_cost decimal(12, 2),
    other_cost decimal(12, 2),
    detail_json text
);

create index idx_trip_request_user_id on trip_request (user_id);
create index idx_trip_plan_user_id on trip_plan (user_id);
create index idx_trip_plan_request_id on trip_plan (request_id);
create index idx_trip_day_plan_id on trip_day (plan_id);
create index idx_trip_item_day_id on trip_item (day_id);
create index idx_budget_breakdown_plan_id on budget_breakdown (plan_id);
```

- [ ] **Step 2: 创建迁移测试**

```java
package com.aitour;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TripMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateTripTables() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('trip_request', 'trip_plan', 'trip_day', 'trip_item', 'budget_breakdown')
                """, Integer.class);

        assertThat(count).isEqualTo(5);
    }
}
```

- [ ] **Step 3: 运行迁移测试**

Run:

```bash
cd backend
mvn test -Dtest=TripMigrationTest
```

Expected:

```text
BUILD SUCCESS
```

## Task 2: 创建领域实体和 Mapper

**Files:**

- Create: `backend/src/main/java/com/aitour/domain/TripPlanStatus.java`
- Create: `backend/src/main/java/com/aitour/domain/TripRequest.java`
- Create: `backend/src/main/java/com/aitour/domain/TripPlan.java`
- Create: `backend/src/main/java/com/aitour/domain/TripDay.java`
- Create: `backend/src/main/java/com/aitour/domain/TripItem.java`
- Create: `backend/src/main/java/com/aitour/domain/BudgetBreakdown.java`
- Create: mapper files under `backend/src/main/java/com/aitour/mapper/`

- [ ] **Step 1: 创建状态枚举**

```java
package com.aitour.domain;

public enum TripPlanStatus {
    PENDING,
    GENERATING,
    GENERATED,
    FAILED,
    CANCELLED
}
```

- [ ] **Step 2: 创建实体字段**

每个实体使用 `@TableName`，字段与数据库表保持驼峰映射：

```java
package com.aitour.domain;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@TableName("trip_request")
public class TripRequest {
    private Long id;
    private Long userId;
    private String userInput;
    private String destination;
    private LocalDate startDate;
    private Integer days;
    private BigDecimal budget;
    private Integer peopleCount;
    private String preferencesJson;
    private Instant createdAt;

    // 生成标准 getter 和 setter
}
```

`TripPlan`、`TripDay`、`TripItem`、`BudgetBreakdown` 按 V2 SQL 字段创建同名驼峰属性，并生成标准 getter 和 setter。

- [ ] **Step 3: 创建 Mapper**

示例：

```java
package com.aitour.mapper;

import com.aitour.domain.TripPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TripPlanMapper extends BaseMapper<TripPlan> {
}
```

为 `TripRequest`、`TripDay`、`TripItem`、`BudgetBreakdown` 创建同结构 Mapper。

## Task 3: 创建 DTO 和草稿行程服务

**Files:**

- Create: `backend/src/main/java/com/aitour/common/dto/TripDtos.java`
- Create: `backend/src/main/java/com/aitour/service/TripDraftService.java`

- [ ] **Step 1: 创建行程 DTO**

```java
package com.aitour.common.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TripDtos {
    private TripDtos() {
    }

    public record CreateTripRequest(
            @NotBlank String destination,
            @FutureOrPresent LocalDate startDate,
            @Min(1) @Max(15) Integer days,
            BigDecimal budget,
            @Min(1) @Max(20) Integer peopleCount,
            List<String> preferences,
            String userInput
    ) {
    }

    public record TripSummaryResponse(
            Long id,
            String title,
            String destination,
            String status,
            BigDecimal totalBudget
    ) {
    }

    public record TripDetailResponse(
            Long id,
            String title,
            String summary,
            String status,
            BigDecimal totalBudget,
            List<DayResponse> days,
            BudgetResponse budget
    ) {
    }

    public record DayResponse(Integer dayIndex, LocalDate date, String city, String weatherSummary, List<ItemResponse> items) {
    }

    public record ItemResponse(String timeSlot, String placeName, String placeType, String address, Integer durationMinutes, String transportSuggestion, BigDecimal estimatedCost, String reason) {
    }

    public record BudgetResponse(BigDecimal hotelCost, BigDecimal foodCost, BigDecimal transportCost, BigDecimal ticketCost, BigDecimal otherCost) {
    }
}
```

- [ ] **Step 2: 创建草稿服务**

```java
package com.aitour.service;

import com.aitour.common.dto.TripDtos;
import com.aitour.domain.TripPlan;
import com.aitour.domain.TripPlanStatus;
import com.aitour.common.entity.TripRequest;
import com.aitour.mapper.TripPlanMapper;
import com.aitour.mapper.TripRequestMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TripDraftService {
    private final TripRequestMapper tripRequestMapper;
    private final TripPlanMapper tripPlanMapper;
    private final ObjectMapper objectMapper;

    public TripDraftService(TripRequestMapper tripRequestMapper, TripPlanMapper tripPlanMapper, ObjectMapper objectMapper) {
        this.tripRequestMapper = tripRequestMapper;
        this.tripPlanMapper = tripPlanMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Long createDraft(Long userId, TripDtos.CreateTripRequest request) throws Exception {
        Instant now = Instant.now();
        TripRequest tripRequest = new TripRequest();
        tripRequest.setId(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        tripRequest.setUserId(userId);
        tripRequest.setUserInput(request.userInput());
        tripRequest.setDestination(request.destination());
        tripRequest.setStartDate(request.startDate());
        tripRequest.setDays(request.days());
        tripRequest.setBudget(request.budget());
        tripRequest.setPeopleCount(request.peopleCount());
        tripRequest.setPreferencesJson(objectMapper.writeValueAsString(request.preferences()));
        tripRequest.setCreatedAt(now);
        tripRequestMapper.insert(tripRequest);

        TripPlan plan = new TripPlan();
        plan.setId(Math.abs(UUID.randomUUID().getLeastSignificantBits()));
        plan.setUserId(userId);
        plan.setRequestId(tripRequest.getId());
        plan.setTitle(request.destination() + request.days() + "日智能行程");
        plan.setStatus(TripPlanStatus.PENDING.name());
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        tripPlanMapper.insert(plan);
        return plan.getId();
    }
}
```

## Task 4: 历史行程查询服务和接口

**Files:**

- Create: `backend/src/main/java/com/aitour/service/TripQueryService.java`
- Create: `backend/src/main/java/com/aitour/controller/TripController.java`
- Create: `backend/src/test/java/com/aitour/controller/TripControllerTest.java`

- [ ] **Step 1: 写接口测试**

```java
package com.aitour.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TripControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateDraftAndQueryTrips() throws Exception {
        String token = registerAndGetToken();

        String response = mockMvc.perform(post("/api/trips/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食"],"userInput":"想吃火锅"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String planId = response.replaceAll(".*\\"planId\\":([0-9]+).*", "$1");

        mockMvc.perform(get("/api/trips").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("成都3日智能行程"));

        mockMvc.perform(get("/api/trips/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.parseLong(planId)));
    }

    private String registerAndGetToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"trip-user","password":"password123","nickname":"Trip User"}
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\\"accessToken\\":\\"([^\\"]+)\\".*", "$1");
    }
}
```

- [ ] **Step 2: 创建查询服务**

实现要点：

```java
package com.aitour.service;

import com.aitour.common.dto.TripDtos;
import com.aitour.domain.TripPlan;
import com.aitour.common.exception.ApiException;
import com.aitour.mapper.TripPlanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TripQueryService {
    private final TripPlanMapper tripPlanMapper;

    public TripQueryService(TripPlanMapper tripPlanMapper) {
        this.tripPlanMapper = tripPlanMapper;
    }

    public List<TripDtos.TripSummaryResponse> listTrips(Long userId) {
        return tripPlanMapper.selectList(new LambdaQueryWrapper<TripPlan>()
                        .eq(TripPlan::getUserId, userId)
                        .orderByDesc(TripPlan::getCreatedAt))
                .stream()
                .map(plan -> new TripDtos.TripSummaryResponse(plan.getId(), plan.getTitle(), "", plan.getStatus(), plan.getTotalBudget()))
                .toList();
    }

    public TripDtos.TripDetailResponse getTrip(Long userId, Long planId) {
        TripPlan plan = tripPlanMapper.selectOne(new LambdaQueryWrapper<TripPlan>()
                .eq(TripPlan::getId, planId)
                .eq(TripPlan::getUserId, userId));
        if (plan == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "行程不存在");
        }
        return new TripDtos.TripDetailResponse(plan.getId(), plan.getTitle(), plan.getSummary(), plan.getStatus(), plan.getTotalBudget(), List.of(), null);
    }
}
```

- [ ] **Step 3: 创建 Controller**

```java
package com.aitour.controller;

import com.aitour.common.dto.TripDtos;
import com.aitour.service.TripDraftService;
import com.aitour.service.TripQueryService;
import com.aitour.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final TripDraftService tripDraftService;
    private final TripQueryService tripQueryService;

    public TripController(TripDraftService tripDraftService, TripQueryService tripQueryService) {
        this.tripDraftService = tripDraftService;
        this.tripQueryService = tripQueryService;
    }

    @PostMapping("/draft")
    public Map<String, Long> createDraft(@AuthenticationPrincipal CurrentUser currentUser, @Valid @RequestBody TripDtos.CreateTripRequest request) throws Exception {
        return Map.of("planId", tripDraftService.createDraft(currentUser.id(), request));
    }

    @GetMapping
    public List<TripDtos.TripSummaryResponse> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return tripQueryService.listTrips(currentUser.id());
    }

    @GetMapping("/{id}")
    public TripDtos.TripDetailResponse detail(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
        return tripQueryService.getTrip(currentUser.id(), id);
    }
}
```

- [ ] **Step 4: 运行行程接口测试**

Run:

```bash
cd backend
mvn test -Dtest=TripControllerTest
```

Expected:

```text
BUILD SUCCESS
```

## Task 5: 完整验证和提交

**Files:**

- Modify: `README.md`

- [ ] **Step 1: 追加行程接口说明**

````markdown
## 行程草稿接口

创建草稿：

```bash
curl -X POST http://localhost:8080/api/trips/draft \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食"],"userInput":"想吃火锅"}'
```

查询历史：

```bash
curl http://localhost:8080/api/trips \
  -H "Authorization: Bearer <accessToken>"
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

- [ ] **Step 3: 提交行程持久化模块**

Run:

```bash
git add backend README.md
git commit -m "feat: add trip persistence and history"
```

Expected:

```text
feat: add trip persistence and history
```

## 自检清单

- 行程数据表通过 Flyway 创建。
- 所有行程查询按 `userId` 隔离。
- 已支持创建草稿、历史列表、详情查询。
- 未引入 AI、MCP 或 SSE，保持该阶段边界清晰。
