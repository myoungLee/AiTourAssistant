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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 行程草稿应用服务，负责保存用户原始出行需求并创建待生成计划。
 *
 * @author myoung
 */
@Service
public class TripDraftServiceImpl implements TripDraftService {
    private static final Logger log = LoggerFactory.getLogger(TripDraftServiceImpl.class);

    private final TripRequestMapper tripRequestMapper;
    private final TripPlanMapper tripPlanMapper;
    private final ObjectMapper objectMapper;

    /**
     * 注入行程请求和计划持久化组件。
     */
    public TripDraftServiceImpl(TripRequestMapper tripRequestMapper, TripPlanMapper tripPlanMapper, ObjectMapper objectMapper) {
        this.tripRequestMapper = tripRequestMapper;
        this.tripPlanMapper = tripPlanMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存出行请求并创建 PENDING 状态的草稿计划。
     */
    @Override
    @Transactional
    public Long createDraft(Long userId, TripDtos.CreateTripRequest request) throws JsonProcessingException {
        log.info("创建行程草稿开始，userId={} destination={} startDate={} days={} peopleCount={} budgetProvided={} preferencesCount={}",
                userId,
                request.destination(),
                request.startDate(),
                request.days(),
                request.peopleCount(),
                request.budget() != null,
                request.preferences() == null ? 0 : request.preferences().size());

        Instant now = Instant.now();
        TripRequest tripRequest = new TripRequest();
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
        log.info("行程请求已保存，requestId={} userId={} destination={}", tripRequest.getId(), userId, request.destination());

        TripPlan plan = new TripPlan();
        plan.setUserId(userId);
        plan.setRequestId(tripRequest.getId());
        plan.setTitle(request.destination() + request.days() + "日智能行程");
        plan.setStatus(TripPlanStatus.PENDING.name());
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        tripPlanMapper.insert(plan);

        log.info("行程草稿创建成功，planId={} requestId={} userId={} status={}",
                plan.getId(),
                tripRequest.getId(),
                userId,
                plan.getStatus());
        return plan.getId();
    }
}
