/*
 * @author myoung
 */
package com.aitour.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 通用接口响应 DTO，承载健康检查和工具状态等轻量响应模型。
 *
 * @author myoung
 */
public final class CommonDtos {
    private CommonDtos() {
    }

    /**
     * 健康检查接口响应。
     */
    @Schema(description = "健康检查响应")
    public record HealthResponse(
            @Schema(description = "服务状态", example = "UP")
            String status,
            @Schema(description = "服务名称", example = "aitour-backend")
            String service,
            @Schema(description = "服务时间戳", example = "2026-05-15T18:00:00Z")
            String timestamp
    ) {
    }

    /**
     * MCP 工具状态接口响应。
     */
    @Schema(description = "工具状态响应")
    public record ToolStatusResponse(
            @Schema(description = "当前 MCP 工具模式", example = "local")
            String mode,
            @Schema(description = "当前可用工具名称列表")
            List<String> tools
    ) {
    }
}
