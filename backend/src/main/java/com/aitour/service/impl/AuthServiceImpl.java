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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 认证应用服务，负责注册、登录和登出流程编排。
 *
 * @author myoung
 */
@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserMapper userMapper;
    private final JwtTokenService jwtTokenService;

    /**
     * 注入用户持久化组件和 JWT 服务。
     */
    public AuthServiceImpl(UserMapper userMapper, JwtTokenService jwtTokenService) {
        this.userMapper = userMapper;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 注册新用户，当前按测试要求明文存储密码。
     */
    @Override
    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        log.info("用户注册开始，username={} nicknameProvided={}", request.username(), request.nickname() != null);
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (existing != null) {
            log.warn("用户注册拒绝，username={} reason=USERNAME_EXISTS", request.username());
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }

        Instant now = Instant.now();
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(request.password());
        user.setNickname(request.nickname() == null ? request.username() : request.nickname());
        user.setStatus(UserStatus.ENABLED.name());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        log.info("用户注册成功，userId={} username={}", user.getId(), user.getUsername());
        return toAuthResponse(user);
    }

    /**
     * 校验用户名密码并签发访问令牌。
     */
    @Override
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        log.info("用户登录开始，username={}", request.username());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (user == null || !request.password().equals(user.getPasswordHash())) {
            log.warn("用户登录失败，username={} reason=BAD_CREDENTIALS", request.username());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        if (!user.isEnabled()) {
            log.warn("用户登录失败，userId={} username={} reason=USER_DISABLED", user.getId(), user.getUsername());
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_DISABLED", "用户已被禁用");
        }

        log.info("用户登录成功，userId={} username={}", user.getId(), user.getUsername());
        return toAuthResponse(user);
    }

    /**
     * 将当前 accessToken 加入黑名单，表示用户主动退出登录。
     */
    @Override
    public void logout(String accessToken) {
        log.info("用户登出，tokenFingerprint={}", tokenFingerprint(accessToken));
        jwtTokenService.blacklistAccessToken(accessToken);
    }

    /**
     * 生成认证响应对象。
     */
    private AuthDtos.AuthResponse toAuthResponse(User user) {
        String accessToken = jwtTokenService.createAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenService.createRefreshToken(user.getId());
        log.info("认证令牌签发完成，userId={} accessTokenLength={} refreshTokenLength={}",
                user.getId(),
                accessToken.length(),
                refreshToken.length());
        return new AuthDtos.AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername(), user.getNickname());
    }

    /**
     * 生成 token 指纹，避免在日志中输出明文令牌。
     */
    private String tokenFingerprint(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return "empty";
        }
        return "len=" + accessToken.length() + ",hash=" + Integer.toHexString(accessToken.hashCode());
    }
}
