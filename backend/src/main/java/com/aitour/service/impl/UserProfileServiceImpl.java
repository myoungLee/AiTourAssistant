/*
 * @author myoung
 */
package com.aitour.service.impl;

import com.aitour.common.dto.UserDtos;
import com.aitour.common.entity.User;
import com.aitour.common.entity.UserProfile;
import com.aitour.common.exception.ApiException;
import com.aitour.mapper.UserMapper;
import com.aitour.mapper.UserProfileMapper;
import com.aitour.service.UserProfileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 用户资料应用服务，负责当前用户信息读取和画像维护。
 *
 * @author myoung
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {
    private static final Logger log = LoggerFactory.getLogger(UserProfileServiceImpl.class);
    private static final Duration CURRENT_USER_CACHE_TTL = Duration.ofMinutes(30);

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 注入用户、用户画像和缓存组件。
     */
    public UserProfileServiceImpl(
            UserMapper userMapper,
            UserProfileMapper userProfileMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper
    ) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前用户基础信息，优先命中 Redis 缓存。
     */
    @Override
    public UserDtos.CurrentUserResponse currentUser(Long userId) {
        log.info("查询当前用户开始，userId={}", userId);
        UserDtos.CurrentUserResponse cached = readCurrentUserCache(userId);
        if (cached != null) {
            log.info("查询当前用户命中缓存，userId={}", userId);
            return cached;
        }

        log.info("查询当前用户缓存未命中，userId={}", userId);
        User user = requireUser(userId);
        UserDtos.CurrentUserResponse response = toCurrentUserResponse(user);
        writeCurrentUserCache(userId, response);
        log.info("查询当前用户完成并回填缓存，userId={}", userId);
        return response;
    }

    /**
     * 新增或更新当前用户画像。
     */
    @Override
    @Transactional
    public UserDtos.ProfileResponse updateProfile(Long userId, UserDtos.UpdateProfileRequest request) {
        log.info("更新用户画像开始，userId={} hasGender={} hasAgeRange={} hasTravelStyle={} hasDefaultBudgetLevel={} hasPreferredTransport={} preferencesJsonLength={}",
                userId,
                request.gender() != null,
                request.ageRange() != null,
                request.travelStyle() != null,
                request.defaultBudgetLevel() != null,
                request.preferredTransport() != null,
                request.preferencesJson() == null ? 0 : request.preferencesJson().length());
        requireUser(userId);

        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId)
        );
        boolean created = profile == null;
        Instant now = Instant.now();
        if (created) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setCreatedAt(now);
        }

        profile.setGender(request.gender());
        profile.setAgeRange(request.ageRange());
        profile.setTravelStyle(request.travelStyle());
        profile.setDefaultBudgetLevel(request.defaultBudgetLevel());
        profile.setPreferredTransport(request.preferredTransport());
        profile.setPreferencesJson(request.preferencesJson());
        profile.setUpdatedAt(now);

        if (created) {
            userProfileMapper.insert(profile);
            log.info("用户画像创建完成，profileId={} userId={}", profile.getId(), userId);
        } else {
            userProfileMapper.updateById(profile);
            log.info("用户画像更新完成，profileId={} userId={}", profile.getId(), userId);
        }

        clearCurrentUserCache(userId);
        log.info("用户画像更新后已清理当前用户缓存，userId={}", userId);

        return new UserDtos.ProfileResponse(
                profile.getGender(),
                profile.getAgeRange(),
                profile.getTravelStyle(),
                profile.getDefaultBudgetLevel(),
                profile.getPreferredTransport(),
                profile.getPreferencesJson()
        );
    }

    /**
     * 读取用户，不存在时抛出业务异常。
     */
    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("查询用户失败，userId={} reason=USER_NOT_FOUND", userId);
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    /**
     * 将用户实体转换为当前用户响应对象。
     */
    private UserDtos.CurrentUserResponse toCurrentUserResponse(User user) {
        return new UserDtos.CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getEmail()
        );
    }

    /**
     * 从 Redis 读取当前用户缓存，损坏时清除并回退数据库。
     */
    private UserDtos.CurrentUserResponse readCurrentUserCache(Long userId) {
        String cached = stringRedisTemplate.opsForValue().get(currentUserCacheKey(userId));
        if (cached == null || cached.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, UserDtos.CurrentUserResponse.class);
        } catch (JsonProcessingException ex) {
            log.warn("当前用户缓存反序列化失败，userId={}，将清理缓存并回退数据库", userId, ex);
            clearCurrentUserCache(userId);
            return null;
        }
    }

    /**
     * 将当前用户基础信息写入 Redis。
     */
    private void writeCurrentUserCache(Long userId, UserDtos.CurrentUserResponse response) {
        try {
            stringRedisTemplate.opsForValue().set(
                    currentUserCacheKey(userId),
                    objectMapper.writeValueAsString(response),
                    CURRENT_USER_CACHE_TTL
            );
            log.info("当前用户缓存写入成功，userId={} ttlMinutes={}", userId, CURRENT_USER_CACHE_TTL.toMinutes());
        } catch (JsonProcessingException ex) {
            log.warn("当前用户缓存写入失败，userId={}，将清理缓存键", userId, ex);
            clearCurrentUserCache(userId);
        }
    }

    /**
     * 删除当前用户缓存，避免读取到过期数据。
     */
    private void clearCurrentUserCache(Long userId) {
        stringRedisTemplate.delete(currentUserCacheKey(userId));
    }

    /**
     * 统一生成当前用户缓存键。
     */
    private String currentUserCacheKey(Long userId) {
        return "user:current:" + userId;
    }
}
