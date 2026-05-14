/*
 * @author myoung
 */
package com.aitour.service.impl;

import com.aitour.common.dto.AuthDtos;
import com.aitour.common.entity.User;
import com.aitour.common.exception.ApiException;
import com.aitour.config.security.JwtTokenService;
import com.aitour.domain.UserStatus;
import com.aitour.mapper.UserMapper;
import com.aitour.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 认证应用服务，封装注册、登录和 token 签发流程。
 *
 * @author myoung
 */
@Service
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    /**
     * 注入用户 mapper、密码编码器和 JWT 签发服务。
     */
    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 注册新用户，写入用户表后签发访问令牌。
     */
    @Override
    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (existing != null) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }

        Instant now = Instant.now();
        User user = new User();
        user.setId(newPositiveId());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null ? request.username() : request.nickname());
        user.setStatus(UserStatus.ENABLED.name());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        return toAuthResponse(user);
    }

    /**
     * 校验用户名密码和用户状态，校验通过后签发访问令牌。
     */
    @Override
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_DISABLED", "用户已被禁用");
        }
        return toAuthResponse(user);
    }

    /**
     * 将用户实体转换为认证响应，并生成 accessToken 和 refreshToken。
     */
    private AuthDtos.AuthResponse toAuthResponse(User user) {
        String accessToken = jwtTokenService.createAccessToken(user.getId(), user.getUsername());
        String refreshToken = UUID.randomUUID().toString();
        return new AuthDtos.AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername(), user.getNickname());
    }

    /**
     * 生成正数主键，沿用当前项目的本地 UUID 主键策略。
     */
    private Long newPositiveId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
