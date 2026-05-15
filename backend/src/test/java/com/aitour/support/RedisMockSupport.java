/*
 * @author myoung
 */
package com.aitour.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 为 Spring Boot 测试中的 Redis 相关 Bean 提供内存化 mock 行为，避免测试依赖真实 Redis 进程。
 *
 * @author myoung
 */
public final class RedisMockSupport {
    /**
     * 工具类不需要实例化。
     */
    private RedisMockSupport() {
    }

    /**
     * 将 StringRedisTemplate 和 ValueOperations 绑定到内存 Map，支持 get、set 和 delete 的基础行为。
     */
    public static void wireInMemoryRedis(
            StringRedisTemplate stringRedisTemplate,
            ValueOperations<String, String> valueOperations,
            Map<String, String> redisStore
    ) {
        redisStore.clear();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(invocation -> redisStore.get(invocation.getArgument(0))).when(valueOperations).get(anyString());
        doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString());
        when(stringRedisTemplate.hasKey(anyString())).thenAnswer(invocation -> redisStore.containsKey(invocation.getArgument(0)));
        doAnswer(invocation -> redisStore.remove(invocation.getArgument(0)) != null).when(stringRedisTemplate).delete(anyString());
    }
}
