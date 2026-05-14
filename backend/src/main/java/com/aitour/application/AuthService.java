/*
 * @author myoung
 */
package com.aitour.application;

import com.aitour.api.dto.AuthDtos;
import com.aitour.domain.User;
import com.aitour.domain.UserStatus;
import com.aitour.infrastructure.exception.ApiException;
import com.aitour.infrastructure.persistence.UserMapper;
import com.aitour.infrastructure.security.JwtTokenService;
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
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

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

    private AuthDtos.AuthResponse toAuthResponse(User user) {
        String accessToken = jwtTokenService.createAccessToken(user.getId(), user.getUsername());
        String refreshToken = UUID.randomUUID().toString();
        return new AuthDtos.AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername(), user.getNickname());
    }

    private Long newPositiveId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
