/*
 * @author myoung
 */
package com.aitour.client.mcp.local;

import com.aitour.client.mcp.ToolRequest;
import com.aitour.client.mcp.ToolResult;
import com.aitour.client.mcp.TravelTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 本地景点推荐占位工具，后续可替换为真实地图或外部 MCP 查询。
 *
 * @author myoung
 */
@Component
public class LocalPlaceSearchTool implements TravelTool {
    @Override
    public String name() {
        return "place.search";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        String city = String.valueOf(request.arguments().getOrDefault("city", "目的地"));
        return new ToolResult(name(), true, city + "推荐 3 个适合首次规划的景点。", Map.of(
                "city", city,
                "places", List.of(
                        Map.of("name", city + "城市地标", "type", "landmark", "durationMinutes", 120),
                        Map.of("name", city + "特色街区", "type", "food", "durationMinutes", 150),
                        Map.of("name", city + "博物馆", "type", "culture", "durationMinutes", 90)
                )
        ));
    }
}
