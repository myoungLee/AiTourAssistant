/*
 * @author myoung
 */
package com.aitour.client.mcp;

import com.aitour.common.exception.ApiException;
import com.aitour.config.mcp.McpProperties;
import org.junit.jupiter.api.Test;

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
     * local 模式下，如果工具仍是占位实现，应直接暴露禁用错误。
     */
    @Test
    void shouldExposeDisabledErrorWhenModeIsLocal() {
        McpToolRegistry registry = new McpToolRegistry(
                List.of(new DisabledLocalTool("weather.query")),
                List.of(new StubExternalTool("weather.query")),
                new McpProperties("local", new McpProperties.External("", 10))
        );

        assertThatThrownBy(() -> registry.execute("weather.query", new ToolRequest(1L, 2L, Map.of("city", "成都"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("本地占位工具已禁用");
    }

    /**
     * external 模式且配置 baseUrl 后，应优先走外部适配器。
     */
    @Test
    void shouldUseExternalToolWhenModeIsExternal() {
        McpToolRegistry registry = new McpToolRegistry(
                List.of(new DisabledLocalTool("weather.query")),
                List.of(new StubExternalTool("weather.query")),
                new McpProperties("external", new McpProperties.External("http://localhost:8089", 10))
        );

        ToolResult result = registry.execute("weather.query", new ToolRequest(1L, 2L, Map.of("city", "成都")));

        assertThat(result.success()).isTrue();
        assertThat(result.summary()).isEqualTo("来自外部 MCP Server");
    }

    /**
     * external 模式缺少外部工具时，应抛出可追踪的工具不存在异常。
     */
    @Test
    void shouldThrowTraceableErrorWhenExternalToolIsMissing() {
        McpToolRegistry registry = new McpToolRegistry(
                List.of(new DisabledLocalTool("weather.query")),
                List.of(),
                new McpProperties("external", new McpProperties.External("http://localhost:8089", 1))
        );

        assertThatThrownBy(() -> registry.execute("weather.query", new ToolRequest(1L, 2L, Map.of("city", "成都"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("工具不存在");
    }

    /**
     * 未知 mcp.mode 应在属性构造阶段失败，避免静默回落到 local。
     */
    @Test
    void shouldRejectUnknownMcpMode() {
        assertThatThrownBy(() -> new McpProperties("demo", new McpProperties.External("", 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mcp.mode");
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

    /**
     * 本地占位工具测试替身，用于验证 local 模式不会伪造成功结果。
     */
    private static final class DisabledLocalTool implements TravelTool {
        private final String name;

        private DisabledLocalTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolResult execute(ToolRequest request) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "LOCAL_MCP_TOOL_DISABLED", "本地占位工具已禁用");
        }
    }
}
