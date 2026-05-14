/*
 * @author myoung
 */
package com.aitour.client.mcp;

import java.util.Map;

/**
 * MCP 风格工具调用请求，保留 userId/planId 便于后续写调用日志。
 *
 * @author myoung
 */
public record ToolRequest(Long userId, Long planId, Map<String, Object> arguments) {
}
