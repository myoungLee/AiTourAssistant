/*
 * @author myoung
 */
package com.aitour.client.mcp;

import com.aitour.client.mcp.external.ExternalMcpToolAdapter;
import com.aitour.client.mcp.local.LocalWeatherTool;
import com.aitour.common.exception.ApiException;
import com.aitour.config.mcp.McpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 MCP 工具注册表可以根据配置在本地和外部模式之间切换。
 *
 * @author myoung
 */
class McpToolRegistryModeTest {

    /**
     * local 模式下，应始终走内置工具实现。
     */
    @Test
    void shouldUseLocalToolWhenModeIsLocal() {
        McpToolRegistry registry = new McpToolRegistry(
                List.of(new LocalWeatherTool()),
                List.of(new ExternalMcpToolAdapter(newRestClient(), new McpProperties("local", new McpProperties.External("", 10)), "weather.query")),
                new McpProperties("local", new McpProperties.External("", 10))
        );

        ToolResult result = registry.execute("weather.query", new ToolRequest(1L, 2L, Map.of("city", "成都")));

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).contains("成都");
    }

    /**
     * external 模式且配置 baseUrl 后，应优先走外部适配器。
     */
    @Test
    void shouldUseExternalToolWhenModeIsExternal() {
        McpToolRegistry registry = new McpToolRegistry(
                List.of(new LocalWeatherTool()),
                List.of(new StubExternalTool("weather.query")),
                new McpProperties("external", new McpProperties.External("http://localhost:8089", 10))
        );

        ToolResult result = registry.execute("weather.query", new ToolRequest(1L, 2L, Map.of("city", "成都")));

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).isEqualTo("来自外部 MCP Server");
    }

    /**
     * external 模式未配置 baseUrl 或外部调用失败时，应抛出可追踪的业务异常。
     */
    @Test
    void shouldThrowTraceableErrorWhenExternalCallFails() {
        McpToolRegistry registry = new McpToolRegistry(
                List.of(new LocalWeatherTool()),
                List.of(new ExternalMcpToolAdapter(newRestClient(), new McpProperties("external", new McpProperties.External("http://localhost:65530", 1)), "weather.query")),
                new McpProperties("external", new McpProperties.External("http://localhost:65530", 1))
        );

        assertThatThrownBy(() -> registry.execute("weather.query", new ToolRequest(1L, 2L, Map.of("city", "成都"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("外部 MCP Server");
    }

    /**
     * 创建用于构造外部适配器的 RestClient。
     */
    private RestClient newRestClient() {
        return RestClient.builder().build();
    }

    /**
     * 轻量外部工具替身，用于验证 external 模式下的优先分发逻辑。
     */
    private static final class StubExternalTool implements TravelTool {
        private final String name;

        private StubExternalTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolResult execute(ToolRequest request) {
            return new ToolResult(name, true, "来自外部 MCP Server", Map.of("source", "external"));
        }
    }
}
