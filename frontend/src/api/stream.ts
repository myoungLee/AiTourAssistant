/*
 * @author myoung
 */
import { ACCESS_TOKEN_KEY } from './http'
import { formParams } from './form'
import type { CreateTripRequest } from './types'

/**
 * 行程流式接口封装。
 *
 * @author myoung
 */
export const streamApi = {
  /**
   * 发起 SSE 风格的流式行程生成请求。
   */
  async streamPlan(payload: CreateTripRequest): Promise<Response> {
    return postEventStream('/api/trips/stream-plan', payload)
  },

  /**
   * 发起行程二次调整流式请求。
   */
  async adjustStream(id: number, instruction: string): Promise<Response> {
    return postEventStream(`/api/trips/${id}/adjust-stream`, { instruction })
  }
}

/**
 * 统一发起 text/event-stream POST 请求。
 */
async function postEventStream<T extends object>(url: string, payload: T): Promise<Response> {
  const headers = new Headers({
    Accept: 'text/event-stream',
    'Content-Type': 'application/x-www-form-urlencoded'
  })

  if (typeof window !== 'undefined') {
    const accessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY)
    if (accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }
  }

  return fetch(url, {
    method: 'POST',
    headers,
    body: formParams(payload)
  })
}
