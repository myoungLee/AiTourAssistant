/*
 * @author myoung
 */
package com.aitour.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "创建或生成行程请求")
    public record CreateTripRequest(
            @Schema(description = "目的地城市或区域", example = "成都") @NotBlank String destination,
            @Schema(description = "出发日期", example = "2099-06-01") @NotNull @FutureOrPresent LocalDate startDate,
            @Schema(description = "出行天数，范围 1-15", example = "3") @NotNull @Min(1) @Max(15) Integer days,
            @Schema(description = "总预算", example = "3000")
            BigDecimal budget,
            @Schema(description = "出行人数，范围 1-20", example = "2") @NotNull @Min(1) @Max(20) Integer peopleCount,
            @Schema(description = "偏好标签", example = "[\"美食\",\"文化\"]")
            List<String> preferences,
            @Schema(description = "用户补充需求", example = "想吃火锅，行程不要太赶")
            String userInput
    ) {
    }

    /**
     * 创建行程草稿后返回的结果。
     */
    @Schema(description = "创建行程草稿响应")
    public record CreateDraftResponse(
            @Schema(description = "行程草稿 ID", example = "10001")
            Long planId
    ) {
    }

    /**
     * 用户对已有行程提出二次调整时的请求体。
     */
    @Schema(description = "行程二次调整请求")
    public record AdjustTripRequest(
            @Schema(description = "调整指令", example = "把第二天安排得轻松一些") @NotBlank String instruction
    ) {
    }

    @Schema(description = "行程概要响应")
    public record TripSummaryResponse(
            @Schema(description = "行程 ID", example = "10001")
            Long id,
            @Schema(description = "行程标题")
            String title,
            @Schema(description = "目的地", example = "成都")
            String destination,
            @Schema(description = "行程状态", example = "GENERATED")
            String status,
            @Schema(description = "总预算", example = "3000")
            BigDecimal totalBudget
    ) {
    }

    @Schema(description = "行程详情响应")
    public record TripDetailResponse(
            @Schema(description = "行程 ID", example = "10001")
            Long id,
            @Schema(description = "行程标题")
            String title,
            @Schema(description = "AI 总结")
            String summary,
            @Schema(description = "行程状态", example = "GENERATED")
            String status,
            @Schema(description = "总预算", example = "3000")
            BigDecimal totalBudget,
            @Schema(description = "每日安排")
            List<DayResponse> days,
            @Schema(description = "预算明细")
            BudgetResponse budget
    ) {
    }

    @Schema(description = "每日行程响应")
    public record DayResponse(
            @Schema(description = "第几天", example = "1")
            Integer dayIndex,
            @Schema(description = "日期", example = "2099-06-01")
            LocalDate date,
            @Schema(description = "城市", example = "成都")
            String city,
            @Schema(description = "天气摘要")
            String weatherSummary,
            @Schema(description = "当天行程条目")
            List<ItemResponse> items
    ) {
    }

    @Schema(description = "行程条目响应")
    public record ItemResponse(
            @Schema(description = "时间段", example = "MORNING")
            String timeSlot,
            @Schema(description = "地点名称")
            String placeName,
            @Schema(description = "地点类型", example = "food")
            String placeType,
            @Schema(description = "地址")
            String address,
            @Schema(description = "预计停留分钟数", example = "120")
            Integer durationMinutes,
            @Schema(description = "交通建议")
            String transportSuggestion,
            @Schema(description = "预估费用")
            BigDecimal estimatedCost,
            @Schema(description = "推荐理由")
            String reason
    ) {
    }

    @Schema(description = "预算明细响应")
    public record BudgetResponse(
            @Schema(description = "住宿费用")
            BigDecimal hotelCost,
            @Schema(description = "餐饮费用")
            BigDecimal foodCost,
            @Schema(description = "交通费用")
            BigDecimal transportCost,
            @Schema(description = "门票费用")
            BigDecimal ticketCost,
            @Schema(description = "其他费用")
            BigDecimal otherCost
    ) {
    }
}
