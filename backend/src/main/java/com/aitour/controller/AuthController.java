/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.common.dto.AuthDtos;
import com.aitour.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册和登录接口，后续退出登录会接入 Redis token 黑名单。
 *
 * @author myoung
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证接口", description = "用户注册、登录和访问令牌获取")
public class AuthController {
    private final AuthService authService;

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
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * 使用用户名和密码登录并返回访问令牌。
     */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "用户登录", description = "校验用户名密码并返回 JWT accessToken 和 refreshToken。")
    @ApiResponse(responseCode = "200", description = "登录成功")
    @ApiResponse(responseCode = "401", description = "用户名或密码错误")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }
}
