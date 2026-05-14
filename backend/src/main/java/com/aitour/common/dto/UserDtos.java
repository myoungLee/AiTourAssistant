/*
 * @author myoung
 */
package com.aitour.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 用户和资料接口请求响应 DTO。
 *
 * @author myoung
 */
public final class UserDtos {
    private UserDtos() {
    }

    @Schema(description = "当前用户基础信息")
    public record CurrentUserResponse(
            @Schema(description = "用户 ID", example = "10001")
            Long id,
            @Schema(description = "用户名", example = "alice")
            String username,
            @Schema(description = "昵称", example = "Alice")
            String nickname,
            @Schema(description = "头像地址")
            String avatarUrl,
            @Schema(description = "手机号")
            String phone,
            @Schema(description = "邮箱")
            String email
    ) {
    }

    @Schema(description = "用户旅行画像响应")
    public record ProfileResponse(
            @Schema(description = "性别")
            String gender,
            @Schema(description = "年龄段")
            String ageRange,
            @Schema(description = "旅行风格", example = "美食休闲")
            String travelStyle,
            @Schema(description = "默认预算等级", example = "中等")
            String defaultBudgetLevel,
            @Schema(description = "偏好交通方式", example = "公共交通")
            String preferredTransport,
            @Schema(description = "偏好 JSON")
            String preferencesJson
    ) {
    }

    @Schema(description = "更新用户旅行画像请求")
    public record UpdateProfileRequest(
            @Schema(description = "性别") @Size(max = 32) String gender,
            @Schema(description = "年龄段") @Size(max = 32) String ageRange,
            @Schema(description = "旅行风格", example = "美食休闲") @Size(max = 64) String travelStyle,
            @Schema(description = "默认预算等级", example = "中等") @Size(max = 32) String defaultBudgetLevel,
            @Schema(description = "偏好交通方式", example = "公共交通") @Size(max = 64) String preferredTransport,
            @Schema(description = "偏好 JSON", example = "{\"food\":[\"火锅\"],\"pace\":\"轻松\"}")
            String preferencesJson
    ) {
    }
}
