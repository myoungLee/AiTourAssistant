/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.common.dto.AuthDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.aitour.support.RedisMockSupport.wireInMemoryRedis;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

/**
 * 验证认证流程会把 refreshToken 写入 Redis，供后续真正的刷新令牌能力复用。
 *
 * @author myoung
 */
@SpringBootTest
class AuthRedisTokenTest {

    @Autowired
    private AuthService authService;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    /**
     * 为认证服务测试准备内存化 Redis mock。
     */
    @BeforeEach
    void setUpRedisMock() {
        wireInMemoryRedis(stringRedisTemplate, valueOperations, redisStore);
    }

    /**
     * 注册成功后，应把 refreshToken 与 userId 的映射写入 Redis 并设置过期时间。
     */
    @Test
    void shouldStoreRefreshTokenInRedisWhenRegistering() {
        AuthDtos.AuthResponse response = authService.register(new AuthDtos.RegisterRequest(
                "register-user-" + UUID.randomUUID(),
                "password123",
                "Register User"
        ));

        verify(valueOperations).set(
                "auth:refresh:" + response.refreshToken(),
                String.valueOf(response.userId()),
                Duration.ofDays(14)
        );
    }

    /**
     * 登录成功后，也应把新签发的 refreshToken 写入 Redis 并设置过期时间。
     */
    @Test
    void shouldStoreRefreshTokenInRedisWhenLoggingIn() {
        String username = "login-user-" + UUID.randomUUID();
        authService.register(new AuthDtos.RegisterRequest(username, "password123", "Login User"));
        clearInvocations(valueOperations);

        AuthDtos.AuthResponse response = authService.login(new AuthDtos.LoginRequest(username, "password123"));

        verify(valueOperations).set(
                "auth:refresh:" + response.refreshToken(),
                String.valueOf(response.userId()),
                Duration.ofDays(14)
        );
    }
}
