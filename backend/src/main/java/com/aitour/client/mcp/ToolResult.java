/*
 * @author myoung
 */
package com.aitour.client.mcp;

import java.util.Map;

/**
 * MCP 风格工具调用结果，summary 给 AI 和前端快速消费，data 保留结构化结果。
 *
 * @author myoung
 */
public record ToolResult(String toolName, boolean success, String summary, Map<String, Object> data) {
}
