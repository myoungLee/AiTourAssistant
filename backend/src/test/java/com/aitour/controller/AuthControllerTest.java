/*
 * @author myoung
 */
package com.aitour.controller;

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

import static com.aitour.support.RedisMockSupport.wireInMemoryRedis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    /**
     * 为认证相关集成测试准备内存化 Redis mock。
     */
    @BeforeEach
    void setUpRedisMock() {
        wireInMemoryRedis(stringRedisTemplate, valueOperations, redisStore);
    }

    /**
     * 注册和登录都应返回 code=1，并将令牌放在 data 中。
     */
    @Test
    void shouldRegisterAndLogin() throws Exception {
        String username = "alice-" + UUID.randomUUID();
        mockMvc.perform(post("/api/auth/register")
                        .param("username", username)
                        .param("password", "password123")
                        .param("nickname", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(post("/api/auth/login")
                        .param("username", username)
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    /**
     * 退出登录后，应将 accessToken 加入黑名单，并拒绝继续访问受保护接口。
     */
    @Test
    void shouldLogoutAndRejectBlacklistedAccessToken() throws Exception {
        String token = registerAndGetToken("logout-user-" + UUID.randomUUID());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token)
                        .param("accessToken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        assertThat(redisStore.keySet()).anyMatch(key -> key.startsWith("auth:blacklist:"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("请先登录"));
    }

    /**
     * 注册测试用户并返回 accessToken，供后续受保护接口测试复用。
     */
    private String registerAndGetToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .param("username", username)
                        .param("password", "password123")
                        .param("nickname", "Auth User"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
