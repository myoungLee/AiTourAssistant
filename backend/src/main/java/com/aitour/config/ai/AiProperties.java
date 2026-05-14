/*
 * @author myoung
 */
package com.aitour.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI-compatible 供应商配置，通过环境变量切换真实供应商。
 *
 * @author myoung
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds
) {
}
