/*
 * @author myoung
 */
package com.aitour.domain.planning;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 预算拆分规则组件，按住宿、餐饮、交通、门票和其他费用生成可持久化预算草案。
 *
 * @author myoung
 */
@Component
public class BudgetEstimator {
    private static final BigDecimal DAILY_RECOMMENDED_BUDGET_PER_PERSON = BigDecimal.valueOf(600);

    /**
     * 按用户预算和推荐预算共同估算费用，并标记预算是否不足。
     */
    public BudgetDraft estimate(int days, int peopleCount, BigDecimal userBudget) {
        BigDecimal recommendedTotal = recommendedTotal(days, peopleCount);
        BigDecimal total = userBudget == null ? recommendedTotal : userBudget.setScale(2, RoundingMode.HALF_UP);
        boolean insufficient = userBudget != null && total.compareTo(recommendedTotal) < 0;
        String warningMessage = insufficient
                ? "预算提醒：当前预算低于建议预算 " + recommendedTotal + " 元，建议放慢节奏并优先低成本安排。"
                : null;
        BigDecimal hotel = amount(total, "0.35");
        BigDecimal food = amount(total, "0.25");
        BigDecimal transport = amount(total, "0.15");
        BigDecimal ticket = amount(total, "0.15");
        BigDecimal other = total.subtract(hotel).subtract(food).subtract(transport).subtract(ticket).setScale(2, RoundingMode.HALF_UP);
        return new BudgetDraft(hotel, food, transport, ticket, other, total, recommendedTotal, insufficient, warningMessage);
    }

    /**
     * 计算建议总预算，作为预算提示和节奏控制的统一基线。
     */
    private BigDecimal recommendedTotal(int days, int peopleCount) {
        return DAILY_RECOMMENDED_BUDGET_PER_PERSON
                .multiply(BigDecimal.valueOf(days))
                .multiply(BigDecimal.valueOf(peopleCount))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 使用字符串构造 BigDecimal，避免二进制浮点误差影响金额。
     */
    private BigDecimal amount(BigDecimal base, String ratio) {
        return base.multiply(new BigDecimal(ratio)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 预算草案，字段与预算明细表保持一一对应，并补充预算风险状态。
     *
     * @author myoung
     */
    public record BudgetDraft(
            BigDecimal hotel,
            BigDecimal food,
            BigDecimal transport,
            BigDecimal ticket,
            BigDecimal other,
            BigDecimal total,
            BigDecimal recommendedTotal,
            boolean insufficient,
            String warningMessage
    ) {
    }
}
