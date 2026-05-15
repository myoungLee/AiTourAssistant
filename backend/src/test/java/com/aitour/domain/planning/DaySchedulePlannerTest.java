/*
 * @author myoung
 */
package com.aitour.domain.planning;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证每日行程编排规则会根据节奏和预算调整安排强度。
 *
 * @author myoung
 */
class DaySchedulePlannerTest {

    /**
     * 常规双人两天行程应保持三段日程，满足基础可用性。
     */
    @Test
    void shouldCreateThreeSlotsPerDay() {
        DaySchedulePlanner planner = new DaySchedulePlanner();
        BudgetEstimator.BudgetDraft budget = new BudgetEstimator.BudgetDraft(
                BigDecimal.valueOf(1260),
                BigDecimal.valueOf(900),
                BigDecimal.valueOf(540),
                BigDecimal.valueOf(540),
                BigDecimal.valueOf(360),
                BigDecimal.valueOf(3600),
                BigDecimal.valueOf(3600),
                false,
                null
        );

        List<DaySchedulePlanner.DaySchedule> result = planner.plan(
                LocalDate.of(2099, 6, 1),
                2,
                "成都",
                List.of(
                        Map.of("name", "武侯祠", "type", "culture", "durationMinutes", 120),
                        Map.of("name", "宽窄巷子", "type", "food", "durationMinutes", 90),
                        Map.of("name", "杜甫草堂", "type", "culture", "durationMinutes", 120)
                ),
                2,
                List.of("美食"),
                budget
        );

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().items()).hasSize(3);
    }

    /**
     * 长天数、多人、偏轻松且预算紧张时，应降低单日节奏，并保证同一天不重复同一景点。
     */
    @Test
    void shouldReduceDailyPaceAndAvoidDuplicatePlacesWhenTripIsRelaxedAndBudgetTight() {
        DaySchedulePlanner planner = new DaySchedulePlanner();
        BudgetEstimator.BudgetDraft budget = new BudgetEstimator.BudgetDraft(
                BigDecimal.valueOf(350),
                BigDecimal.valueOf(250),
                BigDecimal.valueOf(150),
                BigDecimal.valueOf(150),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(5400),
                true,
                "预算提醒：当前预算低于建议预算 5400.00 元，建议放慢节奏并优先低成本安排。"
        );

        List<DaySchedulePlanner.DaySchedule> result = planner.plan(
                LocalDate.of(2099, 6, 1),
                5,
                "成都",
                List.of(
                        Map.of("name", "武侯祠", "type", "culture", "durationMinutes", 120),
                        Map.of("name", "宽窄巷子", "type", "food", "durationMinutes", 90)
                ),
                4,
                List.of("轻松", "美食"),
                budget
        );

        assertThat(result).hasSize(5);
        assertThat(result.getFirst().items()).hasSize(2);
        assertThat(result.getFirst().items().stream().map(DaySchedulePlanner.PlanItemDraft::placeName).distinct().count())
                .isEqualTo(result.getFirst().items().size());
    }
}
