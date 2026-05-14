/*
 * @author myoung
 */
package com.aitour.application;

import com.aitour.api.dto.TripDtos;
import com.aitour.domain.TripPlan;
import com.aitour.domain.TripPlanStatus;
import com.aitour.domain.TripRequest;
import com.aitour.infrastructure.persistence.TripPlanMapper;
import com.aitour.infrastructure.persistence.TripRequestMapper;
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
public class TripDraftService {
    private final TripRequestMapper tripRequestMapper;
    private final TripPlanMapper tripPlanMapper;
    private final ObjectMapper objectMapper;

    public TripDraftService(TripRequestMapper tripRequestMapper, TripPlanMapper tripPlanMapper, ObjectMapper objectMapper) {
        this.tripRequestMapper = tripRequestMapper;
        this.tripPlanMapper = tripPlanMapper;
        this.objectMapper = objectMapper;
    }

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

    private Long newPositiveId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
