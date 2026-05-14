<!-- @author myoung -->

# AiTourAssistant

一个基于 Java 开发的 AI 旅游助手，可以根据用户出行需求，自动完成目的地景点推荐、天气查询、行程规划和预算估算。

## 技术栈

- Backend: Java 17+, Spring Boot 3, MyBatis-Plus, Flyway, MySQL, Redis
- Frontend: Vue 3, Vite, Element Plus, Pinia
- AI: OpenAI-compatible API
- Tools: 本地 MCP 风格工具 + 外部 MCP Server 适配

## 本地中间件

如果本机已安装 MySQL 和 Redis，可以直接配置环境变量后启动后端。

当前建议的本地环境变量：

```powershell
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="your-password"
$env:REDIS_PASSWORD="your-password"
$env:JWT_SECRET="your-32-byte-or-longer-jwt-secret"
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
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="your-password"
$env:REDIS_PASSWORD="your-password"
$env:JWT_SECRET="your-32-byte-or-longer-jwt-secret"
cd backend
mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
curl http://localhost:8080/api/health
```
