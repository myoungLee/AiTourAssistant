/*
 * @author myoung
 */
package com.aitour.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证行程流式生成接口可以启动异步 SSE 响应。
 *
 * @author myoung
 */
@SpringBootTest
@AutoConfigureMockMvc
class TripStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 已登录用户提交行程需求后，接口应返回异步 SSE 请求。
     */
    @Test
    void shouldStartStreamPlanRequest() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/trips/stream-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食"],"userInput":"想吃火锅"}
                                """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    /**
     * 已登录用户对自己的行程发起二次调整时，接口应返回异步 SSE 请求。
     */
    @Test
    void shouldStartAdjustStreamRequest() throws Exception {
        String token = registerAndGetToken();
        String planId = createDraftAndGetPlanId(token);

        mockMvc.perform(post("/api/trips/" + planId + "/adjust-stream")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instruction":"把第二天安排得轻松一些"}
                                """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    /**
     * 注册临时用户并提取 accessToken，供受保护接口测试使用。
     */
    private String registerAndGetToken() throws Exception {
        String username = "stream-user-" + UUID.randomUUID();
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"password123","nickname":"Stream User"}
                                """.formatted(username)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    /**
     * 创建一个草稿行程并提取 planId，供二次调整流式接口验证使用。
     */
    private String createDraftAndGetPlanId(String token) throws Exception {
        String body = mockMvc.perform(post("/api/trips/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食"],"userInput":"想吃火锅"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"planId\":([0-9]+).*", "$1");
    }
}
