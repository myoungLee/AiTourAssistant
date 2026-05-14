/*
 * @author myoung
 */
package com.aitour.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 签发配置，通过环境变量覆盖密钥，避免提交真实密钥。
 *
 * @author myoung
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        long accessTokenMinutes,
        long refreshTokenDays
) {
}
