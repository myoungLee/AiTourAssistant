/*
 * @author myoung
 */
package com.aitour.service.impl;

import com.aitour.common.dto.UserDtos;
import com.aitour.common.entity.User;
import com.aitour.common.entity.UserProfile;
import com.aitour.common.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aitour.mapper.UserMapper;
import com.aitour.mapper.UserProfileMapper;
import com.aitour.service.UserProfileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

/**
 * 用户资料应用服务，隔离 Controller 和持久化细节。
 *
 * @author myoung
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {
    private static final Duration CURRENT_USER_CACHE_TTL = Duration.ofMinutes(30);

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 注入用户和用户画像 mapper。
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
     * 查询当前用户基础信息，用户不存在时抛出业务异常。
     */
    @Override
    public UserDtos.CurrentUserResponse currentUser(Long userId) {
        UserDtos.CurrentUserResponse cached = readCurrentUserCache(userId);
        if (cached != null) {
            return cached;
        }

        User user = requireUser(userId);
        UserDtos.CurrentUserResponse response = toCurrentUserResponse(user);
        writeCurrentUserCache(userId, response);
        return response;
    }

    /**
     * 新增或更新当前用户旅行画像。
     */
    @Override
    @Transactional
    public UserDtos.ProfileResponse updateProfile(Long userId, UserDtos.UpdateProfileRequest request) {
        requireUser(userId);

        UserProfile profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId)
        );
        boolean created = profile == null;
        Instant now = Instant.now();
        if (created) {
            profile = new UserProfile();
            profile.setId(newPositiveId());
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
        } else {
            userProfileMapper.updateById(profile);
        }

        clearCurrentUserCache(userId);

        return new UserDtos.ProfileResponse(
                profile.getGender(), profile.getAgeRange(), profile.getTravelStyle(),
                profile.getDefaultBudgetLevel(), profile.getPreferredTransport(), profile.getPreferencesJson()
        );
    }

    /**
     * 按主键查询用户并统一处理不存在场景。
     */
    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    /**
     * 生成正数主键，沿用当前项目的本地 UUID 主键策略。
     */
    private Long newPositiveId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    /**
     * 将用户实体转换为当前用户响应对象，供数据库读取和缓存回填复用。
     */
    private UserDtos.CurrentUserResponse toCurrentUserResponse(User user) {
        return new UserDtos.CurrentUserResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl(), user.getPhone(), user.getEmail()
        );
    }

    /**
     * 尝试从 Redis 读取当前用户缓存，解析失败时删除损坏缓存并回退数据库。
     */
    private UserDtos.CurrentUserResponse readCurrentUserCache(Long userId) {
        String cached = stringRedisTemplate.opsForValue().get(currentUserCacheKey(userId));
        if (cached == null || cached.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, UserDtos.CurrentUserResponse.class);
        } catch (JsonProcessingException ex) {
            clearCurrentUserCache(userId);
            return null;
        }
    }

    /**
     * 将当前用户基础信息写入 Redis，减少重复查询数据库。
     */
    private void writeCurrentUserCache(Long userId, UserDtos.CurrentUserResponse response) {
        try {
            stringRedisTemplate.opsForValue().set(
                    currentUserCacheKey(userId),
                    objectMapper.writeValueAsString(response),
                    CURRENT_USER_CACHE_TTL
            );
        } catch (JsonProcessingException ignored) {
            clearCurrentUserCache(userId);
        }
    }

    /**
     * 更新资料后删除当前用户缓存，避免后续读取到过期数据。
     */
    private void clearCurrentUserCache(Long userId) {
        stringRedisTemplate.delete(currentUserCacheKey(userId));
    }

    /**
     * 统一生成当前用户缓存键，避免缓存键散落在多个方法中。
     */
    private String currentUserCacheKey(Long userId) {
        return "user:current:" + userId;
    }
}
