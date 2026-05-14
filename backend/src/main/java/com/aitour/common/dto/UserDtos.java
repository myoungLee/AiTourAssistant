/*
 * @author myoung
 */
package com.aitour.common.dto;

import jakarta.validation.constraints.Size;

/**
 * 用户和资料接口请求响应 DTO。
 *
 * @author myoung
 */
public final class UserDtos {
    private UserDtos() {
    }

    public record CurrentUserResponse(
            Long id,
            String username,
            String nickname,
            String avatarUrl,
            String phone,
            String email
    ) {
    }

    public record ProfileResponse(
            String gender,
            String ageRange,
            String travelStyle,
            String defaultBudgetLevel,
            String preferredTransport,
            String preferencesJson
    ) {
    }

    public record UpdateProfileRequest(
            @Size(max = 32) String gender,
            @Size(max = 32) String ageRange,
            @Size(max = 64) String travelStyle,
            @Size(max = 32) String defaultBudgetLevel,
            @Size(max = 64) String preferredTransport,
            String preferencesJson
    ) {
    }
}
