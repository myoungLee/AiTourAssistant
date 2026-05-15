/*
 * @author myoung
 */
import { http } from './http'
import { formParams } from './form'
import type { CreateTripRequest, TripDetailResponse, TripSummaryResponse } from './types'

/**
 * 行程接口封装。
 *
 * @author myoung
 */
export const tripsApi = {
  /**
   * 创建行程草稿并返回 planId。
   */
  async createDraft(payload: CreateTripRequest): Promise<{ planId: number }> {
    const response = await http.post<{ planId: number }>('/trips/draft', formParams(payload), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      }
    })
    return response.data
  },

  /**
   * 查询当前用户历史行程。
   */
  async list(): Promise<TripSummaryResponse[]> {
    const response = await http.get<TripSummaryResponse[]>('/trips')
    return response.data
  },

  /**
   * 查询行程详情。
   */
  async detail(id: number): Promise<TripDetailResponse> {
    const response = await http.get<TripDetailResponse>(`/trips/${id}`)
    return response.data
  }
}
