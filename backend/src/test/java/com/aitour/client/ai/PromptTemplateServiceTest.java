/*
 * @author myoung
 */
package com.aitour.client.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateServiceTest {

    @Test
    void shouldBuildRequirementPrompt() {
        PromptTemplateService service = new PromptTemplateService();

        String prompt = service.buildRequirementNormalizePrompt("成都三天美食游");

        assertThat(prompt).contains("旅行需求解析助手");
        assertThat(prompt).contains("成都三天美食游");
    }
}
