/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.client.mcp.McpToolRegistry;
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
public class ToolController {
    private final McpToolRegistry toolRegistry;

    public ToolController(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "mode", "local",
                "tools", toolRegistry.names()
        );
    }
}
