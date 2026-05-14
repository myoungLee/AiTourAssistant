/*
 * @author myoung
 */
package com.aitour.client.mcp.external;

import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.client.mcp.TravelTool;

import java.util.Map;

/**
 * 外部 MCP Server 适配预留类；配置接入后再注册为 Spring Bean。
 *
 * @author myoung
 */
public class ExternalMcpToolAdapter implements TravelTool {
    private final String name;

    public ExternalMcpToolAdapter(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        return new ToolResult(name, false, "外部 MCP Server 尚未配置。", Map.of());
    }
}
