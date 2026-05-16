/*
 * @author myoung
 */
package com.aitour.client.mcp;

/**
 * 旅行工具统一接口，生产流程优先使用外部 MCP Server 适配器。
 *
 * @author myoung
 */
public interface TravelTool {
    String name();

    ToolResult execute(ToolRequest request);
}
