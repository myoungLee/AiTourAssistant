/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.common.Result;
import com.aitour.common.dto.AuthDtos;
import com.aitour.service.AuthService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 注册和登录接口，后续退出登录会接入 Redis token 黑名单。
 *
 * @author myoung
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证接口", description = "用户注册、登录和访问令牌获取")
@Validated
public class AuthController {
    private final AuthService authService;

    /**
     * 注入认证服务接口。
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册新用户并返回访问令牌。
     */
    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "注册用户", description = "创建系统用户并返回 JWT accessToken 和 refreshToken。")
    @ApiResponse(responseCode = "200", description = "注册成功")
    @ApiResponse(responseCode = "409", description = "用户名已存在")
    public Result<AuthDtos.AuthResponse> register(
            @Parameter(description = "用户名") @RequestParam @NotBlank @Size(min = 3, max = 64) String username,
            @Parameter(description = "密码，至少 8 位") @RequestParam @NotBlank @Size(min = 8, max = 128) String password,
            @Parameter(description = "昵称") @RequestParam(required = false) @Size(max = 64) String nickname
    ) {
        AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest(username, password, nickname);
        return Result.success(authService.register(request));
    }

    /**
     * 使用用户名和密码登录并返回访问令牌。
     */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "用户登录", description = "校验用户名密码并返回 JWT accessToken 和 refreshToken。")
    @ApiResponse(responseCode = "200", description = "登录成功")
    @ApiResponse(responseCode = "401", description = "用户名或密码错误")
    public Result<AuthDtos.AuthResponse> login(
            @Parameter(description = "用户名") @RequestParam @NotBlank String username,
            @Parameter(description = "密码") @RequestParam @NotBlank String password
    ) {
        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest(username, password);
        return Result.success(authService.login(request));
    }
}
