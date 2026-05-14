/*
 * @author myoung
 */
package com.aitour.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "用户注册请求")
    public record RegisterRequest(
            @Schema(description = "用户名", example = "alice") @NotBlank @Size(min = 3, max = 64) String username,
            @Schema(description = "密码，至少 8 位", example = "password123") @NotBlank @Size(min = 8, max = 128) String password,
            @Schema(description = "昵称", example = "Alice") @Size(max = 64) String nickname
    ) {
    }

    @Schema(description = "用户登录请求")
    public record LoginRequest(
            @Schema(description = "用户名", example = "alice") @NotBlank String username,
            @Schema(description = "密码", example = "password123") @NotBlank String password
    ) {
    }

    @Schema(description = "认证响应")
    public record AuthResponse(
            @Schema(description = "JWT 访问令牌")
            String accessToken,
            @Schema(description = "刷新令牌")
            String refreshToken,
            @Schema(description = "用户 ID", example = "10001")
            Long userId,
            @Schema(description = "用户名", example = "alice")
            String username,
            @Schema(description = "昵称", example = "Alice")
            String nickname
    ) {
    }
}
