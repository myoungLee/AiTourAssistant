/*
 * @author myoung
 */
package com.aitour.controller;

import com.aitour.common.dto.TripDtos;
import com.aitour.service.TripDraftService;
import com.aitour.service.TripQueryService;
import com.aitour.config.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 行程草稿创建和历史行程查询接口。
 *
 * @author myoung
 */
@RestController
@RequestMapping("/api/trips")
public class TripController {
    private final TripDraftService tripDraftService;
    private final TripQueryService tripQueryService;

    public TripController(TripDraftService tripDraftService, TripQueryService tripQueryService) {
        this.tripDraftService = tripDraftService;
        this.tripQueryService = tripQueryService;
    }

    @PostMapping("/draft")
    public Map<String, Long> createDraft(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody TripDtos.CreateTripRequest request
    ) throws JsonProcessingException {
        return Map.of("planId", tripDraftService.createDraft(currentUser.id(), request));
    }

    @GetMapping
    public List<TripDtos.TripSummaryResponse> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return tripQueryService.listTrips(currentUser.id());
    }

    @GetMapping("/{id}")
    public TripDtos.TripDetailResponse detail(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
        return tripQueryService.getTrip(currentUser.id(), id);
    }
}
