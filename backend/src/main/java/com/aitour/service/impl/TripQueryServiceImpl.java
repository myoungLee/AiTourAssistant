/*
 * @author myoung
 */
package com.aitour.service.impl;

import com.aitour.common.dto.TripDtos;
import com.aitour.common.entity.BudgetBreakdown;
import com.aitour.common.entity.TripDay;
import com.aitour.common.entity.TripItem;
import com.aitour.common.entity.TripPlan;
import com.aitour.common.exception.ApiException;
import com.aitour.mapper.BudgetBreakdownMapper;
import com.aitour.mapper.TripDayMapper;
import com.aitour.mapper.TripItemMapper;
import com.aitour.mapper.TripPlanMapper;
import com.aitour.service.TripQueryService;
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
public class TripQueryServiceImpl implements TripQueryService {
    private final TripPlanMapper tripPlanMapper;
    private final TripDayMapper tripDayMapper;
    private final TripItemMapper tripItemMapper;
    private final BudgetBreakdownMapper budgetBreakdownMapper;

    /**
     * 注入行程主表、每日安排、条目和预算明细 mapper。
     */
    public TripQueryServiceImpl(
            TripPlanMapper tripPlanMapper,
            TripDayMapper tripDayMapper,
            TripItemMapper tripItemMapper,
            BudgetBreakdownMapper budgetBreakdownMapper
    ) {
        this.tripPlanMapper = tripPlanMapper;
        this.tripDayMapper = tripDayMapper;
        this.tripItemMapper = tripItemMapper;
        this.budgetBreakdownMapper = budgetBreakdownMapper;
    }

    /**
     * 查询当前用户的行程概要列表。
     */
    @Override
    public List<TripDtos.TripSummaryResponse> listTrips(Long userId) {
        return tripPlanMapper.selectList(new LambdaQueryWrapper<TripPlan>()
                        .eq(TripPlan::getUserId, userId)
                        .orderByDesc(TripPlan::getCreatedAt))
                .stream()
                .map(plan -> new TripDtos.TripSummaryResponse(
                        plan.getId(), plan.getTitle(), "", plan.getStatus(), plan.getTotalBudget()))
                .toList();
    }

    /**
     * 查询行程详情，并按天组装条目和预算信息。
     */
    @Override
    public TripDtos.TripDetailResponse getTrip(Long userId, Long planId) {
        TripPlan plan = tripPlanMapper.selectOne(new LambdaQueryWrapper<TripPlan>()
                .eq(TripPlan::getId, planId)
                .eq(TripPlan::getUserId, userId));
        if (plan == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "行程不存在");
        }
        List<TripDtos.DayResponse> days = tripDayMapper.selectList(new LambdaQueryWrapper<TripDay>()
                        .eq(TripDay::getPlanId, planId)
                        .orderByAsc(TripDay::getDayIndex))
                .stream()
                .map(this::toDayResponse)
                .toList();
        BudgetBreakdown budget = budgetBreakdownMapper.selectOne(new LambdaQueryWrapper<BudgetBreakdown>()
                .eq(BudgetBreakdown::getPlanId, planId)
                .last("limit 1"));
        return new TripDtos.TripDetailResponse(
                plan.getId(), plan.getTitle(), plan.getSummary(), plan.getStatus(), plan.getTotalBudget(), days, toBudgetResponse(budget)
        );
    }

    /**
     * 将每日实体和其下条目转换为前端响应 DTO。
     */
    private TripDtos.DayResponse toDayResponse(TripDay day) {
        List<TripDtos.ItemResponse> items = tripItemMapper.selectList(new LambdaQueryWrapper<TripItem>()
                        .eq(TripItem::getDayId, day.getId()))
                .stream()
                .map(this::toItemResponse)
                .toList();
        return new TripDtos.DayResponse(day.getDayIndex(), day.getDate(), day.getCity(), day.getWeatherSummary(), items);
    }

    /**
     * 将行程条目实体转换为前端响应 DTO。
     */
    private TripDtos.ItemResponse toItemResponse(TripItem item) {
        return new TripDtos.ItemResponse(
                item.getTimeSlot(),
                item.getPlaceName(),
                item.getPlaceType(),
                item.getAddress(),
                item.getDurationMinutes(),
                item.getTransportSuggestion(),
                item.getEstimatedCost(),
                item.getReason()
        );
    }

    /**
     * 将预算实体转换为前端响应 DTO，缺失预算时返回 null。
     */
    private TripDtos.BudgetResponse toBudgetResponse(BudgetBreakdown budget) {
        if (budget == null) {
            return null;
        }
        return new TripDtos.BudgetResponse(
                budget.getHotelCost(),
                budget.getFoodCost(),
                budget.getTransportCost(),
                budget.getTicketCost(),
                budget.getOtherCost()
        );
    }
}
