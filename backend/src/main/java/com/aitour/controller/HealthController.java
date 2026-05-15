/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.common.Result;
import com.aitour.common.dto.CommonDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 提供基础健康检查接口，用于本地联调和部署探活。
 *
 * @author myoung
 */
@RestController
@RequestMapping("/api")
@Tag(name = "健康检查", description = "服务可用性检查接口")
public class HealthController {

    /**
     * 返回后端服务当前健康状态。
     */
    @GetMapping("/health")
    @SecurityRequirements
    @Operation(summary = "健康检查", description = "无需登录，返回服务状态、服务名和当前时间。")
    @ApiResponse(responseCode = "200", description = "服务可用")
    public Result<CommonDtos.HealthResponse> health() {
        return Result.success(new CommonDtos.HealthResponse(
                "UP",
                "aitour-backend",
                Instant.now().toString()
        ));
    }
}
