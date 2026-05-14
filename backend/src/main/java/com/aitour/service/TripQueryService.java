/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.common.dto.TripDtos;

import java.util.List;

/**
 * 行程查询服务接口，定义历史行程列表和详情查询能力。
 *
 * @author myoung
 */
public interface TripQueryService {

    /**
     * 查询当前用户的行程概要列表。
     */
    List<TripDtos.TripSummaryResponse> listTrips(Long userId);

    /**
     * 查询当前用户指定行程详情。
     */
    TripDtos.TripDetailResponse getTrip(Long userId, Long planId);
}
