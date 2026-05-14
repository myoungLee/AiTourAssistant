/*
 * @author myoung
 */
package com.aitour.service;

import com.aitour.common.dto.TripDtos;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * 行程草稿服务接口，定义保存用户出行需求并创建草稿的能力。
 *
 * @author myoung
 */
public interface TripDraftService {

    /**
     * 创建待生成的行程草稿并返回行程 ID。
     */
    Long createDraft(Long userId, TripDtos.CreateTripRequest request) throws JsonProcessingException;
}
