/*
 * @author myoung
 */
package com.aitour.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 负责签发和解析 access token，并维护 refreshToken 与 accessToken 黑名单的 Redis 状态。
 *
 * @author myoung
 */
@Service
public class JwtTokenService {
    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String ACCESS_TOKEN_BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    private final JwtProperties properties;
    private final SecretKey key;
    private final StringRedisTemplate stringRedisTemplate;

    public JwtTokenService(JwtProperties properties, StringRedisTemplate stringRedisTemplate) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 创建 JWT accessToken，供受保护接口访问使用。
     */
    public String createAccessToken(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessTokenMinutes() * 60)))
                .signWith(key)
                .compact();
    }

    /**
     * 生成 refreshToken，并把 refreshToken -> userId 的映射写入 Redis。
     */
    public String createRefreshToken(Long userId) {
        String refreshToken = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + refreshToken,
                String.valueOf(userId),
                Duration.ofDays(properties.refreshTokenDays())
        );
        return refreshToken;
    }

    /**
     * 解析 accessToken，提取当前登录用户。
     */
    public CurrentUser parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        return new CurrentUser(Long.valueOf(claims.getSubject()), claims.get("username", String.class));
    }

    /**
     * 判断 accessToken 是否已被加入 Redis 黑名单。
     */
    public boolean isAccessTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(ACCESS_TOKEN_BLACKLIST_KEY_PREFIX + token));
    }

    /**
     * 将 accessToken 加入 Redis 黑名单，并把过期时间设置为该 token 的剩余有效期。
     */
    public void blacklistAccessToken(String token) {
        Claims claims = parseClaims(token);
        long seconds = Math.max(1, claims.getExpiration().toInstant().getEpochSecond() - Instant.now().getEpochSecond());
        stringRedisTemplate.opsForValue().set(
                ACCESS_TOKEN_BLACKLIST_KEY_PREFIX + token,
                "1",
                Duration.ofSeconds(seconds)
        );
    }

    /**
     * 使用统一的 JWT 配置解析声明，供用户解析和黑名单 TTL 计算复用。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
