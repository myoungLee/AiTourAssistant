/*
 * @author myoung
 */
package com.aitour.service.impl;

import com.aitour.common.dto.TripDtos;
import com.aitour.common.entity.TripPlan;
import com.aitour.common.entity.TripRequest;
import com.aitour.domain.TripPlanStatus;
import com.aitour.mapper.TripPlanMapper;
import com.aitour.mapper.TripRequestMapper;
import com.aitour.service.TripDraftService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 保存用户行程需求并创建待生成的行程草稿。
 *
 * @author myoung
 */
@Service
public class TripDraftServiceImpl implements TripDraftService {
    private final TripRequestMapper tripRequestMapper;
    private final TripPlanMapper tripPlanMapper;
    private final ObjectMapper objectMapper;

    /**
     * 注入行程请求 mapper、行程主表 mapper 和 JSON 序列化器。
     */
    public TripDraftServiceImpl(TripRequestMapper tripRequestMapper, TripPlanMapper tripPlanMapper, ObjectMapper objectMapper) {
        this.tripRequestMapper = tripRequestMapper;
        this.tripPlanMapper = tripPlanMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存用户原始出行需求，并创建 PENDING 状态的行程草稿。
     */
    @Override
    @Transactional
    public Long createDraft(Long userId, TripDtos.CreateTripRequest request) throws JsonProcessingException {
        Instant now = Instant.now();
        TripRequest tripRequest = new TripRequest();
        tripRequest.setId(newPositiveId());
        tripRequest.setUserId(userId);
        tripRequest.setUserInput(request.userInput());
        tripRequest.setDestination(request.destination());
        tripRequest.setStartDate(request.startDate());
        tripRequest.setDays(request.days());
        tripRequest.setBudget(request.budget());
        tripRequest.setPeopleCount(request.peopleCount());
        tripRequest.setPreferencesJson(objectMapper.writeValueAsString(request.preferences()));
        tripRequest.setCreatedAt(now);
        tripRequestMapper.insert(tripRequest);

        TripPlan plan = new TripPlan();
        plan.setId(newPositiveId());
        plan.setUserId(userId);
        plan.setRequestId(tripRequest.getId());
        plan.setTitle(request.destination() + request.days() + "日智能行程");
        plan.setStatus(TripPlanStatus.PENDING.name());
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        tripPlanMapper.insert(plan);
        return plan.getId();
    }

    /**
     * 生成正数主键，沿用当前项目的本地 UUID 主键策略。
     */
    private Long newPositiveId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
