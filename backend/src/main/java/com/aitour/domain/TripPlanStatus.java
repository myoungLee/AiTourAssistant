/*
 * @author myoung
 */
package com.aitour.domain;

/**
 * 行程计划生成状态。
 *
 * @author myoung
 */
public enum TripPlanStatus {
    PENDING,
    GENERATING,
    GENERATED,
    FAILED,
    CANCELLED
}
