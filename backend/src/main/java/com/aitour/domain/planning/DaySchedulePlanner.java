/*
 * @author myoung
 */
package com.aitour.domain.planning;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 每日行程编排规则组件，将候选景点分配到上午、下午和晚上三个时间段。
 *
 * @author myoung
 */
@Component
public class DaySchedulePlanner {
    private static final List<String> DEFAULT_SLOTS = List.of("MORNING", "AFTERNOON", "EVENING");

    /**
     * 根据开始日期、天数和候选景点生成每日安排；候选景点不足时循环使用，保证 MVP 始终能返回快照。
     */
    public List<DaySchedule> plan(LocalDate startDate, int days, String city, List<Map<String, Object>> places) {
        List<Map<String, Object>> safePlaces = places.isEmpty()
                ? List.of(Map.of("name", city + "精选景点", "type", "ATTRACTION", "durationMinutes", 120))
                : places;
        List<DaySchedule> schedules = new ArrayList<>();
        int placeIndex = 0;
        for (int day = 1; day <= days; day++) {
            List<PlanItemDraft> items = new ArrayList<>();
            for (String slot : DEFAULT_SLOTS) {
                Map<String, Object> place = safePlaces.get(placeIndex % safePlaces.size());
                items.add(new PlanItemDraft(
                        slot,
                        String.valueOf(place.getOrDefault("name", city + "精选景点")),
                        String.valueOf(place.getOrDefault("type", "ATTRACTION")),
                        numberValue(place.get("durationMinutes"), 120)
                ));
                placeIndex++;
            }
            schedules.add(new DaySchedule(day, startDate.plusDays(day - 1L), city, items));
        }
        return schedules;
    }

    /**
     * 将工具返回的数字字段转换为整数，缺失或类型不匹配时使用兜底值。
     */
    private Integer numberValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    /**
     * 单日行程草案。
     */
    public record DaySchedule(Integer dayIndex, LocalDate date, String city, List<PlanItemDraft> items) {
    }

    /**
     * 单个时间段的行程条目草案。
     */
    public record PlanItemDraft(String timeSlot, String placeName, String placeType, Integer durationMinutes) {
    }
}
