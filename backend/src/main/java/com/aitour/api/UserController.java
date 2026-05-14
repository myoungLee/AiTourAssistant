/*
 * @author myoung
 */
package com.aitour.api;

import com.aitour.api.dto.UserDtos;
import com.aitour.application.UserProfileService;
import com.aitour.infrastructure.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户和旅行偏好资料接口。
 *
 * @author myoung
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public UserDtos.CurrentUserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return userProfileService.currentUser(currentUser.id());
    }

    @PutMapping("/me/profile")
    public UserDtos.ProfileResponse updateProfile(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody UserDtos.UpdateProfileRequest request
    ) {
        return userProfileService.updateProfile(currentUser.id(), request);
    }
}
