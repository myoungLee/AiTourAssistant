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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户资料应用服务，隔离 Controller 和持久化细节。
 *
 * @author myoung
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    /**
     * 注入用户和用户画像 mapper。
     */
    public UserProfileServiceImpl(UserMapper userMapper, UserProfileMapper userProfileMapper) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
    }

    /**
     * 查询当前用户基础信息，用户不存在时抛出业务异常。
     */
    @Override
    public UserDtos.CurrentUserResponse currentUser(Long userId) {
        User user = requireUser(userId);
        return new UserDtos.CurrentUserResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl(), user.getPhone(), user.getEmail()
        );
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
}
