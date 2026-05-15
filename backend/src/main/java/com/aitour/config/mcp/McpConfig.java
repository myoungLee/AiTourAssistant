/*
 * @author myoung
 */
package com.aitour.config.mcp;

import com.aitour.client.mcp.external.ExternalMcpToolAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * MCP 客户端配置，负责注册属性对象、HTTP 客户端和外部工具适配器。
 *
 * @author myoung
 */
@Configuration
@EnableConfigurationProperties(McpProperties.class)
public class McpConfig {

    /**
     * 为外部 MCP 调用提供带超时控制的 RestClient，避免请求长期阻塞规划流程。
     */
    @Bean
    RestClient mcpRestClient(RestClient.Builder builder, McpProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(properties.external().timeout().toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return builder.requestFactory(requestFactory).build();
    }

    /**
     * 注册天气查询外部工具适配器。
     */
    @Bean
    ExternalMcpToolAdapter externalWeatherTool(RestClient mcpRestClient, McpProperties properties) {
        return new ExternalMcpToolAdapter(mcpRestClient, properties, "weather.query");
    }

    /**
     * 注册景点搜索外部工具适配器。
     */
    @Bean
    ExternalMcpToolAdapter externalPlaceSearchTool(RestClient mcpRestClient, McpProperties properties) {
        return new ExternalMcpToolAdapter(mcpRestClient, properties, "place.search");
    }

    /**
     * 注册路线规划外部工具适配器。
     */
    @Bean
    ExternalMcpToolAdapter externalRouteTool(RestClient mcpRestClient, McpProperties properties) {
        return new ExternalMcpToolAdapter(mcpRestClient, properties, "route.plan");
    }

    /**
     * 注册预算估算外部工具适配器。
     */
    @Bean
    ExternalMcpToolAdapter externalBudgetTool(RestClient mcpRestClient, McpProperties properties) {
        return new ExternalMcpToolAdapter(mcpRestClient, properties, "budget.estimate");
    }
}
