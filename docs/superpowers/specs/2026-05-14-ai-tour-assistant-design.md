# Java AI 旅游助手项目设计

## 1. 项目目标

构建一个作品集级别的 Java AI 旅游助手项目。系统采用 Spring Boot 后端和 Vue 3 前端，用户登录后可以提交出行需求，系统根据目的地、日期、天数、预算、偏好和同行人数，流式生成包含景点推荐、天气提醒、路线建议和预算估算的旅行行程。

第一版目标是完整跑通核心体验，同时保持架构清晰、实现可控、便于本地演示和后续扩展。

## 2. 已确认范围

### 2.1 MVP 范围

- 用户注册、登录、退出登录和用户资料管理。
- 基于 JWT 的接口认证。
- 持久化用户信息和旅行偏好。
- AI 行程生成支持流式返回。
- 大模型接入使用 Spring AI 官方 OpenAI ChatClient，通过 OpenAI-compatible 中转地址访问模型。
- 默认接入真实外部 MCP Server，用于天气、搜索、景点、地图和路线工具。
- 本地 MCP 占位工具不再构造模拟数据，未配置真实服务时显式失败。
- MySQL 持久化用户、行程、预算、调用日志和缓存记录。
- Redis 用于登录态、用户资料缓存、限流、工具缓存和生成进度。
- 前端采用 Vue 3 + Vite + Element Plus。
- 支持历史行程列表和行程详情页。
- 支持基于已有行程的流式二次调整。

### 2.2 MVP 暂不实现

- RocketMQ 不进入第一版主链路。
- 不做多租户企业权限。
- 不做支付、订单、酒店预订或门票预订。
- 不做完整生产部署自动化。
- 不做原生移动端。

RocketMQ 作为后续扩展项保留。当系统需要分布式任务处理、失败补偿、削峰填谷或更强的异步重试能力时，再引入消息队列。

## 3. 技术栈

### 3.1 后端

- JDK 21。
- Spring Boot 3。
- 内嵌 Tomcat。
- Spring Security。
- JWT。
- MyBatis-Plus。
- Flyway。
- Druid。
- MySQL。
- Redis。
- Jackson。
- Spring `SseEmitter`。
- 必要时使用 Spring `TaskExecutor` 处理应用内异步任务。

### 3.2 前端

- Vue 3。
- Vite。
- Element Plus。
- Pinia。
- Vue Router。
- Axios 用于普通 REST 请求。
- `fetch + ReadableStream` 用于带认证的 POST 流式请求。

### 3.3 AI 与工具

- Spring AI OpenAI ChatClient。
- OpenAI-compatible 中转地址。
- 大模型流式响应。
- 本地 MCP 风格工具实现。
- 外部 MCP Server 适配层。

## 4. 总体架构

```text
Vue 3 + Vite + Element Plus
        |
        | REST + SSE
        v
Spring Boot 3 后端
        |
        |-- 认证与用户模块
        |-- 行程规划应用层
        |-- AI 编排层
        |     |-- Spring AI ChatClient
        |     |-- Prompt 模板
        |     |-- JSON 解析与校验
        |
        |-- 工具层
        |     |-- 本地 MCP 风格工具
        |     |-- 外部 MCP Server 适配器
        |
        |-- 规划层
        |     |-- 景点排序
        |     |-- 每日排程
        |     |-- 天气风险分析
        |     |-- 预算估算
        |
        |-- 基础设施层
              |-- MySQL
              |-- Redis
              |-- Spring AI OpenAI Starter
              |-- 统一异常处理
```

后端采用模块化单体架构。第一版不拆微服务，降低开发和部署复杂度；但通过清晰的包结构和接口抽象，让后续拆分 AI 服务、工具服务或任务服务时成本可控。

## 5. 后端包结构

```text
backend/src/main/java/com/aitour/
  controller/
    AuthController.java
    UserController.java
    TripController.java
    ToolController.java

  service/
    AuthService.java
    UserProfileService.java
    TripPlanningService.java
    TripAdjustmentService.java
    StreamingEventPublisher.java

  service/impl/
    AuthServiceImpl.java
    UserProfileServiceImpl.java
    TripPlanningServiceImpl.java
    TripAdjustmentServiceImpl.java
    StreamingEventPublisherImpl.java

  client/
    AiChatClient.java
    SpringAiChatClient.java
    TravelTool.java
    ToolRequest.java
    ToolResult.java
    McpToolRegistry.java
    LocalToolAdapter.java
    ExternalMcpToolAdapter.java

  config/
    SecurityConfig.java

  common/
    dto/
    entity/
    exception/

  domain/
    AttractionRanker.java
    DaySchedulePlanner.java
    BudgetEstimator.java
    WeatherRiskAnalyzer.java

  mapper/
    UserMapper.java
    TripPlanMapper.java
    ToolCallLogMapper.java
```

Controller 只负责请求校验、认证上下文读取和响应转换，不直接拼 Prompt、不直接调用外部工具、不直接写复杂规划逻辑。

## 6. 用户认证与用户信息

系统必须登录后使用行程规划能力。

认证方案：

- 用户密码使用 BCrypt 加密存储。
- 登录成功后签发 JWT access token。
- Redis 保存 refresh token 或登录态辅助信息。
- 用户退出登录后，将 token 标识写入 Redis 黑名单。
- 受保护接口统一使用 `Authorization: Bearer <token>`。

用户数据：

- `users` 保存账号基础信息。
- `user_profile` 保存旅行偏好。
- 用户资料可缓存在 Redis 中，MySQL 始终是最终数据源。

## 7. AI 接入设计

后端定义供应商无关的 AI 客户端接口：

```java
public interface AiChatClient {
    String chat(ChatRequest request);

    void streamChat(ChatRequest request, StreamCallback callback);
}
```

默认实现使用 Spring AI 官方 OpenAI Starter。中转地址、模型名和推理等级写在配置文件中，只有 Key 从环境变量读取：

```yaml
spring:
  ai:
    openai:
      base-url: https://www.micuapi.ai
      api-key: ${AI_API_KEY:}
      chat:
        completions-path: /v1/chat/completions
        options:
          model: gpt-5.4
          reasoning-effort: high
```

AI 层不能从 Controller 直接调用。所有行程相关 AI 调用都通过 `TripPlanningService` 或其他应用服务发起。

## 8. MCP 工具设计

第一版保留本地工具接口和外部 MCP Server 适配层，但运行时默认使用外部 MCP Server。

统一工具接口：

```java
public interface TravelTool {
    String name();

    ToolResult execute(ToolRequest request);
}
```

初始工具：

- `weather.query`：查询目的地天气。
- `place.search`：查询景点、餐厅、酒店和城市 POI。
- `route.plan`：估算地点之间的路线和交通时间。
- `web.search`：补充开放时间、门票说明、热度信息等。
- `budget.estimate`：估算住宿、餐饮、门票、交通和其他费用。

本地工具只作为禁用保护存在，不提供确定性模拟数据。外部 MCP 工具通过配置启用，不影响行程规划主流程代码。

## 9. 流式行程生成

主体验采用流式输出，提升用户等待体验。

接口：

```http
POST /api/trips/stream-plan
Accept: text/event-stream
Authorization: Bearer <token>
```

请求示例：

```json
{
  "destination": "成都",
  "startDate": "2026-06-01",
  "days": 3,
  "budget": 3000,
  "peopleCount": 2,
  "preferences": ["美食", "历史", "轻松"],
  "userInput": "想吃火锅，不想太赶，最好安排一些市区景点"
}
```

事件类型：

- `progress`：规划阶段进度。
- `ai_delta`：AI 文本增量。
- `tool_result`：工具查询摘要。
- `plan_snapshot`：中间行程快照。
- `completed`：最终行程 ID 和状态。
- `error`：失败原因。

事件示例：

```text
event: progress
data: {"step":"parse","message":"正在解析出行需求","percent":10}

event: tool_result
data: {"tool":"weather.query","summary":"成都 6 月 1 日小雨，建议安排室内景点"}

event: ai_delta
data: {"text":"根据你的偏好，我会优先安排市区美食和历史文化景点..."}

event: plan_snapshot
data: {"dayIndex":1,"items":[{"timeSlot":"MORNING","placeName":"武侯祠"}]}

event: completed
data: {"planId":101,"status":"GENERATED"}
```

## 10. 主流程

```text
用户提交出行需求
  -> TripController 校验认证和请求体
  -> TripPlanningService 创建 trip_request 和 trip_plan
  -> SSE 推送 progress: parse
  -> AI 解析或规范化用户需求
  -> MCP 工具查询景点、天气、路线和预算信息
  -> Planner 排序景点并生成每日安排
  -> AI 流式输出说明文本
  -> Planner 形成最终 TripPlan
  -> MySQL 保存行程、每日安排、预算和日志
  -> Redis 保存临时进度和必要缓存
  -> SSE 推送 completed，返回 planId
```

任何阶段失败时，服务尽量推送 `error` 事件，将 `trip_plan.status` 标记为 `FAILED`，并落库必要的诊断信息。

## 11. REST API 设计

认证接口：

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/users/me
PUT  /api/users/me/profile
```

行程接口：

```http
POST   /api/trips/stream-plan
GET    /api/trips
GET    /api/trips/{id}
POST   /api/trips/{id}/adjust-stream
DELETE /api/trips/{id}
```

工具与观测接口：

```http
GET /api/tools/status
GET /api/tools/calls
```

所有行程和工具调用日志接口默认按当前用户隔离。后续如果增加管理员能力，再单独设计管理端权限。

## 12. 数据库设计

推荐持久化方案：

- MySQL 作为主数据库。
- Flyway 管理数据库版本。
- MyBatis-Plus 负责数据访问。
- MySQL `JSON` 字段保存 AI 和工具返回的动态结构。

用户表：

```text
users
- id
- username
- password_hash
- nickname
- avatar_url
- phone
- email
- status
- created_at
- updated_at

user_profile
- id
- user_id
- gender
- age_range
- travel_style
- default_budget_level
- preferred_transport
- preferences_json
- created_at
- updated_at
```

行程表：

```text
trip_request
- id
- user_id
- user_input
- destination
- start_date
- days
- budget
- people_count
- preferences_json
- created_at

trip_plan
- id
- user_id
- request_id
- title
- summary
- status
- total_budget
- raw_ai_result_json
- created_at
- updated_at

trip_day
- id
- plan_id
- day_index
- date
- city
- weather_summary
- daily_budget

trip_item
- id
- day_id
- time_slot
- place_name
- place_type
- address
- duration_minutes
- transport_suggestion
- estimated_cost
- reason

budget_breakdown
- id
- plan_id
- hotel_cost
- food_cost
- transport_cost
- ticket_cost
- other_cost
- detail_json
```

日志和缓存表：

```text
llm_call_log
- id
- user_id
- plan_id
- provider
- model
- prompt_summary
- response_summary
- token_usage_json
- latency_ms
- success
- error_message
- created_at

tool_call_log
- id
- user_id
- plan_id
- tool_name
- request_json
- response_summary
- latency_ms
- success
- error_message
- created_at

place_cache
weather_cache
route_cache
```

`trip_plan.status` 可选值：

```text
PENDING
GENERATING
GENERATED
FAILED
CANCELLED
```

## 13. Redis 设计

Redis 职责：

- 登录态和 token 黑名单。
- 用户资料缓存。
- AI 调用限流。
- 临时生成进度。
- 天气、景点和路线缓存。
- 工具状态缓存。

建议 key：

```text
auth:refresh:{userId}:{tokenId}
auth:blacklist:{tokenId}
user:profile:{userId}
trip:stream:progress:{planId}
rate:limit:ai:{userId}
tool:status
weather:cache:{city}:{date}
place:cache:{city}:{keyword}
route:cache:{from}:{to}
```

Redis 只作为缓存和协作层，不能替代 MySQL 的持久化职责。

## 14. 前端设计

路由：

```text
/login
/register
/home
/plans
/plans/:id
/profile
/tools
```

核心组件：

```text
TripRequestForm.vue
StreamingPlanPanel.vue
ProgressTimeline.vue
DayPlanTabs.vue
BudgetBreakdown.vue
WeatherAlert.vue
ToolStatusPanel.vue
```

`/home` 页面：

- 左侧：结构化出行需求表单和自然语言补充输入。
- 右侧：流式生成面板。

`/plans/:id` 页面：

- 顶部：标题、目的地、日期范围和总预算。
- 主体：Day 1、Day 2、Day 3 分 Tab 展示。
- 侧边：天气、预算、路线建议。
- 底部：行程调整输入框，支持流式返回。

前端流式请求使用 `fetch + ReadableStream`，原因是原生 `EventSource` 对带认证的 POST 请求支持不够直接。

## 15. 错误处理

后端错误类型：

- 认证失败。
- 参数校验失败。
- AI 超时或供应商错误。
- MCP 工具不可用。
- 工具响应解析失败。
- 规划算法失败。
- 数据库持久化失败。

SSE 流中能返回的错误通过 `error` 事件返回。后端同时将行程状态更新为 `FAILED`，并记录可诊断日志。

失败处理策略：

- 外部 MCP 工具失败、未配置或响应字段缺失时，直接返回可追踪错误并标记行程失败。
- 路线数据不可用时不再使用本地默认交通时间估算，要求真实路线工具返回结果。
- AI JSON 解析失败时，允许使用修复 Prompt 重试一次，再决定失败。

## 16. 测试策略

后端测试：

- Planner、预算估算器、天气风险分析器、JSON 解析器的单元测试。
- 认证、行程创建、行程详情查询的集成测试。
- SSE 事件顺序和事件结构的契约测试。
- LLM Client 和 MCP Tool Registry 的 Mock 测试。

前端测试：

- 出行需求表单和行程展示组件测试。
- 流式事件解析测试。
- 登录、生成行程、查看详情、调整行程的手动冒烟测试。

## 17. 实施阶段

### 阶段 1：项目基础

- 创建 Spring Boot 后端和 Vue 前端。
- 接入 MySQL、Redis、Flyway、MyBatis-Plus。
- 实现认证和用户资料。

### 阶段 2：行程持久化

- 创建行程请求、行程结果、每日安排、行程项和预算表。
- 实现历史行程列表和详情查询。

### 阶段 3：AI 与工具抽象

- 实现 OpenAI-compatible 客户端。
- 实现外部 MCP Server 适配层，并禁用本地模拟工具。
- 实现工具注册表和调用日志。

### 阶段 4：流式规划

- 实现 `POST /api/trips/stream-plan`。
- 实现 SSE 事件发布器。
- 前端接入流式生成面板。

### 阶段 5：详情和调整

- 实现行程详情页。
- 实现流式调整接口。
- 保存调整后的行程版本或更新后的行程结果。

### 阶段 6：打磨和观测

- 实现工具状态页。
- 展示缓存状态和错误状态。
- 优化 Prompt、失败暴露策略和前端文案。

## 18. 后续扩展

- 引入 RocketMQ 处理分布式行程规划任务。
- 仅在需要双向实时协作时引入 WebSocket。
- 增加管理后台，用于查看工具日志和模型配置。
- 接入真实外部 MCP Server，实现地图、天气和搜索能力。
- 增加部署脚本和生产环境配置。
