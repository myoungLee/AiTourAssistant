/*
 * @author myoung
 */
package com.aitour.domain.planning;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 每日行程编排规则组件，将候选景点分配到上午、下午和晚上等时间段，并根据节奏做收缩。
 *
 * @author myoung
 */
@Component
public class DaySchedulePlanner {
    private static final List<String> DEFAULT_SLOTS = List.of("MORNING", "AFTERNOON", "EVENING");
    private static final List<String> RELAXED_SLOTS = List.of("MORNING", "AFTERNOON");

    /**
     * 根据天数、人数、偏好和预算生成每日安排，并保证同一天不重复同一景点。
     */
    public List<DaySchedule> plan(
            LocalDate startDate,
            int days,
            String city,
            List<Map<String, Object>> places,
            int peopleCount,
            List<String> preferences,
            BudgetEstimator.BudgetDraft budget
    ) {
        List<Map<String, Object>> safePlaces = normalizePlaces(city, places);
        List<String> slots = chooseSlots(days, peopleCount, preferences, budget);
        List<DaySchedule> schedules = new ArrayList<>();
        int cursor = 0;
        for (int day = 1; day <= days; day++) {
            List<PlanItemDraft> items = new ArrayList<>();
            Set<String> usedPlaceNames = new LinkedHashSet<>();
            for (String slot : slots) {
                Map<String, Object> place = nextAvailablePlace(safePlaces, cursor, usedPlaceNames);
                cursor = nextCursor(safePlaces, cursor, place);
                String placeName = String.valueOf(place.getOrDefault("name", city + "精选景点"));
                usedPlaceNames.add(placeName);
                items.add(new PlanItemDraft(
                        slot,
                        placeName,
                        String.valueOf(place.getOrDefault("type", "ATTRACTION")),
                        numberValue(place.get("durationMinutes"), durationBySlot(slot, budget)),
                        reasonFor(slot, preferences, budget)
                ));
            }
            schedules.add(new DaySchedule(day, startDate.plusDays(day - 1L), city, items));
        }
        return schedules;
    }

    /**
     * 为缺省或空的候选景点提供本地兜底，避免无景点时行程编排完全失败。
     */
    private List<Map<String, Object>> normalizePlaces(String city, List<Map<String, Object>> places) {
        if (places == null || places.isEmpty()) {
            return List.of(
                    Map.of("name", city + "城市地标", "type", "ATTRACTION", "durationMinutes", 120),
                    Map.of("name", city + "特色街区", "type", "FOOD", "durationMinutes", 90),
                    Map.of("name", city + "文化博物馆", "type", "CULTURE", "durationMinutes", 120)
            );
        }
        return places;
    }

    /**
     * 根据行程强度、人数、偏好和预算不足状态决定单日安排节奏。
     */
    private List<String> chooseSlots(int days, int peopleCount, List<String> preferences, BudgetEstimator.BudgetDraft budget) {
        boolean relaxedPreference = preferences != null && preferences.stream().anyMatch("轻松"::equals);
        boolean longTrip = days >= 5;
        boolean largeGroup = peopleCount >= 4;
        boolean budgetTight = budget != null && budget.insufficient();
        return relaxedPreference || longTrip || largeGroup || budgetTight ? RELAXED_SLOTS : DEFAULT_SLOTS;
    }

    /**
     * 在候选列表中选出当前日还未使用的景点；如果全部用过，再按顺序回退到当前游标。
     */
    private Map<String, Object> nextAvailablePlace(List<Map<String, Object>> places, int cursor, Set<String> usedPlaceNames) {
        for (int offset = 0; offset < places.size(); offset++) {
            Map<String, Object> candidate = places.get((cursor + offset) % places.size());
            String candidateName = String.valueOf(candidate.getOrDefault("name", ""));
            if (!usedPlaceNames.contains(candidateName)) {
                return candidate;
            }
        }
        return places.get(cursor % places.size());
    }

    /**
     * 计算下一次选点起始游标，保证多天安排尽量顺序推进而不是总从头开始。
     */
    private int nextCursor(List<Map<String, Object>> places, int currentCursor, Map<String, Object> selectedPlace) {
        for (int index = 0; index < places.size(); index++) {
            if (places.get(index) == selectedPlace) {
                return index + 1;
            }
        }
        return currentCursor + 1;
    }

    /**
     * 预算紧张时压缩单条活动时长，避免每日安排名义上变少但实际强度没降下来。
     */
    private int durationBySlot(String slot, BudgetEstimator.BudgetDraft budget) {
        if (budget != null && budget.insufficient()) {
            return "MORNING".equals(slot) ? 90 : 120;
        }
        return "EVENING".equals(slot) ? 90 : 120;
    }

    /**
     * 为条目生成可读理由，体现轻松偏好或预算提醒已经被排程逻辑吸收。
     */
    private String reasonFor(String slot, List<String> preferences, BudgetEstimator.BudgetDraft budget) {
        boolean relaxedPreference = preferences != null && preferences.stream().anyMatch("轻松"::equals);
        if (budget != null && budget.insufficient()) {
            return "结合预算提醒，优先安排低成本且节奏较缓的活动时段。";
        }
        if (relaxedPreference) {
            return "结合轻松偏好，避免过度赶场并保留机动休息时间。";
        }
        return "结合当日时段和景点类型做均衡安排。";
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
     *
     * @author myoung
     */
    public record DaySchedule(Integer dayIndex, LocalDate date, String city, List<PlanItemDraft> items) {
    }

    /**
     * 单个时间段的行程条目草案。
     *
     * @author myoung
     */
    public record PlanItemDraft(
            String timeSlot,
            String placeName,
            String placeType,
            Integer durationMinutes,
            String reason
    ) {
    }
}
