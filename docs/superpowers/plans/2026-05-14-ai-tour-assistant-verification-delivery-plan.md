<!-- @author myoung -->

# AI 旅游助手验证与交付 Implementation Plan

> **历史计划，停止按此执行。** 本文保留为 2026-05-14 阶段拆分记录，代码片段和配置不再作为后续实施依据。当前项目已调整为 Spring Boot + Spring AI + `Result<T>` + `service.impl` 架构，后续执行以 `docs/superpowers/plans/2026-05-15-ai-tour-assistant-current-architecture-plan.md` 和根目录 `AGENTS.md` 为准。
>
> 旧内容中可能包含 `.env` 中配置模型地址、模型名、数据库账号或 JWT 密钥等过期约束；当前约定是只有模型 Key 从环境变量读取，其余本机开发配置写入 `application.yml`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在所有功能阶段完成后，建立统一的本地验证、配置说明、演示脚本和交付检查，确保项目可运行、可演示、可继续扩展。

**Architecture:** 验证计划不新增业务能力，只补齐工程交付材料和端到端检查。所有成功结论必须来自实际命令输出。

**Tech Stack:** Maven、npm、Docker Compose、Spring Boot、Vue、MySQL、Redis。

---

## 前置条件

- 基础工程计划已完成。
- 认证与用户资料计划已完成。
- 行程数据与历史记录计划已完成。
- AI 与 MCP 工具抽象计划已完成。
- SSE 流式行程生成计划已完成。
- 前端核心页面计划已完成。

## 文件结构规划

```text
.env.example
README.md
docs/superpowers/checklists/local-smoke-test.md
docs/superpowers/checklists/demo-script.md
docs/superpowers/checklists/release-checklist.md
```

## Task 1: 环境变量示例

**Files:**

- Create: `.env.example`
- Modify: `README.md`

- [ ] **Step 1: 创建 `.env.example`**

```dotenv
JWT_SECRET=aitour-local-development-secret-must-be-32-bytes
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=replace-with-your-api-key
AI_MODEL=gpt-4o-mini
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=aitour
MYSQL_USERNAME=aitour
MYSQL_PASSWORD=aitour
REDIS_HOST=localhost
REDIS_PORT=6379
```

- [ ] **Step 2: README 增加配置说明**

````markdown
## 环境变量

复制 `.env.example` 并按本地环境填写：

```bash
cp .env.example .env
```

敏感信息不要提交到 Git。`AI_API_KEY` 只在本地或部署环境中配置。
````

- [ ] **Step 3: 确认 `.gitignore` 排除 `.env`**

Run:

```bash
Select-String -Path .gitignore -Pattern '^\\.env$'
```

Expected:

```text
.env
```

## Task 2: 本地冒烟测试清单

**Files:**

- Create: `docs/superpowers/checklists/local-smoke-test.md`

- [ ] **Step 1: 创建冒烟测试清单**

````markdown
# 本地冒烟测试清单

## 1. 中间件

```bash
docker compose up -d
docker compose ps
```

期望：

- `aitour-mysql` 状态为 running 或 healthy。
- `aitour-redis` 状态为 running 或 healthy。

## 2. 后端测试

```bash
cd backend
mvn test
```

期望：

- 输出 `BUILD SUCCESS`。

## 3. 后端启动

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

期望：

- 输出 `Started AiTourAssistantApplication`。

## 4. 健康检查

```bash
curl http://localhost:8080/api/health
```

期望：

- 响应中包含 `"status":"UP"`。

## 5. 认证接口

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"password123","nickname":"Demo"}'
```

期望：

- 响应中包含 `accessToken`。

## 6. 流式行程生成

```bash
curl -N -X POST http://localhost:8080/api/trips/stream-plan \
  -H "Authorization: Bearer <accessToken>" \
  -H "Accept: text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食","轻松"],"userInput":"想吃火锅，不想太赶"}'
```

期望：

- 依次看到 `progress`、`tool_result`、`ai_delta`、`completed` 事件。

## 7. 前端测试和构建

```bash
cd frontend
npm run test
npm run build
```

期望：

- 测试输出 `passed`。
- 构建输出 `dist`。

## 8. 前端手动检查

```bash
cd frontend
npm run dev
```

打开：

```text
http://localhost:5173
```

期望：

- 可以注册或登录。
- 可以进入 `/home`。
- 可以提交旅行需求。
- 可以看到流式生成过程。
```
````

## Task 3: 演示脚本

**Files:**

- Create: `docs/superpowers/checklists/demo-script.md`

- [ ] **Step 1: 创建演示脚本**

````markdown
# AI 旅游助手演示脚本

## 演示目标

展示这是一个完整 Java Web 工程，而不是单纯的大模型调用 Demo。

## 演示顺序

1. 打开登录页，注册一个用户。
2. 进入首页，说明左侧是结构化出行需求，右侧是流式生成过程。
3. 输入示例需求：

```text
目的地：成都
日期：2099-06-01
天数：3
预算：3000
人数：2
偏好：美食、轻松、历史
补充：想吃火锅，不想太赶，最好安排市区景点
```

4. 点击生成行程。
5. 观察流式事件：
   - 正在解析需求。
   - 正在查询天气。
   - 正在查询景点。
   - 正在估算预算。
   - AI 文本逐步出现。
6. 打开历史行程页，展示行程已持久化。
7. 打开工具状态页，展示本地 MCP 风格工具。
8. 打开数据库或日志说明：
   - 用户信息在 MySQL。
   - 登录态和缓存用 Redis。
   - AI 和工具调用有日志表。

## 技术亮点说明

- Spring Boot 3 模块化单体。
- Spring Security + JWT 登录认证。
- MySQL + Redis 中间件。
- OpenAI-compatible AI Client。
- MCP 工具抽象，支持本地和外部工具切换。
- SSE 流式返回提升用户体验。
- Vue 3 + Element Plus 前端。
```
````

## Task 4: 发布检查清单

**Files:**

- Create: `docs/superpowers/checklists/release-checklist.md`

- [ ] **Step 1: 创建发布检查清单**

````markdown
# 发布检查清单

## 代码检查

- [ ] 没有提交 `.env`、API Key、数据库密码或 Token。
- [ ] 后端 `mvn test` 通过。
- [ ] 前端 `npm run test` 通过。
- [ ] 前端 `npm run build` 通过。
- [ ] Flyway 迁移可以从空库完整执行。
- [ ] README 的启动命令可用。

## 功能检查

- [ ] 可以注册用户。
- [ ] 可以登录用户。
- [ ] 未登录访问业务页面会跳转登录。
- [ ] 可以创建流式行程。
- [ ] 行程完成后可以在历史列表看到。
- [ ] 工具状态页显示本地工具。
- [ ] AI API Key 缺失时有明确错误或本地兜底路径。

## 演示检查

- [ ] 浏览器打开 `/home` 页面无控制台错误。
- [ ] 生成行程时能看到进度变化。
- [ ] 生成行程时能看到 AI 文本流式输出。
- [ ] 演示数据不会泄露真实用户或密钥。

## 后续扩展记录

- [ ] RocketMQ 仍为后续扩展，不在 MVP 主链路。
- [ ] 外部 MCP Server 接入留在工具适配层。
- [ ] WebSocket 只在需要双向实时交互时再引入。
```
````

## Task 5: 全量验证命令

**Files:**

- Modify: `README.md`

- [ ] **Step 1: README 增加全量验证命令**

````markdown
## 全量验证

```bash
docker compose up -d
cd backend
mvn test
cd ../frontend
npm run test
npm run build
```

全部完成后，再分别启动后端和前端做手动冒烟测试。
````

- [ ] **Step 2: 运行 Docker 配置校验**

Run:

```bash
docker compose config
```

Expected:

```text
services:
```

- [ ] **Step 3: 运行后端测试**

Run:

```bash
cd backend
mvn test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: 运行前端测试和构建**

Run:

```bash
cd frontend
npm run test
npm run build
```

Expected:

```text
passed
```

并且：

```text
dist
```

- [ ] **Step 5: 查看 Git 状态**

Run:

```bash
git status --short
```

Expected:

```text
 M README.md
?? .env.example
?? docs/superpowers/checklists/
```

- [ ] **Step 6: 提交验证与交付文档**

Run:

```bash
git add .env.example README.md docs/superpowers/checklists
git commit -m "docs: add verification and demo checklist"
```

Expected:

```text
docs: add verification and demo checklist
```

## 自检清单

- 验证命令覆盖 Docker、后端、前端。
- 演示脚本覆盖业务价值和技术亮点。
- 发布检查覆盖密钥、测试、构建、数据库迁移和核心功能。
- 没有把 RocketMQ 写入 MVP 必选项。
