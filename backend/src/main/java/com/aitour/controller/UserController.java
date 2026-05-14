/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.common.dto.UserDtos;
import com.aitour.config.security.CurrentUser;
import com.aitour.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "用户接口", description = "当前用户信息和用户画像维护")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * 查询当前登录用户基础信息。
     */
    @GetMapping("/me")
    @Operation(summary = "查询当前用户", description = "根据 Authorization Bearer Token 查询当前登录用户信息。")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或 token 无效")
    public UserDtos.CurrentUserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal CurrentUser currentUser) {
        return userProfileService.currentUser(currentUser.id());
    }

    /**
     * 更新当前登录用户的旅行偏好画像。
     */
    @PutMapping("/me/profile")
    @Operation(summary = "更新用户画像", description = "更新旅行风格、预算偏好、交通方式和偏好 JSON。")
    @ApiResponse(responseCode = "200", description = "更新成功")
    @ApiResponse(responseCode = "401", description = "未登录或 token 无效")
    public UserDtos.ProfileResponse updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody UserDtos.UpdateProfileRequest request
    ) {
        return userProfileService.updateProfile(currentUser.id(), request);
    }
}
