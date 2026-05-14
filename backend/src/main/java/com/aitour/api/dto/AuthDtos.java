/*
 * @author myoung
 */
package com.aitour.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 认证接口请求和响应 DTO 集合，避免为简单载荷拆太多文件。
 *
 * @author myoung
 */
public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 64) String nickname
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            Long userId,
            String username,
            String nickname
    ) {
    }
}
