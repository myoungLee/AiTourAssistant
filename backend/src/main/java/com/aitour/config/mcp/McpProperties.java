/*
 * @author myoung
 */
package com.aitour.config.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * MCP 工具模式配置，统一描述本地内置工具和外部 MCP Server 的切换方式。
 *
 * @author myoung
 */
@ConfigurationProperties(prefix = "mcp")
public record McpProperties(
        @DefaultValue("local") String mode,
        @DefaultValue External external
) {

    /**
     * 归一化模式和值对象，避免业务层重复处理空值和大小写差异。
     */
    public McpProperties {
        mode = normalizeMode(mode);
        external = external == null ? new External("", 10) : external;
    }

    /**
     * 判断当前是否启用外部 MCP Server 模式。
     */
    public boolean isExternalMode() {
        return "external".equals(mode);
    }

    /**
     * 归一化 MCP 模式，未知值统一回落到 local，避免错误配置直接放大影响面。
     */
    private static String normalizeMode(String rawMode) {
        if (!StringUtils.hasText(rawMode)) {
            return "local";
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        return "external".equals(normalized) ? "external" : "local";
    }

    /**
     * 外部 MCP Server 连接配置。
     *
     * @author myoung
     */
    public record External(
            String baseUrl,
            @DefaultValue("10") Integer timeoutSeconds
    ) {

        /**
         * 规整 baseUrl 和超时时间，避免后续调用阶段到处做空值判断。
         */
        public External {
            baseUrl = baseUrl == null ? "" : baseUrl.trim();
            timeoutSeconds = timeoutSeconds == null || timeoutSeconds <= 0 ? 10 : timeoutSeconds;
        }

        /**
         * 判断是否已经配置可用的外部 MCP Server 地址。
         */
        public boolean isConfigured() {
            return StringUtils.hasText(baseUrl);
        }

        /**
         * 将秒级超时统一转换为 Duration，方便配置类和客户端共用。
         */
        public Duration timeout() {
            return Duration.ofSeconds(timeoutSeconds);
        }
    }
}
