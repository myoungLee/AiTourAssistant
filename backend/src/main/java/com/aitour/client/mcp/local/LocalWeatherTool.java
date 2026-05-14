/*
 * @author myoung
 */
package com.aitour.client.mcp.local;

import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.client.mcp.TravelTool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 本地天气占位工具，保证无外部 MCP Server 时系统也能跑通。
 *
 * @author myoung
 */
@Component
public class LocalWeatherTool implements TravelTool {
    @Override
    public String name() {
        return "weather.query";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        String city = String.valueOf(request.arguments().getOrDefault("city", "目的地"));
        return new ToolResult(name(), true, city + "未来天气整体适合出行，午后注意防晒或降雨。", Map.of(
                "city", city,
                "condition", "多云",
                "temperature", "22-29"
        ));
    }
}
