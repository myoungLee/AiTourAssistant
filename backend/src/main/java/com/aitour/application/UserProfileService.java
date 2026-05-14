/*
 * @author myoung
 */
package com.aitour.application;

import com.aitour.api.dto.UserDtos;
import com.aitour.domain.User;
import com.aitour.domain.UserProfile;
import com.aitour.infrastructure.exception.ApiException;
import com.aitour.infrastructure.persistence.UserMapper;
import com.aitour.infrastructure.persistence.UserProfileMapper;
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
public class UserProfileService {
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    public UserProfileService(UserMapper userMapper, UserProfileMapper userProfileMapper) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
    }

    public UserDtos.CurrentUserResponse currentUser(Long userId) {
        User user = requireUser(userId);
        return new UserDtos.CurrentUserResponse(
                user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl(), user.getPhone(), user.getEmail()
        );
    }

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

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private Long newPositiveId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
