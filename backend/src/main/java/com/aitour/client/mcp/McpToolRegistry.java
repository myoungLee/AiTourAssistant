/*
 * @author myoung
 */
package com.aitour.client.mcp;

import com.aitour.client.mcp.external.ExternalMcpToolAdapter;
import com.aitour.common.exception.ApiException;
import com.aitour.config.mcp.McpProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册表，按当前 MCP 模式分发工具调用，业务层不需要感知本地或外部实现差异。
 *
 * @author myoung
 */
@Component
public class McpToolRegistry {
    private final Map<String, TravelTool> localTools;
    private final Map<String, TravelTool> externalTools;
    private final McpProperties properties;

    /**
     * Spring 场景下从全部工具 Bean 中拆分出本地和外部两套实现。
     */
    @Autowired
    public McpToolRegistry(List<TravelTool> tools, McpProperties properties) {
        this(
                tools.stream().filter(tool -> !(tool instanceof ExternalMcpToolAdapter)).toList(),
                tools.stream().filter(ExternalMcpToolAdapter.class::isInstance).toList(),
                properties
        );
    }

    /**
     * 测试和显式装配场景下直接注入本地/外部工具集合，便于验证模式切换逻辑。
     */
    public McpToolRegistry(List<TravelTool> localTools, List<? extends TravelTool> externalTools, McpProperties properties) {
        this.localTools = localTools.stream().collect(Collectors.toMap(TravelTool::name, Function.identity()));
        this.externalTools = externalTools.stream().collect(Collectors.toMap(TravelTool::name, Function.identity()));
        this.properties = properties;
    }

    /**
     * 按当前 MCP 模式分发工具调用，本地模式走内置实现，外部模式走真实 MCP Server。
     */
    public ToolResult execute(String name, ToolRequest request) {
        TravelTool tool = activeTools().get(name);
        if (tool == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "工具不存在: " + name);
        }
        return tool.execute(request);
    }

    /**
     * 返回当前模式下可见的工具名称；外部模式优先返回远端真实工具列表。
     */
    public List<String> names() {
        if (properties.isExternalMode()) {
            TravelTool externalTool = externalTools.values().stream().findFirst().orElse(null);
            if (externalTool instanceof ExternalMcpToolAdapter adapter) {
                return adapter.listAvailableToolNames();
            }
        }
        return activeTools().keySet().stream().sorted().toList();
    }

    /**
     * 暴露当前生效的 MCP 模式，供状态接口和日志输出使用。
     */
    public String mode() {
        return properties.mode();
    }

    /**
     * 根据配置选择当前生效的工具集合。
     */
    private Map<String, TravelTool> activeTools() {
        return properties.isExternalMode() ? externalTools : localTools;
    }
}
