/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.common.dto.AuthDtos;

/**
 * 认证服务接口，定义注册和登录等对外业务能力。
 *
 * @author myoung
 */
public interface AuthService {

    /**
     * 注册新用户并返回访问令牌信息。
     */
    AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request);

    /**
     * 校验用户名密码并返回访问令牌信息。
     */
    AuthDtos.AuthResponse login(AuthDtos.LoginRequest request);
}
