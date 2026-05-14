/*
 * @author myoung
 */
package com.aitour.api.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 行程草稿和历史查询接口 DTO。
 *
 * @author myoung
 */
public final class TripDtos {
    private TripDtos() {
    }

    public record CreateTripRequest(
            @NotBlank String destination,
            @NotNull @FutureOrPresent LocalDate startDate,
            @NotNull @Min(1) @Max(15) Integer days,
            BigDecimal budget,
            @NotNull @Min(1) @Max(20) Integer peopleCount,
            List<String> preferences,
            String userInput
    ) {
    }

    public record TripSummaryResponse(
            Long id,
            String title,
            String destination,
            String status,
            BigDecimal totalBudget
    ) {
    }

    public record TripDetailResponse(
            Long id,
            String title,
            String summary,
            String status,
            BigDecimal totalBudget,
            List<DayResponse> days,
            BudgetResponse budget
    ) {
    }

    public record DayResponse(
            Integer dayIndex,
            LocalDate date,
            String city,
            String weatherSummary,
            List<ItemResponse> items
    ) {
    }

    public record ItemResponse(
            String timeSlot,
            String placeName,
            String placeType,
            String address,
            Integer durationMinutes,
            String transportSuggestion,
            BigDecimal estimatedCost,
            String reason
    ) {
    }

    public record BudgetResponse(
            BigDecimal hotelCost,
            BigDecimal foodCost,
            BigDecimal transportCost,
            BigDecimal ticketCost,
            BigDecimal otherCost
    ) {
    }
}
