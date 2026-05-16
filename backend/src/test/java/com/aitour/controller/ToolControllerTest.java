/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.client.mcp.McpToolRegistry;
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
import java.util.concurrent.ConcurrentHashMap;

import static com.aitour.support.RedisMockSupport.wireInMemoryRedis;
import static org.mockito.Mockito.when;
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

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    @MockitoBean
    private McpToolRegistry toolRegistry;

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    /**
     * 为工具状态接口测试准备内存化 Redis mock。
     */
    @BeforeEach
    void setUpRedisMock() {
        wireInMemoryRedis(stringRedisTemplate, valueOperations, redisStore);
    }

    /**
     * 已登录用户查询工具状态时，工具列表应位于 Result.data 中。
     */
    @Test
    void shouldReturnToolStatus() throws Exception {
        when(toolRegistry.mode()).thenReturn("external-test");
        when(toolRegistry.names()).thenReturn(java.util.List.of("weather.query", "place.search"));
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/tools/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.mode").value("external-test"))
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
