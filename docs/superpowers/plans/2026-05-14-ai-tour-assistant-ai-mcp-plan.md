# AI 旅游助手 AI 与 MCP 工具抽象 Implementation Plan

> 历史版本说明：本计划保留为 2026-05-14 的拆分设计记录。当前后续执行以 `docs/superpowers/plans/2026-05-15-ai-tour-assistant-current-architecture-plan.md` 为准；AI 接入已改为 Spring AI 官方 OpenAI ChatClient，不再按本文中的 JDK 21 `HttpClient` 或 `OpenAiCompatibleChatClient` 实施。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 OpenAI-compatible AI 客户端抽象、本地 MCP 风格工具、工具注册表和调用日志，为后续流式行程生成提供稳定能力。

**Architecture:** AI 和工具都通过接口隔离。业务服务只依赖 `AiChatClient` 和 `McpToolRegistry`。本地工具默认启用，外部 MCP Server 通过 `ExternalMcpToolAdapter` 预留扩展位。

**Tech Stack:** Spring Boot 3、JDK 21 HttpClient、Jackson、MyBatis-Plus、JUnit 5、MockWebServer 或 WireMock。

---

## 前置条件

- 基础工程、认证计划、行程数据计划已完成。
- 后端可以保存 `trip_plan`。

## 文件结构规划

```text
backend/src/main/resources/db/migration/V3__init_ai_tool_logs.sql
backend/src/main/java/com/aitour/client/ai/AiChatClient.java
backend/src/main/java/com/aitour/config/ai/AiProperties.java
backend/src/main/java/com/aitour/client/ai/ChatRequest.java
backend/src/main/java/com/aitour/client/ai/OpenAiCompatibleChatClient.java
backend/src/main/java/com/aitour/client/ai/PromptTemplateService.java
backend/src/main/java/com/aitour/client/mcp/TravelTool.java
backend/src/main/java/com/aitour/client/mcp/ToolRequest.java
backend/src/main/java/com/aitour/client/mcp/ToolResult.java
backend/src/main/java/com/aitour/client/mcp/McpToolRegistry.java
backend/src/main/java/com/aitour/client/mcp/local/LocalWeatherTool.java
backend/src/main/java/com/aitour/client/mcp/local/LocalPlaceSearchTool.java
backend/src/main/java/com/aitour/client/mcp/local/LocalRouteTool.java
backend/src/main/java/com/aitour/client/mcp/local/LocalBudgetTool.java
backend/src/main/java/com/aitour/client/mcp/external/ExternalMcpToolAdapter.java
backend/src/main/java/com/aitour/common/entity/LlmCallLog.java
backend/src/main/java/com/aitour/common/entity/ToolCallLog.java
backend/src/main/java/com/aitour/mapper/LlmCallLogMapper.java
backend/src/main/java/com/aitour/mapper/ToolCallLogMapper.java
backend/src/main/java/com/aitour/controller/ToolController.java
```

## Task 1: 创建 AI 与工具日志表

**Files:**

- Create: `backend/src/main/resources/db/migration/V3__init_ai_tool_logs.sql`
- Create: `backend/src/test/java/com/aitour/AiToolMigrationTest.java`

- [ ] **Step 1: 创建日志表迁移**

```sql
create table llm_call_log (
    id bigint primary key,
    user_id bigint,
    plan_id bigint,
    provider varchar(64) not null,
    model varchar(128) not null,
    prompt_summary text,
    response_summary text,
    token_usage_json text,
    latency_ms bigint,
    success boolean not null,
    error_message text,
    created_at timestamp not null
);

create table tool_call_log (
    id bigint primary key,
    user_id bigint,
    plan_id bigint,
    tool_name varchar(128) not null,
    request_json text,
    response_summary text,
    latency_ms bigint,
    success boolean not null,
    error_message text,
    created_at timestamp not null
);

create index idx_llm_call_log_user_id on llm_call_log (user_id);
create index idx_llm_call_log_plan_id on llm_call_log (plan_id);
create index idx_tool_call_log_user_id on tool_call_log (user_id);
create index idx_tool_call_log_plan_id on tool_call_log (plan_id);
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
class AiToolMigrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateAiAndToolLogTables() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name in ('llm_call_log', 'tool_call_log')
                """, Integer.class);

        assertThat(count).isEqualTo(2);
    }
}
```

## Task 2: AI 客户端抽象

**Files:**

- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create AI package files.
- Create: `backend/src/test/java/com/aitour/ai/PromptTemplateServiceTest.java`

- [ ] **Step 1: 使用 JDK 21 HttpClient**

在 `backend/pom.xml` 增加：

```xml
不额外引入 WebFlux；AI HTTP 调用使用 JDK 21 自带 `java.net.http.HttpClient`，避免在 Servlet MVC 应用里启动额外 Reactive HTTP 连接器。
```

- [ ] **Step 2: 增加 AI 配置**

```yaml
ai:
  provider: openai-compatible
  base-url: ${AI_BASE_URL:https://api.openai.com/v1}
  api-key: ${AI_API_KEY:}
  model: ${AI_MODEL:gpt-4o-mini}
  timeout-seconds: 60
```

- [ ] **Step 3: 创建 AI 类型**

`AiProperties.java`：

```java
package com.aitour.client.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(String provider, String baseUrl, String apiKey, String model, int timeoutSeconds) {
}
```

`ChatRequest.java`：

```java
package com.aitour.client.ai;

import java.util.List;

public record ChatRequest(List<Message> messages, boolean stream) {
    public record Message(String role, String content) {
    }
}
```

`AiChatClient.java`：

```java
package com.aitour.client.ai;

import java.util.function.Consumer;

public interface AiChatClient {
    String chat(ChatRequest request);

    void streamChat(ChatRequest request, Consumer<String> onDelta);
}
```

- [ ] **Step 4: 创建 Prompt 服务**

```java
package com.aitour.client.ai;

import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {

    public String buildRequirementNormalizePrompt(String userInput) {
        return """
                你是旅行需求解析助手。请从用户输入中提取目的地、天数、预算、人数、偏好和节奏。
                如果信息缺失，使用 null，不要编造。
                用户输入：
                %s
                """.formatted(userInput);
    }

    public String buildPlanSummaryPrompt(String planJson) {
        return """
                你是旅行规划助手。请基于以下结构化行程生成简洁中文说明，突出天气、路线和预算提醒。
                行程 JSON：
                %s
                """.formatted(planJson);
    }
}
```

- [ ] **Step 5: 测试 Prompt 服务**

```java
package com.aitour.client.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateServiceTest {

    @Test
    void shouldBuildRequirementPrompt() {
        PromptTemplateService service = new PromptTemplateService();

        String prompt = service.buildRequirementNormalizePrompt("成都三天美食游");

        assertThat(prompt).contains("旅行需求解析助手");
        assertThat(prompt).contains("成都三天美食游");
    }
}
```

- [ ] **Step 6: 创建 OpenAI-compatible 客户端**

```java
package com.aitour.client.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.function.Consumer;

@Component
@EnableConfigurationProperties(AiProperties.class)
public class OpenAiCompatibleChatClient implements AiChatClient {
    private final AiProperties properties;
    private final HttpClient httpClient;

    public OpenAiCompatibleChatClient(AiProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String chat(ChatRequest request) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "stream", false,
                "messages", request.messages().stream()
                        .map(message -> Map.of("role", message.role(), "content", message.content()))
                        .toList()
        );
        // 实际项目代码使用 JDK 21 HttpClient 发送 OpenAI-compatible 请求。
        return "{}";
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<String> onDelta) {
        String response = chat(new ChatRequest(request.messages(), false));
        onDelta.accept(response);
    }
}
```

## Task 3: MCP 工具接口和本地工具

**Files:**

- Create MCP package files.
- Create: `backend/src/test/java/com/aitour/mcp/McpToolRegistryTest.java`

- [ ] **Step 1: 创建工具基础类型**

```java
package com.aitour.client.mcp;

import java.util.Map;

public record ToolRequest(Long userId, Long planId, Map<String, Object> arguments) {
}
```

```java
package com.aitour.client.mcp;

import java.util.Map;

public record ToolResult(String toolName, boolean success, String summary, Map<String, Object> data) {
}
```

```java
package com.aitour.client.mcp;

public interface TravelTool {
    String name();

    ToolResult execute(ToolRequest request);
}
```

- [ ] **Step 2: 创建本地天气工具**

```java
package com.aitour.client.mcp.local;

import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.client.mcp.TravelTool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LocalWeatherTool implements TravelTool {
    @Override
    public String name() {
        return "weather.query";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        String city = String.valueOf(request.arguments().getOrDefault("city", "目的地"));
        return new ToolResult(name(), true, city + "未来天气整体适合出行，午后注意防晒或降雨。", Map.of(
                "city", city,
                "condition", "多云",
                "temperature", "22-29"
        ));
    }
}
```

- [ ] **Step 3: 创建本地景点、路线、预算工具**

分别实现：

```text
LocalPlaceSearchTool -> name = place.search，返回 3 个本地景点。
LocalRouteTool -> name = route.plan，返回交通方式和预计分钟数。
LocalBudgetTool -> name = budget.estimate，返回 hotel/food/transport/ticket/other 估算。
```

每个类都实现 `TravelTool`，返回 `ToolResult`，并标记 `success=true`。

- [ ] **Step 4: 创建工具注册表**

```java
package com.aitour.client.mcp;

import com.aitour.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class McpToolRegistry {
    private final Map<String, TravelTool> tools;

    public McpToolRegistry(List<TravelTool> tools) {
        this.tools = tools.stream().collect(Collectors.toMap(TravelTool::name, Function.identity()));
    }

    public ToolResult execute(String name, ToolRequest request) {
        TravelTool tool = tools.get(name);
        if (tool == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "工具不存在: " + name);
        }
        return tool.execute(request);
    }

    public List<String> names() {
        return tools.keySet().stream().sorted().toList();
    }
}
```

- [ ] **Step 5: 测试工具注册表**

```java
package com.aitour.client.mcp;

import com.aitour.client.mcp.local.LocalWeatherTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolRegistryTest {

    @Test
    void shouldExecuteLocalWeatherTool() {
        McpToolRegistry registry = new McpToolRegistry(List.of(new LocalWeatherTool()));

        ToolResult result = registry.execute("weather.query", new ToolRequest(1L, 2L, Map.of("city", "成都")));

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("成都");
    }
}
```

## Task 4: 工具状态接口

**Files:**

- Create: `backend/src/main/java/com/aitour/controller/ToolController.java`
- Create: `backend/src/test/java/com/aitour/controller/ToolControllerTest.java`

- [ ] **Step 1: 创建接口测试**

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
class ToolControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnToolStatus() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/tools/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools").isArray());
    }

    private String registerAndGetToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"tool-user","password":"password123","nickname":"Tool User"}
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\\"accessToken\\":\\"([^\\"]+)\\".*", "$1");
    }
}
```

- [ ] **Step 2: 创建 Controller**

```java
package com.aitour.controller;

import com.aitour.client.mcp.McpToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tools")
public class ToolController {
    private final McpToolRegistry toolRegistry;

    public ToolController(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "mode", "local",
                "tools", toolRegistry.names()
        );
    }
}
```

## Task 5: 验证和提交

**Files:**

- Modify: `README.md`

- [ ] **Step 1: 运行后端测试**

Run:

```bash
cd backend
mvn test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 2: 追加工具说明**

````markdown
## 工具状态接口

```bash
curl http://localhost:8080/api/tools/status \
  -H "Authorization: Bearer <accessToken>"
```
````

- [ ] **Step 3: 提交 AI 与 MCP 抽象**

Run:

```bash
git add backend README.md
git commit -m "feat: add ai client and mcp tools"
```

Expected:

```text
feat: add ai client and mcp tools
```

## 自检清单

- AI 客户端通过接口隔离。
- 本地工具默认可用。
- 工具注册表按工具名分发。
- 工具状态接口可查询当前启用工具。
- 外部 MCP 工具适配层作为后续增强，不阻塞 MVP。
