/*
 * @author myoung
 */
package com.aitour.domain.planning;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证每日行程编排规则可以按天生成固定时间段。
 *
 * @author myoung
 */
class DaySchedulePlannerTest {

    /**
     * 两天行程应生成两组日程，且每天包含上午、下午、晚上三个条目。
     */
    @Test
    void shouldCreateThreeSlotsPerDay() {
        DaySchedulePlanner planner = new DaySchedulePlanner();

        List<DaySchedulePlanner.DaySchedule> result = planner.plan(
                LocalDate.of(2099, 6, 1),
                2,
                "成都",
                List.of(Map.of("name", "武侯祠"), Map.of("name", "宽窄巷子"), Map.of("name", "杜甫草堂"))
        );

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().items()).hasSize(3);
    }
}
