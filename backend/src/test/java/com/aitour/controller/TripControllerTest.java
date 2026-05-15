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
import java.util.concurrent.ConcurrentHashMap;

import static com.aitour.support.RedisMockSupport.wireInMemoryRedis;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证行程草稿和查询接口在统一 Result 响应结构下正常工作。
 *
 * @author myoung
 */
@SpringBootTest
@AutoConfigureMockMvc
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    /**
     * 为需要登录态的行程接口测试准备内存化 Redis mock。
     */
    @BeforeEach
    void setUpRedisMock() {
        wireInMemoryRedis(stringRedisTemplate, valueOperations, redisStore);
    }

    /**
     * 创建草稿、查询列表和详情时，业务数据应位于 Result.data 中。
     */
    @Test
    void shouldCreateDraftAndQueryTrips() throws Exception {
        String token = registerAndGetToken();

        String response = mockMvc.perform(post("/api/trips/draft")
                        .header("Authorization", "Bearer " + token)
                        .param("destination", "成都")
                        .param("startDate", "2099-06-01")
                        .param("days", "3")
                        .param("budget", "3000")
                        .param("peopleCount", "2")
                        .param("preferences", "美食")
                        .param("userInput", "想吃火锅"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.planId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String planId = response.replaceAll(".*\"planId\":([0-9]+).*", "$1");

        mockMvc.perform(get("/api/trips").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data[0].title").value("成都3日智能行程"));

        mockMvc.perform(get("/api/trips/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(Long.parseLong(planId)));
    }

    /**
     * 注册临时用户并从统一 Result.data 中提取访问令牌。
     */
    private String registerAndGetToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .param("username", "trip-user")
                        .param("password", "password123")
                        .param("nickname", "Trip User"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
