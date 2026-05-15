<!-- @author myoung -->

# 当前架构本地冒烟清单

## 适用范围

- 项目：`AiTourAssistant`
- 适用日期：`2026-05-15`
- 目标：验证当前 Spring Boot + Vue 架构在本机 MySQL、Redis 和本地配置下可以完成基础联调与交付。

## 验证前置条件

- 已安装并启用 JDK 21。
- 已安装 Maven、Node.js 与 npm。
- 本机 MySQL 已启动，数据库存在 `aitour`，连接账号为 `root/young`。
- 本机 Redis 已启动，密码为 `young`。
- 已设置模型 Key 环境变量：

```powershell
$env:AI_API_KEY="your-api-key"
```

- 如需使用 Docker 启动中间件，已准备 `.env` 文件：

```powershell
Copy-Item .env.example .env
```

## 冒烟步骤

### 1. Docker Compose 配置校验

```powershell
docker compose config
```

期望：

- 配置解析成功。
- MySQL 和 Redis 所需环境变量已正确替换。

### 2. MySQL 连接验证

验证内容：

- `backend/src/main/resources/application.yml` 中的 MySQL 地址、用户名和密码与本机一致。
- 后端测试或启动时 Flyway 能正常执行迁移。

期望：

- 后端日志中无数据库连接失败信息。
- Flyway 输出迁移成功或数据库已是最新版本。

### 3. Redis 连接验证

验证内容：

- `backend/src/main/resources/application.yml` 中的 Redis 地址和密码与本机一致。
- 登录态、黑名单或缓存相关测试运行正常。

期望：

- 后端测试或启动日志中无 Redis 认证失败或连接失败信息。

### 4. 后端测试

```powershell
cd backend
mvn.cmd test
```

期望：

- `BUILD SUCCESS`

### 5. 后端默认配置启动

```powershell
cd backend
mvn.cmd spring-boot:run
```

期望：

- 应用使用 `application.yml` 启动成功。
- 服务监听 `http://localhost:8080`。

### 6. Swagger UI 验证

访问：

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

期望：

- Swagger UI 可访问。
- OpenAPI JSON 返回 200。
- 字段参数接口直接展示 query/path 参数，不要求 JSON `requestBody`。

### 7. 注册登录验证

示例：

```powershell
curl -X POST http://localhost:8080/api/auth/register `
  -d "username=smoke_user" `
  -d "password=password123" `
  -d "nickname=Smoke"

curl -X POST http://localhost:8080/api/auth/login `
  -d "username=smoke_user" `
  -d "password=password123"
```

期望：

- 返回 `Result<AuthResponse>`。
- 能拿到 `accessToken` 和 `refreshToken`。

### 8. 退出登录验证

```powershell
curl -X POST http://localhost:8080/api/auth/logout `
  -H "Authorization: Bearer <accessToken>"
```

期望：

- 返回成功响应。
- 同一个 `accessToken` 再访问受保护接口时被拒绝。

### 9. 当前用户与资料验证

```powershell
curl http://localhost:8080/api/users/me `
  -H "Authorization: Bearer <accessToken>"

curl -X PUT http://localhost:8080/api/users/me/profile `
  -H "Authorization: Bearer <accessToken>" `
  -d "travelStyle=轻松" `
  -d "defaultBudgetLevel=中等" `
  -d "preferredTransport=地铁"
```

期望：

- 能查询当前用户。
- 能更新并再次查询用户资料。

### 10. 行程草稿验证

```powershell
curl -X POST http://localhost:8080/api/trips/draft `
  -H "Authorization: Bearer <accessToken>" `
  -d "destination=成都" `
  -d "startDate=2099-06-01" `
  -d "days=3" `
  -d "budget=3000" `
  -d "peopleCount=2" `
  -d "preferences=美食" `
  -d "userInput=想吃火锅"
```

期望：

- 返回 `Result<CreateDraftResponse>`。
- 响应 `data.planId` 存在。

### 11. SSE 流式生成验证

```powershell
curl -N -X POST http://localhost:8080/api/trips/stream-plan `
  -H "Authorization: Bearer <accessToken>" `
  -H "Accept: text/event-stream" `
  -d "destination=成都" `
  -d "startDate=2099-06-01" `
  -d "days=3" `
  -d "budget=3000" `
  -d "peopleCount=2"
```

期望：

- 返回 `text/event-stream`。
- 能观察到 `progress`、`tool_result`、`plan_snapshot`、`ai_delta`、`completed` 或 `error` 事件。

### 12. 历史行程与详情验证

```powershell
curl http://localhost:8080/api/trips `
  -H "Authorization: Bearer <accessToken>"

curl http://localhost:8080/api/trips/<planId> `
  -H "Authorization: Bearer <accessToken>"
```

期望：

- 列表接口返回当前用户历史行程。
- 详情接口返回每日安排和预算信息。

### 13. 工具状态验证

```powershell
curl http://localhost:8080/api/tools/status `
  -H "Authorization: Bearer <accessToken>"
```

期望：

- 返回 `Result<ToolStatusResponse>`。
- `data.mode` 与当前 `mcp.mode` 配置一致。
- `data.tools` 返回可用工具名称列表。

### 14. 前端测试

```powershell
cd frontend
npm.cmd run test
```

期望：

- 测试命令通过。

### 15. 前端构建

```powershell
cd frontend
npm.cmd run build
```

期望：

- 构建命令通过。
- 生成 `frontend/dist`。

## 执行记录模板

| 项目 | 命令/动作 | 结果 | 备注 |
|---|---|---|---|
| Docker Compose 配置 | `docker compose config` | 待执行 | |
| 后端测试 | `cd backend && mvn.cmd test` | 待执行 | |
| 后端启动 | `cd backend && mvn.cmd spring-boot:run` | 待执行 | |
| Swagger UI | 浏览器访问 | 待执行 | |
| 注册登录 | `curl` | 待执行 | |
| 退出登录 | `curl` | 待执行 | |
| 用户资料 | `curl` | 待执行 | |
| 行程草稿 | `curl` | 待执行 | |
| SSE 流式生成 | `curl -N` | 待执行 | |
| 历史行程 | `curl` | 待执行 | |
| 工具状态 | `curl` | 待执行 | |
| 前端测试 | `cd frontend && npm.cmd run test` | 待执行 | |
| 前端构建 | `cd frontend && npm.cmd run build` | 待执行 | |
