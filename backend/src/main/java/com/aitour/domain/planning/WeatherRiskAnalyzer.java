/*
 * @author myoung
 */
package com.aitour.domain.planning;

import com.aitour.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 天气风险分析规则组件，将天气工具摘要转换为行程日程里的可读提醒。
 *
 * @author myoung
 */
@Component
public class WeatherRiskAnalyzer {

    /**
     * 根据真实天气摘要生成风险提示，摘要缺失时直接失败。
     */
    public String summarize(String weatherSummary) {
        if (weatherSummary == null || weatherSummary.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MCP_INVALID_RESPONSE", "天气工具返回摘要为空，无法生成真实天气提醒");
        }
        if (weatherSummary.contains("雨")) {
            return weatherSummary + " 建议准备雨具，并优先安排室内景点。";
        }
        return weatherSummary + " 整体适合户外游览。";
    }
}
