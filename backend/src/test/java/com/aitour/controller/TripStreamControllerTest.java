/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.client.ai.AiChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.aitour.support.RedisMockSupport.wireInMemoryRedis;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

    @MockitoBean
    private AiChatClient aiChatClient;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    /**
     * 为流式接口测试准备内存化 Redis mock。
     */
    @BeforeEach
    void setUpRedisMock() {
        wireInMemoryRedis(stringRedisTemplate, valueOperations, redisStore);
    }

    /**
     * 已登录用户使用字段参数提交行程需求后，接口应返回异步 SSE 请求。
     */
    @Test
    void shouldStartStreamPlanRequest() throws Exception {
        mockAiStreamResponse();
        String token = registerAndGetToken();

        mockMvc.perform(post("/api/trips/stream-plan")
                        .header("Authorization", "Bearer " + token)
                        .param("destination", "成都")
                        .param("startDate", "2099-06-01")
                        .param("days", "3")
                        .param("budget", "3000")
                        .param("peopleCount", "2")
                        .param("preferences", "美食")
                        .param("userInput", "想吃火锅"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    /**
     * 已登录用户使用字段参数对自己的行程发起二次调整时，接口应返回异步 SSE 请求。
     */
    @Test
    void shouldStartAdjustStreamRequest() throws Exception {
        mockAiStreamResponse();
        String token = registerAndGetToken();
        String planId = createDraftAndGetPlanId(token);

        mockMvc.perform(post("/api/trips/" + planId + "/adjust-stream")
                        .header("Authorization", "Bearer " + token)
                        .param("instruction", "把第二天安排得轻松一些"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    /**
     * 注册临时用户并从统一 Result.data 中提取 accessToken，供受保护接口测试使用。
     */
    private String registerAndGetToken() throws Exception {
        String username = "stream-user-" + UUID.randomUUID();
        String body = mockMvc.perform(post("/api/auth/register")
                        .param("username", username)
                        .param("password", "password123")
                        .param("nickname", "Stream User"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    /**
     * 创建一个草稿行程并从统一 Result.data 中提取 planId，供二次调整流式接口验证使用。
     */
    private String createDraftAndGetPlanId(String token) throws Exception {
        String body = mockMvc.perform(post("/api/trips/draft")
                        .header("Authorization", "Bearer " + token)
                        .param("destination", "成都")
                        .param("startDate", "2099-06-01")
                        .param("days", "3")
                        .param("budget", "3000")
                        .param("peopleCount", "2")
                        .param("preferences", "美食")
                        .param("userInput", "想吃火锅"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"planId\":([0-9]+).*", "$1");
    }

    /**
     * 使用测试替身隔离真实 Spring AI 模型调用，避免异步 SSE 测试访问外部中转服务。
     */
    private void mockAiStreamResponse() {
        doAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(1);
            onDelta.accept("测试 AI 摘要");
            return null;
        }).when(aiChatClient).streamChat(any(), any());
    }
}
