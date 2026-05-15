/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.client.mcp.McpToolRegistry;
import com.aitour.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 查询当前可用 MCP 风格工具，供前端和冒烟验证确认工具层是否就绪。
 *
 * @author myoung
 */
@RestController
@RequestMapping("/api/tools")
@Tag(name = "工具接口", description = "MCP 工具状态和可用工具查询")
@SecurityRequirement(name = "bearerAuth")
public class ToolController {
    private final McpToolRegistry toolRegistry;

    /**
     * 注入 MCP 工具注册表。
     */
    public ToolController(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 查询当前后端生效的 MCP 工具模式和工具清单。
     */
    @GetMapping("/status")
    @Operation(summary = "查询工具状态", description = "返回当前 MCP 工具模式和已注册工具名称")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "401", description = "未登录或 token 无效")
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of(
                "mode", toolRegistry.mode(),
                "tools", toolRegistry.names()
        ));
    }
}
