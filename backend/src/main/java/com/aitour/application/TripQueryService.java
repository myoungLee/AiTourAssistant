/*
 * @author myoung
 */
package com.aitour.application;

import com.aitour.api.dto.TripDtos;
import com.aitour.domain.TripPlan;
import com.aitour.infrastructure.exception.ApiException;
import com.aitour.infrastructure.persistence.TripPlanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查询当前用户自己的历史行程，所有查询都按 userId 隔离。
 *
 * @author myoung
 */
@Service
public class TripQueryService {
    private final TripPlanMapper tripPlanMapper;

    public TripQueryService(TripPlanMapper tripPlanMapper) {
        this.tripPlanMapper = tripPlanMapper;
    }

    public List<TripDtos.TripSummaryResponse> listTrips(Long userId) {
        return tripPlanMapper.selectList(new LambdaQueryWrapper<TripPlan>()
                        .eq(TripPlan::getUserId, userId)
                        .orderByDesc(TripPlan::getCreatedAt))
                .stream()
                .map(plan -> new TripDtos.TripSummaryResponse(
                        plan.getId(), plan.getTitle(), "", plan.getStatus(), plan.getTotalBudget()))
                .toList();
    }

    public TripDtos.TripDetailResponse getTrip(Long userId, Long planId) {
        TripPlan plan = tripPlanMapper.selectOne(new LambdaQueryWrapper<TripPlan>()
                .eq(TripPlan::getId, planId)
                .eq(TripPlan::getUserId, userId));
        if (plan == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "行程不存在");
        }
        return new TripDtos.TripDetailResponse(
                plan.getId(), plan.getTitle(), plan.getSummary(), plan.getStatus(), plan.getTotalBudget(), List.of(), null
        );
    }
}
