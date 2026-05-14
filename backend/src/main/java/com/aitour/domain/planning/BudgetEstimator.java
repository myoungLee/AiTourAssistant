/*
 * @author myoung
 */
package com.aitour.domain.planning;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 预算拆分规则组件，按照住宿、餐饮、交通、门票和其他费用生成可持久化预算草案。
 *
 * @author myoung
 */
@Component
public class BudgetEstimator {

    /**
     * 按用户预算或默认人均日预算估算费用，并返回总额及五类成本。
     */
    public BudgetDraft estimate(int days, int peopleCount, BigDecimal userBudget) {
        BigDecimal base = userBudget == null
                ? BigDecimal.valueOf((long) days * peopleCount * 600L)
                : userBudget;
        BigDecimal hotel = amount(base, "0.35");
        BigDecimal food = amount(base, "0.25");
        BigDecimal transport = amount(base, "0.15");
        BigDecimal ticket = amount(base, "0.15");
        BigDecimal other = base.subtract(hotel).subtract(food).subtract(transport).subtract(ticket).setScale(2, RoundingMode.HALF_UP);
        return new BudgetDraft(hotel, food, transport, ticket, other, base.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 使用字符串构造 BigDecimal，避免二进制浮点误差影响金额。
     */
    private BigDecimal amount(BigDecimal base, String ratio) {
        return base.multiply(new BigDecimal(ratio)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 预算草案，字段与预算明细表保持一一对应。
     */
    public record BudgetDraft(
            BigDecimal hotel,
            BigDecimal food,
            BigDecimal transport,
            BigDecimal ticket,
            BigDecimal other,
            BigDecimal total
    ) {
    }
}
