/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.common.dto.UserDtos;

/**
 * 用户资料服务接口，定义当前用户查询和旅行画像维护能力。
 *
 * @author myoung
 */
public interface UserProfileService {

    /**
     * 查询当前登录用户基础信息。
     */
    UserDtos.CurrentUserResponse currentUser(Long userId);

    /**
     * 更新当前登录用户的旅行画像。
     */
    UserDtos.ProfileResponse updateProfile(Long userId, UserDtos.UpdateProfileRequest request);
}
