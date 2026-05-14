/*
 * @author myoung
 */
package com.aitour.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 Swagger/OpenAPI 自动生成的接口测试文档可以无需登录访问。
 *
 * @author myoung
 */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * OpenAPI JSON 应包含主要 RESTful 接口路径，供 Swagger UI 自动渲染测试文档。
     */
    @Test
    void shouldExposeOpenApiDocsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths", hasKey("/api/auth/register")))
                .andExpect(jsonPath("$.paths", hasKey("/api/trips/stream-plan")))
                .andExpect(jsonPath("$.components.securitySchemes", hasKey("bearerAuth")));
    }

    /**
     * Swagger UI 入口应允许浏览器访问，便于本地接口调试。
     */
    @Test
    void shouldExposeSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
