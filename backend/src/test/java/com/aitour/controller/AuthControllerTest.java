/*
 * @author myoung
 */
package com.aitour.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证认证接口使用字段参数提交后可以返回统一 Result 响应。
 *
 * @author myoung
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 注册和登录都应返回 code=1，并将令牌放在 data 中。
     */
    @Test
    void shouldRegisterAndLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .param("username", "alice")
                        .param("password", "password123")
                        .param("nickname", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value("alice"));

        mockMvc.perform(post("/api/auth/login")
                        .param("username", "alice")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }
}
