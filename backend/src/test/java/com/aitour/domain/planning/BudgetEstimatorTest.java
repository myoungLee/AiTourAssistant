/*
 * @author myoung
 */
package com.aitour.domain.planning;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证预算评估规则会识别预算不足场景，并给出可落到服务端结果中的明确提示。
 *
 * @author myoung
 */
class BudgetEstimatorTest {

    /**
     * 当用户预算明显低于推荐最低预算时，应标记预算不足并返回确定性的提示文案。
     */
    @Test
    void shouldMarkBudgetAsInsufficientWhenUserBudgetIsTooLow() {
        BudgetEstimator estimator = new BudgetEstimator();

        BudgetEstimator.BudgetDraft result = estimator.estimate(3, 2, BigDecimal.valueOf(1000));

        assertThat(result.insufficient()).isTrue();
        assertThat(result.recommendedTotal()).isEqualByComparingTo("3600.00");
        assertThat(result.warningMessage()).contains("预算提醒");
        assertThat(result.warningMessage()).contains("3600.00");
    }
}
