/*
 * @author myoung
 */
package com.aitour.client.mcp;

import com.aitour.client.mcp.local.LocalWeatherTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolRegistryTest {

    @Test
    void shouldExecuteLocalWeatherTool() {
        McpToolRegistry registry = new McpToolRegistry(List.of(new LocalWeatherTool()));

        ToolResult result = registry.execute("weather.query", new ToolRequest(1L, 2L, Map.of("city", "成都")));

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("成都");
    }
}
