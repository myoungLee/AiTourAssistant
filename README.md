<!-- @author myoung -->

# AiTourAssistant

一个基于 Java 开发的 AI 旅游助手，可以根据用户出行需求，自动完成目的地景点推荐、天气查询、行程规划和预算估算。

## 技术栈

- Backend: JDK 21, Spring Boot 3, embedded Tomcat, Druid, MyBatis-Plus, Flyway, MySQL, Redis
- Frontend: Vue 3, Vite, Element Plus, Pinia
- AI: OpenAI-compatible API
- Tools: 本地 MCP 风格工具 + 外部 MCP Server 适配

## 后端结构约定

- `controller`: 接收请求、参数校验、返回结果
- `service`: 编排业务流程
- `domain`: 核心业务模型和业务规则
- `mapper`: MyBatis-Plus 数据访问层
- `client`: 调用第三方服务，例如天气、地图、大模型和 MCP 工具适配
- `config`: 配置类
- `common`: 通用工具、异常、响应封装、DTO、Entity、VO

## 本地中间件

如果本机已安装 MySQL 和 Redis，可以直接使用 `backend/src/main/resources/application.yml` 中的本地配置启动后端。

当前默认配置：

```text
MySQL: jdbc:mysql://localhost:3306/aitour
MySQL 用户名: root
MySQL 密码: young
Redis: localhost:6379
Redis 密码: young
```

AI 模型请求地址、模型名和推理等级写在配置文件中，只有模型 Key 从环境变量读取：

```powershell
$env:AI_API_KEY="your-api-key"
```

如果需要用 Docker 启动中间件，先复制 `.env.example` 为 `.env` 并填写本地密码：

```powershell
Copy-Item .env.example .env
docker compose up -d
```

## 后端启动

```powershell
cd backend
mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

健康检查：

```powershell
curl http://localhost:8080/api/health
```

## Swagger 接口测试文档

启动后端后访问 Swagger UI：

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON 文档地址：

```text
http://localhost:8080/v3/api-docs
```

需要登录的接口在 Swagger UI 右上角点击 `Authorize`，填入登录接口返回的 `accessToken`：

```text
Bearer <accessToken>
```

## 认证接口

注册：

```powershell
curl -X POST http://localhost:8080/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{"username":"alice","password":"password123","nickname":"Alice"}'
```

登录：

```powershell
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"alice","password":"password123"}'
```

当前用户：

```powershell
curl http://localhost:8080/api/users/me `
  -H "Authorization: Bearer <accessToken>"
```

更新旅行偏好：

```powershell
curl -X PUT http://localhost:8080/api/users/me/profile `
  -H "Authorization: Bearer <accessToken>" `
  -H "Content-Type: application/json" `
  -d '{"travelStyle":"轻松","defaultBudgetLevel":"中等","preferredTransport":"地铁"}'
```

## 行程草稿接口

创建草稿：

```powershell
curl -X POST http://localhost:8080/api/trips/draft `
  -H "Authorization: Bearer <accessToken>" `
  -H "Content-Type: application/json" `
  -d '{"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食"],"userInput":"想吃火锅"}'
```

查询历史：

```powershell
curl http://localhost:8080/api/trips `
  -H "Authorization: Bearer <accessToken>"
```

查询详情：

```powershell
curl http://localhost:8080/api/trips/<planId> `
  -H "Authorization: Bearer <accessToken>"
```

## SSE 流式行程生成接口

创建行程并流式接收规划进度、MCP 工具结果、阶段性行程快照和 AI 增量回答：

```powershell
curl -N -X POST http://localhost:8080/api/trips/stream-plan `
  -H "Authorization: Bearer <accessToken>" `
  -H "Accept: text/event-stream" `
  -H "Content-Type: application/json" `
  -d '{"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食","文化"],"userInput":"想吃火锅，行程不要太赶"}'
```

对已生成行程发起二次调整，并流式接收调整建议：

```powershell
curl -N -X POST http://localhost:8080/api/trips/<planId>/adjust-stream `
  -H "Authorization: Bearer <accessToken>" `
  -H "Accept: text/event-stream" `
  -H "Content-Type: application/json" `
  -d '{"instruction":"把第二天改得轻松一些，增加一家本地餐厅"}'
```

## 工具状态接口

查询当前可用的本地 MCP 风格工具：

```powershell
curl http://localhost:8080/api/tools/status `
  -H "Authorization: Bearer <accessToken>"
```

## 前端启动

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问：

```text
http://localhost:5173
```

## 本地验证

后端单元测试和 Flyway 迁移验证：

```powershell
cd backend
mvn.cmd test
```

前端组件测试和生产构建：

```powershell
cd frontend
npm.cmd run test
npm.cmd run build
```

Docker Compose 配置解析需要先准备 `.env`，避免中间件密码被空值替换：

```powershell
Copy-Item .env.example .env
docker compose config
```

后端连接本机 MySQL 和 Redis 的冒烟验证：

```powershell
$env:AI_API_KEY="your-api-key"
cd backend
mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
curl http://localhost:8080/api/health
```
