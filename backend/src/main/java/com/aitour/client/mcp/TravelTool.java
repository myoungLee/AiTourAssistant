/*
 * @author myoung
 */
package com.aitour.client.mcp;

/**
 * 旅行工具统一接口，本地工具和外部 MCP Server 适配器都实现该接口。
 *
 * @author myoung
 */
public interface TravelTool {
    String name();

    ToolResult execute(ToolRequest request);
}
