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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证用户接口在统一 Result 响应结构下可以查询和更新用户画像。
 *
 * @author myoung
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 登录用户应能读取当前用户信息，并通过字段参数更新旅行画像。
     */
    @Test
    void shouldReadAndUpdateCurrentUserProfile() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .param("username", "bob")
                        .param("password", "password123")
                        .param("nickname", "Bob"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.username").value("bob"));

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .param("travelStyle", "轻松")
                        .param("defaultBudgetLevel", "中等")
                        .param("preferredTransport", "地铁"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.travelStyle").value("轻松"));
    }
}
