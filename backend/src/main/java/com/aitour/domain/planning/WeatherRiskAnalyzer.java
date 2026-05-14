/*
 * @author myoung
 */
package com.aitour.domain.planning;

import org.springframework.stereotype.Component;

/**
 * 天气风险分析规则组件，将天气工具摘要转换为行程日程里的可读提醒。
 *
 * @author myoung
 */
@Component
public class WeatherRiskAnalyzer {

    /**
     * 根据天气摘要生成风险提示，第一版只做轻量规则判断。
     */
    public String summarize(String weatherSummary) {
        if (weatherSummary == null || weatherSummary.isBlank()) {
            return "天气信息暂缺，建议出行前再次确认。";
        }
        if (weatherSummary.contains("雨")) {
            return weatherSummary + " 建议准备雨具，并优先安排室内景点。";
        }
        return weatherSummary + " 整体适合户外游览。";
    }
}
