/*
 * @author myoung
 */
package com.aitour.client.mcp;

import com.aitour.config.mcp.McpProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolRegistryTest {

    /**
     * external 模式下应执行显式注入的外部工具测试替身。
     */
    @Test
    void shouldExecuteExternalWeatherTool() {
        McpToolRegistry registry = new McpToolRegistry(
                List.of(),
                List.of(new StubTravelTool("weather.query")),
                new McpProperties("external", new McpProperties.External("http://localhost:8089", 10))
        );

        ToolResult result = registry.execute("weather.query", new ToolRequest(1L, 2L, Map.of("city", "成都")));

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("成都");
    }

    /**
     * 轻量工具替身只用于测试注册表路由，不作为生产运行时替代实现。
     */
    private static final class StubTravelTool implements TravelTool {
        private final String name;

        private StubTravelTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolResult execute(ToolRequest request) {
            return new ToolResult(name, true, "成都外部天气结果", Map.of("source", "test"));
        }
    }
}
