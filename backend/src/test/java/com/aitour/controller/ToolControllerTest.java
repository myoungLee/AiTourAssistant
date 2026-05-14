/*
 * @author myoung
 */
package com.aitour.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证工具状态接口返回统一 Result 响应。
 *
 * @author myoung
 */
@SpringBootTest
@AutoConfigureMockMvc
class ToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 已登录用户查询工具状态时，工具列表应位于 Result.data 中。
     */
    @Test
    void shouldReturnToolStatus() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/tools/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.tools").isArray());
    }

    /**
     * 注册临时用户并从统一 Result.data 中提取访问令牌。
     */
    private String registerAndGetToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .param("username", "tool-user")
                        .param("password", "password123")
                        .param("nickname", "Tool User"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
