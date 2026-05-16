/*
 * @author myoung
 */
package com.aitour.domain.planning;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 景点排序规则组件，第一版对真实工具返回的景点做稳定名称排序。
 *
 * @author myoung
 */
@Component
public class AttractionRanker {

    /**
     * 对候选景点进行稳定排序，后续可以替换为结合距离、热度和用户偏好的排序策略。
     */
    public List<Map<String, Object>> rank(List<Map<String, Object>> places) {
        return places.stream()
                .sorted(Comparator.comparing(place -> String.valueOf(place.getOrDefault("name", ""))))
                .toList();
    }
}
