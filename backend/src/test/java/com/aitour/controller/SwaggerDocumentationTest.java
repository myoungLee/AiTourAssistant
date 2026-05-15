/*
 * @author myoung
 */
package com.aitour.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 Swagger/OpenAPI 自动生成的接口测试文档可供本地联调直接使用。
 *
 * @author myoung
 */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * OpenAPI JSON 应包含核心路径、Bearer 鉴权、字段参数定义和统一响应 schema。
     */
    @Test
    void shouldExposeFieldParametersAndTypedResultSchemas() throws Exception {
        String content = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(content);
        JsonNode paths = root.path("paths");
        JsonNode schemas = root.path("components").path("schemas");

        assertThat(paths.has("/api/auth/register")).isTrue();
        assertThat(paths.has("/api/trips/stream-plan")).isTrue();
        assertThat(root.path("components").path("securitySchemes").has("bearerAuth")).isTrue();

        assertFieldParameters(paths.path("/api/auth/register").path("post"), "username", "password", "nickname");
        assertFieldParameters(paths.path("/api/trips/stream-plan").path("post"),
                "destination", "startDate", "days", "budget", "peopleCount", "preferences", "userInput");
        assertFieldParameters(paths.path("/api/users/me/profile").path("put"),
                "gender", "ageRange", "travelStyle", "defaultBudgetLevel", "preferredTransport", "preferencesJson");

        assertThat(paths.path("/api/auth/register").path("post").path("requestBody").isMissingNode()).isTrue();
        assertThat(paths.path("/api/trips/stream-plan").path("post").path("requestBody").isMissingNode()).isTrue();
        assertThat(paths.path("/api/users/me/profile").path("put").path("requestBody").isMissingNode()).isTrue();

        assertResultSchemaExists(schemas, "AuthResponse");
        assertResultSchemaExists(schemas, "CurrentUserResponse");
        assertResultSchemaExists(schemas, "CreateDraftResponse");
        assertResultSchemaExists(schemas, "HealthResponse");
        assertResultSchemaExists(schemas, "ToolStatusResponse");
        assertThat(schemas.has("HealthResponse")).isTrue();
        assertThat(schemas.has("ToolStatusResponse")).isTrue();
        assertThat(schemas.has("CreateDraftResponse")).isTrue();

        assertThat(paths.path("/api/trips/{id}").path("get").path("responses").has("404")).isTrue();
    }

    /**
     * Swagger UI 入口应允许浏览器访问，便于本地接口调试。
     */
    @Test
    void shouldExposeSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * 断言一个操作节点只使用字段参数，不出现 JSON requestBody。
     */
    private void assertFieldParameters(JsonNode operation, String... expectedParameters) {
        assertThat(operation.isMissingNode()).isFalse();
        JsonNode parametersNode = operation.path("parameters");
        assertThat(parametersNode.isArray()).isTrue();
        Set<String> parameterNames = new HashSet<>();
        for (JsonNode parameterNode : parametersNode) {
            parameterNames.add(parameterNode.path("name").asText());
            assertThat(parameterNode.path("in").asText()).isIn("query", "path", "header");
        }
        assertThat(parameterNames).contains(expectedParameters);
    }

    /**
     * 查找 `Result<T>` 包装后的 schema，并验证其中包含 code/msg/data 三个字段。
     */
    private void assertResultSchemaExists(JsonNode schemas, String dataSchemaName) {
        Iterator<String> fieldNames = schemas.fieldNames();
        while (fieldNames.hasNext()) {
            String schemaName = fieldNames.next();
            JsonNode schemaNode = schemas.path(schemaName);
            JsonNode propertiesNode = schemaNode.path("properties");
            if (!schemaName.startsWith("Result") || !propertiesNode.has("code") || !propertiesNode.has("msg") || !propertiesNode.has("data")) {
                continue;
            }
            JsonNode dataNode = propertiesNode.path("data");
            JsonNode dataRefNode = dataNode.path("$ref");
            if (dataRefNode.isTextual() && dataRefNode.asText().endsWith("/" + dataSchemaName)) {
                return;
            }
        }
        throw new AssertionError("未找到包装数据类型为 " + dataSchemaName + " 的 Result schema");
    }
}
