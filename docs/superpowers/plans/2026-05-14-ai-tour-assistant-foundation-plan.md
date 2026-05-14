# AI 旅游助手基础工程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 AI 旅游助手项目的基础工程，让后端、前端、MySQL、Redis 和基础健康检查可以在本地跑通。

**Architecture:** 本计划只覆盖基础工程，不实现完整用户认证、行程规划、AI 编排或 MCP 工具链。项目采用前后端分离结构：`backend/` 是 Spring Boot 3 应用，`frontend/` 是 Vue 3 + Vite 应用，根目录提供 Docker Compose 启动 MySQL 和 Redis。

**Tech Stack:** JDK 21、Spring Boot 3、embedded Tomcat、Druid、Maven、MySQL、Redis、Flyway、MyBatis-Plus、Vue 3、Vite、Element Plus、Pinia、Vitest。

---

## 范围检查

完整设计包含认证、用户资料、行程持久化、AI、MCP、SSE 流式输出和前端详情页等多个子系统。该范围过大，不适合塞进一个实施计划。

本计划只实现第一阶段基础工程：

- 根目录工程约束和运行说明。
- MySQL 与 Redis 的本地 Docker Compose。
- Spring Boot 后端骨架。
- 后端健康检查接口。
- Flyway 基础用户表迁移。
- Vue 3 前端骨架。
- 前端健康检查页面。

后续单独拆分这些计划：

- 认证与用户资料计划。
- 行程数据模型与历史行程计划。
- AI 与 MCP 工具抽象计划。
- SSE 流式生成计划。
- 前端行程生成和详情页计划。

## 文件结构规划

```text
AiTourAssistant/
  .gitignore
  docker-compose.yml
  README.md
  AGENTS.md

  backend/
    pom.xml
    src/main/java/com/aitour/AiTourAssistantApplication.java
    src/main/java/com/aitour/controller/HealthController.java
    src/main/resources/application.yml
    src/main/resources/application-dev.yml
    src/main/resources/db/migration/V1__init_foundation.sql
    src/test/java/com/aitour/controller/HealthControllerTest.java
    src/test/java/com/aitour/DatabaseMigrationTest.java

  frontend/
    package.json
    index.html
    vite.config.ts
    vitest.config.ts
    tsconfig.json
    tsconfig.node.json
    src/main.ts
    src/env.d.ts
    src/App.vue
    src/router/index.ts
    src/stores/app.ts
    src/views/HomeView.vue
    src/api/http.ts
    src/styles/main.css
    src/App.test.ts
```

## Task 1: 根目录工程文件

**Files:**

- Create: `.gitignore`
- Create: `docker-compose.yml`
- Create: `README.md`

- [ ] **Step 1: 创建 `.gitignore`**

写入：

```gitignore
# Java
target/
*.class
*.log

# Maven
.mvn/wrapper/maven-wrapper.jar

# Node
node_modules/
dist/
coverage/

# IDE
.idea/
.vscode/
*.iml

# OS
.DS_Store
Thumbs.db

# Env
.env
.env.*
!.env.example

# Local brainstorm / agent artifacts
.superpowers/
```

- [ ] **Step 2: 创建 `docker-compose.yml`**

写入：

```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: aitour-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: aitour
      MYSQL_USER: aitour
      MYSQL_PASSWORD: aitour
    ports:
      - "3306:3306"
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    volumes:
      - aitour-mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "-proot"]
      interval: 10s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7.4
    container_name: aitour-redis
    ports:
      - "6379:6379"
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - aitour-redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10

volumes:
  aitour-mysql-data:
  aitour-redis-data:
```

- [ ] **Step 3: 创建 `README.md`**

写入：

````markdown
# AI Tour Assistant

Java AI 旅游助手项目，采用 Spring Boot 3 + Vue 3 前后端分离架构。

## 技术栈

- Backend: JDK 21, Spring Boot 3, embedded Tomcat, Druid, MyBatis-Plus, Flyway, MySQL, Redis
- Frontend: Vue 3, Vite, Element Plus, Pinia
- AI: OpenAI-compatible API
- Tools: 本地 MCP 风格工具 + 外部 MCP Server 适配

## 本地启动

启动中间件：

```bash
docker compose up -d
```

启动后端：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

## 健康检查

后端：

```bash
curl http://localhost:8080/api/health
```

前端：

```text
http://localhost:5173
```
````

- [ ] **Step 4: 验证 Docker Compose 配置**

Run:

```bash
docker compose config
```

Expected:

```text
services:
```

输出中必须能看到 `mysql` 和 `redis` 两个服务。

- [ ] **Step 5: 提交根目录工程文件**

如果当前目录还不是 Git 仓库，先执行：

```bash
git init
```

然后提交：

```bash
git add .gitignore docker-compose.yml README.md AGENTS.md docs/superpowers/specs/2026-05-14-ai-tour-assistant-design.md docs/superpowers/plans/2026-05-14-ai-tour-assistant-foundation-plan.md
git commit -m "docs: add project design and foundation plan"
```

Expected:

```text
[main
```

或：

```text
[master
```

## Task 2: Spring Boot 后端骨架和健康检查

**Files:**

- Create: `backend/pom.xml`
- Create: `backend/src/test/java/com/aitour/controller/HealthControllerTest.java`
- Create: `backend/src/main/java/com/aitour/AiTourAssistantApplication.java`
- Create: `backend/src/main/java/com/aitour/controller/HealthController.java`

- [ ] **Step 1: 创建 `backend/pom.xml`**

写入：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.aitour</groupId>
    <artifactId>aitour-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>aitour-backend</name>
    <description>AI Tour Assistant backend</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 先写失败测试 `HealthControllerTest`**

写入：

```java
package com.aitour.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("aitour-backend"));
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run:

```bash
cd backend
mvn test -Dtest=HealthControllerTest
```

Expected:

```text
cannot find symbol
```

失败原因应指向 `HealthController` 尚不存在。

- [ ] **Step 4: 创建 Spring Boot 启动类**

写入 `backend/src/main/java/com/aitour/AiTourAssistantApplication.java`：

```java
package com.aitour;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiTourAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTourAssistantApplication.class, args);
    }
}
```

- [ ] **Step 5: 创建健康检查接口**

写入 `backend/src/main/java/com/aitour/controller/HealthController.java`：

```java
package com.aitour.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "aitour-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run:

```bash
cd backend
mvn test -Dtest=HealthControllerTest
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 7: 提交后端健康检查骨架**

Run:

```bash
git add backend/pom.xml backend/src/main/java/com/aitour/AiTourAssistantApplication.java backend/src/main/java/com/aitour/controller/HealthController.java backend/src/test/java/com/aitour/controller/HealthControllerTest.java
git commit -m "feat: add backend health endpoint"
```

Expected:

```text
feat: add backend health endpoint
```

## Task 3: 后端 MySQL、Redis 和 Flyway 基础配置

**Files:**

- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-dev.yml`
- Create: `backend/src/main/resources/db/migration/V1__init_foundation.sql`
- Create: `backend/src/test/java/com/aitour/DatabaseMigrationTest.java`

- [ ] **Step 1: 修改 `backend/pom.xml` 增加数据访问依赖**

在 `<dependencies>` 中追加：

```xml
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.9</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: 创建默认配置 `application.yml`**

写入：

```yaml
server:
  port: 8080

spring:
  application:
    name: aitour-backend
  datasource:
    url: jdbc:h2:mem:aitour;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2s

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: assign_id
```

- [ ] **Step 3: 创建开发环境配置 `application-dev.yml`**

写入：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aitour?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: aitour
    password: aitour
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2s

logging:
  level:
    com.aitour: DEBUG
```

- [ ] **Step 4: 创建 Flyway 迁移 `V1__init_foundation.sql`**

写入：

```sql
create table users (
    id bigint primary key,
    username varchar(64) not null,
    password_hash varchar(255) not null,
    nickname varchar(64),
    avatar_url varchar(512),
    phone varchar(32),
    email varchar(128),
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_users_username unique (username)
);

create table user_profile (
    id bigint primary key,
    user_id bigint not null,
    gender varchar(32),
    age_range varchar(32),
    travel_style varchar(64),
    default_budget_level varchar(32),
    preferred_transport varchar(64),
    preferences_json text,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_user_profile_user_id unique (user_id)
);

create index idx_user_profile_user_id on user_profile (user_id);
```

- [ ] **Step 5: 写数据库迁移测试**

写入 `backend/src/test/java/com/aitour/DatabaseMigrationTest.java`：

```java
package com.aitour;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateUsersTable() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'users'",
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldCreateUserProfileTable() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'user_profile'",
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 6: 运行后端测试**

Run:

```bash
cd backend
mvn test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 7: 启动 MySQL 和 Redis**

Run:

```bash
docker compose up -d
```

Expected:

```text
Container aitour-mysql
Container aitour-redis
```

输出可能包含 `Started`、`Healthy` 或 `Running`，但必须能看到两个容器名。

- [ ] **Step 8: 使用 dev profile 启动后端**

Run:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Expected:

```text
Started AiTourAssistantApplication
```

- [ ] **Step 9: 提交基础设施配置**

Run:

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/resources/application-dev.yml backend/src/main/resources/db/migration/V1__init_foundation.sql backend/src/test/java/com/aitour/DatabaseMigrationTest.java
git commit -m "feat: configure backend persistence foundation"
```

Expected:

```text
feat: configure backend persistence foundation
```

## Task 4: Vue 3 前端骨架和首页

**Files:**

- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/vitest.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/env.d.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/stores/app.ts`
- Create: `frontend/src/views/HomeView.vue`
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/styles/main.css`
- Create: `frontend/src/App.test.ts`

- [ ] **Step 1: 创建 `frontend/package.json`**

写入：

```json
{
  "name": "aitour-frontend",
  "version": "0.0.1",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "test": "vitest run",
    "preview": "vite preview"
  },
  "dependencies": {
    "@element-plus/icons-vue": "^2.3.1",
    "axios": "^1.7.7",
    "element-plus": "^2.8.7",
    "pinia": "^2.2.4",
    "vue": "^3.5.12",
    "vue-router": "^4.4.5"
  },
  "devDependencies": {
    "@types/node": "^22.9.0",
    "@vitejs/plugin-vue": "^5.1.4",
    "@vue/test-utils": "^2.4.6",
    "jsdom": "^25.0.1",
    "typescript": "^5.6.3",
    "vite": "^5.4.10",
    "vitest": "^2.1.3",
    "vue-tsc": "^2.1.6"
  }
}
```

- [ ] **Step 2: 创建 Vite HTML 入口**

写入 `frontend/index.html`：

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>AI 旅游助手</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 3: 创建 Vite 配置**

写入 `frontend/vite.config.ts`：

```ts
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

- [ ] **Step 4: 创建 Vitest 配置**

写入 `frontend/vitest.config.ts`：

```ts
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom'
  }
})
```

- [ ] **Step 5: 创建 TypeScript 配置和 Vue 类型声明**

写入 `frontend/tsconfig.json`：

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "Bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.vue"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

写入 `frontend/tsconfig.node.json`：

```json
{
  "compilerOptions": {
    "composite": true,
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts", "vitest.config.ts"]
}
```

写入 `frontend/src/env.d.ts`：

```ts
/// <reference types="vite/client" />
```

- [ ] **Step 6: 创建前端入口 `main.ts`**

写入 `frontend/src/main.ts`：

```ts
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import './styles/main.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
```

- [ ] **Step 7: 创建根组件 `App.vue`**

写入：

```vue
<template>
  <el-config-provider>
    <router-view />
  </el-config-provider>
</template>
```

- [ ] **Step 8: 创建路由**

写入 `frontend/src/router/index.ts`：

```ts
import { createRouter, createWebHistory } from 'vue-router'

import HomeView from '@/views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/home'
    },
    {
      path: '/home',
      name: 'home',
      component: HomeView
    }
  ]
})

export default router
```

- [ ] **Step 9: 创建 Pinia 基础状态**

写入 `frontend/src/stores/app.ts`：

```ts
import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    appName: 'AI 旅游助手'
  })
})
```

- [ ] **Step 10: 创建 HTTP 客户端**

写入 `frontend/src/api/http.ts`：

```ts
import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})
```

- [ ] **Step 11: 创建首页**

写入 `frontend/src/views/HomeView.vue`：

```vue
<template>
  <main class="home-page">
    <section class="hero">
      <div>
        <p class="eyebrow">AI Tour Assistant</p>
        <h1>AI 旅游助手</h1>
        <p class="summary">
          输入目的地、预算、天数和旅行偏好，系统将生成包含景点、天气、路线和预算的智能行程。
        </p>
      </div>

      <el-card class="status-card" shadow="never">
        <template #header>
          <span>当前阶段</span>
        </template>
        <el-tag type="success">基础工程搭建中</el-tag>
        <p>后端健康检查和前端基础页面用于验证项目骨架。</p>
      </el-card>
    </section>
  </main>
</template>
```

- [ ] **Step 12: 创建全局样式**

写入 `frontend/src/styles/main.css`：

```css
body {
  margin: 0;
  min-width: 320px;
  background: #f6f8fb;
  color: #172033;
  font-family:
    Inter,
    "Microsoft YaHei",
    Arial,
    sans-serif;
}

.home-page {
  min-height: 100vh;
  padding: 40px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 420px);
  gap: 24px;
  align-items: start;
  max-width: 1120px;
  margin: 0 auto;
}

.eyebrow {
  margin: 0 0 12px;
  color: #2563eb;
  font-size: 14px;
  font-weight: 700;
}

h1 {
  margin: 0 0 16px;
  font-size: 40px;
  line-height: 1.15;
  letter-spacing: 0;
}

.summary {
  max-width: 680px;
  margin: 0;
  color: #5f6b7a;
  font-size: 17px;
  line-height: 1.75;
}

.status-card {
  border-radius: 8px;
}

.status-card p {
  margin: 16px 0 0;
  color: #667085;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .home-page {
    padding: 24px;
  }

  .hero {
    grid-template-columns: 1fr;
  }

  h1 {
    font-size: 32px;
  }
}
```

- [ ] **Step 13: 创建前端测试**

写入 `frontend/src/App.test.ts`：

```ts
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'

import App from './App.vue'
import router from './router'

describe('App', () => {
  it('renders home page through router', async () => {
    router.push('/home')
    await router.isReady()

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), router]
      }
    })

    expect(wrapper.text()).toContain('AI 旅游助手')
    expect(wrapper.text()).toContain('基础工程搭建中')
  })
})
```

- [ ] **Step 14: 安装前端依赖**

Run:

```bash
cd frontend
npm install
```

Expected:

```text
added
```

或：

```text
up to date
```

- [ ] **Step 15: 运行前端测试**

Run:

```bash
cd frontend
npm run test
```

Expected:

```text
1 passed
```

- [ ] **Step 16: 构建前端**

Run:

```bash
cd frontend
npm run build
```

Expected:

```text
dist
```

输出中必须显示 Vite build 完成并生成 `dist`。

- [ ] **Step 17: 提交前端骨架**

Run:

```bash
git add frontend
git commit -m "feat: add frontend foundation"
```

Expected:

```text
feat: add frontend foundation
```

## Task 5: 本地联调冒烟验证

**Files:**

- Modify: `README.md`

- [ ] **Step 1: 补充 README 验证清单**

在 `README.md` 末尾追加：

````markdown
## 本地冒烟验证

1. 启动 MySQL 和 Redis：

```bash
docker compose up -d
```

2. 运行后端测试：

```bash
cd backend
mvn test
```

3. 启动后端：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

4. 检查后端健康接口：

```bash
curl http://localhost:8080/api/health
```

期望响应：

```json
{
  "status": "UP",
  "service": "aitour-backend"
}
```

5. 运行前端测试和构建：

```bash
cd frontend
npm run test
npm run build
```

6. 启动前端：

```bash
cd frontend
npm run dev
```

7. 打开前端页面：

```text
http://localhost:5173/home
```
````

- [ ] **Step 2: 运行完整后端验证**

Run:

```bash
cd backend
mvn test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: 运行完整前端验证**

Run:

```bash
cd frontend
npm run test
npm run build
```

Expected:

```text
1 passed
```

并且：

```text
dist
```

- [ ] **Step 4: 查看 Git 状态**

Run:

```bash
git status --short
```

Expected:

```text
 M README.md
```

如果测试或构建产生了未跟踪产物，确认 `.gitignore` 已排除 `target/`、`node_modules/`、`dist/` 和 `.superpowers/`。

- [ ] **Step 5: 提交 README 验证清单**

Run:

```bash
git add README.md
git commit -m "docs: add local smoke verification"
```

Expected:

```text
docs: add local smoke verification
```

## 自检清单

- 设计中的基础工程范围由 Task 1 到 Task 5 覆盖。
- MySQL 和 Redis 在 Task 1 与 Task 3 中覆盖。
- 后端健康检查由 Task 2 覆盖。
- Flyway 基础迁移由 Task 3 覆盖。
- 前端 Vue 3 基础页面由 Task 4 覆盖。
- 本地验证流程由 Task 5 覆盖。
- 本计划不实现认证、AI、MCP、SSE 和行程详情，避免第一阶段范围过大。
