<!-- @author myoung -->

# AI 旅游助手当前架构执行计划 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于当前已经确定的 Spring Boot + Vue 前后端分离架构，继续完成 AI 旅游助手的可用闭环：登录后提交出行需求，系统通过 AI 与 MCP 工具流式生成行程，并持久化用户、行程、工具调用和预算数据。

**Architecture:** 后端是单体 Spring Boot 应用，使用内嵌 Tomcat、Druid、MyBatis-Plus、Flyway、MySQL 和 Redis；Controller 只接收字段参数并统一返回 `Result<T>`，SSE 接口按协议返回 `SseEmitter`；Service 根包只放接口，实现类统一放在 `service.impl`。AI 调用使用 Spring AI 官方 OpenAI ChatClient，底层通过 OpenAI-compatible 中转地址访问 `gpt-5.4`，推理等级为 `high`；MCP 工具默认使用本地内置实现，配置后可切换外部 MCP Server；第一版不引入 RocketMQ。

**Tech Stack:** JDK 21、Spring Boot 3、Spring AI、embedded Tomcat、Druid、MyBatis-Plus、Flyway、MySQL、Redis、Spring Security、springdoc-openapi、Vue 3、Vite、Element Plus、Pinia、Axios、ReadableStream。

---

## 计划状态说明

本计划从 2026-05-15 起作为后续开发主线。`2026-05-14-*` 计划文件保留为历史拆分参考，其中部分代码片段已经不符合当前项目约束，例如：

- Controller 使用 `@RequestBody` 接收完整 DTO。
- 普通 REST 接口直接返回业务对象或 `Map`。
- Service 根包直接放实现类。
- 前端直接读取 Axios 的原始 `data` 作为业务对象。
- 部分配置项通过环境变量读取，而当前约定是只有模型 Key 从环境变量读取。
- AI 接入使用 JDK `HttpClient`、`OpenAiCompatibleChatClient` 或 `AiProperties`。
- 通过 `application-dev.yml` 和 dev profile 作为本机默认启动入口。

上述历史计划已经全部加上“停止按此执行”说明。后续执行时，以本文档和根目录 `AGENTS.md` 为准。

## 阶段状态

- [x] Task 0: 收尾当前架构调整。已完成 `Result<T>`、`service.impl`、Controller 字段参数、Spring AI 接入和主计划更新，并已推送。
- [x] Task 1: 前端适配统一 `Result<T>` 和字段参数。已完成并推送。
- [x] Task 2: 完善前端核心页面。已完成并推送。
- [x] Task 3: Redis 登录态与缓存使用落地。已完成并推送。
- [x] Task 4: MCP 工具模式配置化增强。已完成本地/外部 MCP 模式切换与真实外部 MCP 调用能力，并已推送。
- [x] Task 5: 行程生成质量和持久化细化。已完成预算提醒、节奏调整、同日去重和调用日志测试补齐，并已推送。
- [x] Task 6: Swagger 和接口测试文档补齐。已补齐字段参数文档校验、明确响应 schema 和 Swagger 使用说明，并已推送。
- [ ] Task 7: 本地联调和交付清单。

## 当前基线

已经完成的内容：

- 后端基础工程：JDK 21、Spring Boot、内嵌 Tomcat、Druid、Flyway、MyBatis-Plus。
- 本地中间件配置：MySQL 使用 `root/young`，Redis 使用密码 `young`。
- 本机默认启动方式：直接使用 `backend/src/main/resources/application.yml`，不再以 dev profile 作为默认入口。
- 认证与用户资料：注册、登录、当前用户、用户偏好更新。
- 行程持久化：行程草稿、历史列表、详情查询、行程生成结果相关表。
- AI 与 MCP：Spring AI OpenAI ChatClient，本地 MCP 风格天气、景点、路线、预算工具，外部 MCP 适配入口。
- SSE：流式行程生成和二次调整接口。
- Swagger：通过 springdoc-openapi 生成接口测试文档。
- 接口契约：普通 REST 接口统一返回 `Result<T>`，业务数据放在 `data` 字段。
- 分层结构：`service` 放接口，`service.impl` 放实现。
- 前端现状：仅有基础首页、基础路由和 `http.ts` 骨架，尚未适配 `Result<T>`、认证态、业务 API 模块和业务页面。

## 全局执行规则

- 每个阶段完成后运行对应测试、构建或冒烟验证。
- 提交前必须运行 `git diff --check`、`git status --short --ignored` 和敏感信息扫描。
- 不提交 `.superpowers/`、日志、`target/`、`node_modules/`、`dist/`、密钥或临时文件。
- 后端本机运行、Swagger 验证和本地联调默认基于 `application.yml`；只有模型 Key 通过 `AI_API_KEY` 环境变量注入。
- 所有新增或修改的 Java/TypeScript/Vue/Markdown 文件顶部保留 `@author myoung` 注释。
- 每个类、方法和关键流程添加有效注释。
- 提交消息和推送说明使用简体中文。

## Task 0: 收尾当前架构调整

> 状态：已完成并推送。保留本任务作为架构收口验收记录，后续执行从 Task 1 开始。

**Files:**

- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `backend/src/main/java/com/aitour/common/Result.java`
- Modify: `backend/src/main/java/com/aitour/controller/*.java`
- Modify: `backend/src/main/java/com/aitour/service/*.java`
- Modify: `backend/src/main/java/com/aitour/service/impl/*.java`
- Modify: `backend/src/test/java/com/aitour/controller/*.java`
- Modify: `backend/src/test/java/com/aitour/service/*.java`

- [x] **Step 1: 确认 Controller 没有 `@RequestBody`**

Run:

```powershell
rg -n "@RequestBody" backend/src/main/java/com/aitour/controller
```

Expected:

```text
无输出，命令退出码可以是 1。
```

- [x] **Step 2: 确认普通接口统一返回 `Result<T>`**

重点检查：

```text
backend/src/main/java/com/aitour/controller/AuthController.java
backend/src/main/java/com/aitour/controller/HealthController.java
backend/src/main/java/com/aitour/controller/ToolController.java
backend/src/main/java/com/aitour/controller/TripController.java
backend/src/main/java/com/aitour/controller/UserController.java
```

验收标准：

```text
普通 REST 方法返回 Result<T>。
SSE 方法仍返回 SseEmitter。
Controller 参数使用 @RequestParam 或 @PathVariable。
```

- [x] **Step 3: 运行后端测试**

Run:

```powershell
cd backend
mvn.cmd -o test
```

Expected:

```text
BUILD SUCCESS
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
```

- [x] **Step 4: 提交并推送**

Run:

```powershell
git diff --check
git status --short --ignored
rg -n "AI_API_KEY=.*|Bearer eyJ|sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16}" -g "!backend/target/**" -g "!frontend/node_modules/**" -g "!frontend/dist/**" -g "!.git/**" .
git add AGENTS.md README.md backend/src/main/java backend/src/test/java backend/src/test/resources/application.yml docs/superpowers/plans
git commit -m "refactor: 调整服务层结构并统一接口响应"
git pull --rebase origin main
git push origin main
```

Expected:

```text
提交成功，远程 main 包含本阶段改动。
```

## Task 1: 前端适配统一 `Result<T>` 和字段参数

> 状态：下一步优先执行。

**Files:**

- Modify: `frontend/src/api/http.ts`
- Create: `frontend/src/api/types.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/api/trips.ts`
- Create: `frontend/src/api/tools.ts`
- Create: `frontend/src/api/stream.ts`
- Modify: `frontend/src/App.test.ts`

- [ ] **Step 1: 定义前端统一响应类型**

在 `frontend/src/api/types.ts` 中定义：

```ts
/*
 * @author myoung
 */

/**
 * 后端普通 REST 接口统一响应结构。
 */
export interface Result<T> {
  code: number
  msg?: string | null
  data: T
}
```

- [ ] **Step 2: 让 Axios 自动解包 `Result.data`**

在 `frontend/src/api/http.ts` 中保留 `baseURL: '/api'`，增加响应拦截：

```ts
/*
 * @author myoung
 */
import axios from 'axios'
import type { Result } from './types'

/**
 * 统一后端 HTTP 客户端，负责添加认证头和解包 Result.data。
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

/**
 * 对普通 REST 响应做统一解包，业务层直接拿到 data。
 */
http.interceptors.response.use((response) => {
  const body = response.data as Result<unknown>
  if (body && typeof body === 'object' && 'code' in body) {
    if (body.code !== 1) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    response.data = body.data
  }
  return response
})
```

- [ ] **Step 3: API 方法使用 `URLSearchParams` 提交字段参数**

示例规则：

```ts
/**
 * 构建字段表单参数，保持参数名与后端 DTO 字段一致。
 */
function formParams(values: Record<string, string | number | undefined | null>) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value))
    }
  })
  return params
}
```

- [ ] **Step 4: 抽出流式接口请求封装**

验收标准：

```text
新增 frontend/src/api/stream.ts。
流式 POST 请求统一使用 fetch。
请求头包含 Authorization 和 Accept: text/event-stream。
请求体使用 URLSearchParams，不回退为 JSON RequestBody。
```

- [ ] **Step 5: 运行前端测试和构建**

Run:

```powershell
cd frontend
npm.cmd run test
npm.cmd run build
```

Expected:

```text
测试通过，生产构建成功生成 dist。
```

- [ ] **Step 6: 提交并推送**

Commit:

```powershell
git commit -m "feat: 适配前端统一响应结构"
```

## Task 2: 完善前端核心页面

**Files:**

- Create: `frontend/src/api/stream.ts`
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/stores/trip.ts`
- Modify: `frontend/src/router/index.ts`
- Create: `frontend/src/layouts/AppLayout.vue`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/RegisterView.vue`
- Create: `frontend/src/views/TripPlannerView.vue`
- Create: `frontend/src/views/TripHistoryView.vue`
- Create: `frontend/src/views/TripDetailView.vue`
- Create: `frontend/src/views/ProfileView.vue`
- Create: `frontend/src/views/ToolStatusView.vue`

- [ ] **Step 1: 路由以登录态保护业务页面**

验收标准：

```text
未登录访问行程、历史、详情、资料页时跳转登录页。
登录后可以回到原目标页面。
```

- [ ] **Step 2: 行程生成页使用字段参数提交**

验收标准：

```text
表单字段名与后端 CreateTripRequest 字段一致：
destination、startDate、days、budget、peopleCount、preferences、userInput。
```

- [ ] **Step 3: SSE 页面使用 `fetch + ReadableStream`**

验收标准：

```text
请求地址为 /api/trips/stream-plan。
请求头包含 Authorization 和 Accept: text/event-stream。
请求体使用 URLSearchParams。
页面可展示 progress、tool_result、plan_snapshot、ai_delta、completed、error。
```

- [ ] **Step 4: 运行前端测试和构建**

Run:

```powershell
cd frontend
npm.cmd run test
npm.cmd run build
```

Expected:

```text
测试通过，生产构建成功。
```

- [ ] **Step 5: 提交并推送**

Commit:

```powershell
git commit -m "feat: 完善前端核心页面"
```

## Task 3: Redis 登录态与缓存使用落地

> 状态：已完成并推送。包含 `refreshToken` Redis 持久化、`/api/auth/logout`、accessToken 黑名单校验和当前用户缓存失效处理。

**Files:**

- Modify: `backend/src/main/java/com/aitour/controller/AuthController.java`
- Modify: `backend/src/main/java/com/aitour/service/AuthService.java`
- Modify: `backend/src/main/java/com/aitour/service/impl/AuthServiceImpl.java`
- Modify: `backend/src/main/java/com/aitour/config/security/JwtTokenService.java`
- Modify: `backend/src/main/java/com/aitour/config/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/aitour/service/impl/UserProfileServiceImpl.java`
- Modify: `backend/src/test/java/com/aitour/controller/AuthControllerTest.java`
- Create: `backend/src/test/java/com/aitour/service/AuthRedisTokenTest.java`

- [ ] **Step 1: 刷新令牌写入 Redis**

验收标准：

```text
登录或注册成功后，refreshToken 与 userId 关系写入 Redis，并设置过期时间。
沿用当前 AuthResponse 返回 refreshToken 的接口契约，不新增完整 RequestBody。
```

- [ ] **Step 2: 支持退出登录黑名单**

验收标准：

```text
新增 POST /api/auth/logout。
Controller 继续使用字段参数和 Result<T>。
新增退出登录接口后，accessToken 剩余有效期内进入 Redis 黑名单。
JwtAuthenticationFilter 识别黑名单 token 并拒绝访问。
```

- [ ] **Step 3: 用户资料可选缓存**

验收标准：

```text
读取当前用户资料时可从 Redis 命中。
更新资料后删除或刷新缓存。
缓存键命名和失效逻辑需要明确，不无边界扩散用户敏感数据。
```

- [ ] **Step 4: 运行后端测试**

Run:

```powershell
cd backend
mvn.cmd -o test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: 提交并推送**

Commit:

```powershell
git commit -m "feat: 落地 Redis 登录态与退出登录"
```

## Task 4: MCP 工具模式配置化增强

> 状态：已完成并推送。已补充 `mcp.mode` / `mcp.external.*` 配置、`McpToolRegistry` 模式路由、真实外部 MCP Server HTTP/JSON-RPC 调用和对应测试，不再重复改模型接入。

**Files:**

- Create: `backend/src/main/java/com/aitour/config/mcp/McpProperties.java`
- Modify: `backend/src/main/java/com/aitour/client/mcp/McpToolRegistry.java`
- Modify: `backend/src/main/java/com/aitour/client/mcp/external/ExternalMcpToolAdapter.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/java/com/aitour/client/mcp/McpToolRegistryTest.java`

- [ ] **Step 1: 明确本地和外部 MCP 模式**

配置约定：

```yaml
mcp:
  mode: local
  external:
    base-url:
    timeout-seconds: 10
```

验收标准：

```text
mcp.mode=local 时始终使用内置工具。
mcp.mode=external 且配置 base-url 后，优先调用外部 MCP Server。
外部调用失败时返回可追踪错误，不吞异常。
```

- [ ] **Step 2: 保持 Spring AI 既有配置不回退**

验收标准：

```text
不修改当前 spring.ai.openai.* 的官方配置结构。
application.yml 中 spring.ai.openai.api-key 保持 ${AI_API_KEY:}。
不引入手写 HttpClient、OpenAiCompatibleChatClient 或 AiProperties 回退实现。
```

- [ ] **Step 3: 运行后端测试**

Run:

```powershell
cd backend
mvn.cmd -o test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: 提交并推送**

Commit:

```powershell
git commit -m "feat: 支持 MCP 本地与外部模式切换"
```

## Task 5: 行程生成质量和持久化细化

> 状态：已完成并推送。已补充预算不足提示、按天数/人数/偏好/预算调节单日节奏、同日景点去重，以及工具和 LLM 调用日志字段验证。

**Files:**

- Modify: `backend/src/main/java/com/aitour/service/impl/TripPlanningServiceImpl.java`
- Modify: `backend/src/main/java/com/aitour/domain/planning/*.java`
- Modify: `backend/src/main/java/com/aitour/common/entity/*.java`
- Modify: `backend/src/test/java/com/aitour/service/TripPlanningServiceTest.java`
- Modify: `backend/src/test/java/com/aitour/domain/planning/*.java`

- [ ] **Step 1: 优化每日行程规则**

验收标准：

```text
每日行程根据 days、peopleCount、preferences、budget 调整节奏。
每日安排不重复同一景点。
预算不足时给出明确提示。
```

- [ ] **Step 2: 工具和大模型调用日志完整落库**

验收标准：

```text
tool_call_log 记录 toolName、requestJson、responseSummary、latencyMs、success、errorMessage。
llm_call_log 记录 provider、model、promptSummary、responseSummary、latencyMs、success、errorMessage。
```

- [ ] **Step 3: 运行后端测试**

Run:

```powershell
cd backend
mvn.cmd -o test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: 提交并推送**

Commit:

```powershell
git commit -m "feat: 优化行程生成质量和调用日志"
```

## Task 6: Swagger 和接口测试文档补齐

**Files:**

- Modify: `backend/src/main/java/com/aitour/config/OpenApiConfig.java`
- Modify: `backend/src/main/java/com/aitour/controller/*.java`
- Modify: `backend/src/main/java/com/aitour/common/dto/*.java`
- Modify: `backend/src/main/java/com/aitour/common/Result.java`
- Modify: `backend/src/test/java/com/aitour/controller/SwaggerDocumentationTest.java`
- Modify: `README.md`

- [x] **Step 1: 基于现有注解补齐缺口**

验收标准：

```text
每个公开接口有 @Operation。
每个字段参数有 @Parameter。
接口错误状态有 @ApiResponse。
DTO 和统一 Result<T> 在文档中有清晰 schema 展示。
Swagger UI 中能看到字段参数，不再要求 JSON RequestBody。
```

- [x] **Step 2: 验证 Swagger 文档**

Run:

```powershell
cd backend
mvn.cmd -Dtest=SwaggerDocumentationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [x] **Step 3: 提交并推送**

Commit:

```powershell
git commit -m "docs: 完善 Swagger 接口说明"
```

## Task 7: 本地联调和交付清单

**Files:**

- Create: `docs/superpowers/checklists/current-local-smoke-test.md`
- Modify: `README.md`
- Modify: `.gitignore`

- [ ] **Step 1: 编写当前架构冒烟清单**

清单必须覆盖：

```text
MySQL 连接
Redis 连接
后端 mvn test
后端默认 application.yml 启动
Swagger UI
注册登录
退出登录
用户资料
行程草稿
SSE 流式生成
历史行程
工具状态
前端 npm run test
前端 npm run build
```

- [ ] **Step 2: 执行本地冒烟验证**

Run:

```powershell
docker compose config
cd backend
mvn.cmd test
mvn.cmd spring-boot:run
cd ..\frontend
npm.cmd run test
npm.cmd run build
```

Expected:

```text
所有命令通过。
```

- [ ] **Step 3: 最终提交并推送**

Commit:

```powershell
git commit -m "docs: 更新当前架构交付清单"
```

## Self-Review

- Spec coverage: 当前用户确认的 JDK 21、Spring Boot、Spring AI、Tomcat、Druid、三层加扩展分层、MySQL、Redis、无 RocketMQ、OpenAI-compatible 中转、MCP 可切换、SSE、Swagger、统一 `Result<T>`、字段参数、中文提交说明均已覆盖。
- Placeholder scan: 本计划不使用 `TBD`、`TODO` 或“后续再补”作为执行步骤。
- Type consistency: 后端统一使用 `Result<T>`、`SseEmitter`、`service.impl`、`URLSearchParams` 等当前项目约定命名。
