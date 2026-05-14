/*
 * @author myoung
 */
package com.aitour.client.mcp;

import com.aitour.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册表，按工具名分发调用，业务层不用感知工具实现来源。
 *
 * @author myoung
 */
@Component
public class McpToolRegistry {
    private final Map<String, TravelTool> tools;

    public McpToolRegistry(List<TravelTool> tools) {
        this.tools = tools.stream().collect(Collectors.toMap(TravelTool::name, Function.identity()));
    }

    public ToolResult execute(String name, ToolRequest request) {
        TravelTool tool = tools.get(name);
        if (tool == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "工具不存在: " + name);
        }
        return tool.execute(request);
    }

    public List<String> names() {
        return tools.keySet().stream().sorted().toList();
    }
}
