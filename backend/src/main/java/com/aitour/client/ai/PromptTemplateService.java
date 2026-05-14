/*
 * @author myoung
 */
package com.aitour.client.ai;

import org.springframework.stereotype.Service;

/**
 * 统一管理 AI 提示词模板，避免业务服务中散落长提示词。
 *
 * @author myoung
 */
@Service
public class PromptTemplateService {

    public String buildRequirementNormalizePrompt(String userInput) {
        return """
                你是旅行需求解析助手。请从用户输入中提取目的地、天数、预算、人数、偏好和节奏。
                如果信息缺失，使用 null，不要编造。
                用户输入：
                %s
                """.formatted(userInput);
    }

    public String buildPlanSummaryPrompt(String planJson) {
        return """
                你是旅行规划助手。请基于以下结构化行程生成简洁中文说明，突出天气、路线和预算提醒。
                行程 JSON：
                %s
                """.formatted(planJson);
    }
}
