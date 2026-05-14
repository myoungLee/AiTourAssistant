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
 * 本地路线估算占位工具，提供基础交通方式和耗时估算。
 *
 * @author myoung
 */
@Component
public class LocalRouteTool implements TravelTool {
    @Override
    public String name() {
        return "route.plan";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        String from = String.valueOf(request.arguments().getOrDefault("from", "起点"));
        String to = String.valueOf(request.arguments().getOrDefault("to", "终点"));
        return new ToolResult(name(), true, from + " 到 " + to + " 建议公共交通优先，约 35 分钟。", Map.of(
                "from", from,
                "to", to,
                "transport", "公共交通",
                "durationMinutes", 35
        ));
    }
}
