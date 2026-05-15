/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.common.entity.User;
import com.aitour.mapper.UserMapper;
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
import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private UserMapper userMapper;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    /**
     * 为当前用户与画像接口测试准备内存化 Redis mock。
     */
    @BeforeEach
    void setUpRedisMock() {
        wireInMemoryRedis(stringRedisTemplate, valueOperations, redisStore);
    }

    /**
     * 当前用户接口应优先命中缓存，画像更新后需要清理缓存，避免继续返回旧资料。
     */
    @Test
    void shouldReadCurrentUserFromCacheAndEvictAfterProfileUpdate() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .param("username", "bob")
                        .param("password", "password123")
                        .param("nickname", "Bob"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        Long userId = Long.valueOf(body.replaceAll(".*\"userId\":([0-9]+).*", "$1"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.username").value("bob"))
                .andExpect(jsonPath("$.data.nickname").value("Bob"));

        assertThat(redisStore).containsKey("user:current:" + userId);

        User user = userMapper.selectById(userId);
        user.setNickname("Bob Changed");
        userMapper.updateById(user);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Bob"));

        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + token)
                        .param("travelStyle", "轻松")
                        .param("defaultBudgetLevel", "中等")
                        .param("preferredTransport", "地铁"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.travelStyle").value("轻松"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Bob Changed"));
    }
}
