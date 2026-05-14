/*
 * @author myoung
 */
package com.aitour.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 负责签发和解析 access token，refresh token 状态后续接入 Redis。
 *
 * @author myoung
 */
@Service
public class JwtTokenService {
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

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

    public CurrentUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new CurrentUser(Long.valueOf(claims.getSubject()), claims.get("username", String.class));
    }
}
