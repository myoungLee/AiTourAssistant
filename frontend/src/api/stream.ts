/*
 * @author myoung
 */
import { ACCESS_TOKEN_KEY } from './http'
import { formParams } from './form'
import type { CreateTripRequest, StreamEvent, StreamEventName } from './types'

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
 * 消费后端 SSE 响应体，按事件边界解析后回调给页面状态层。
 */
export async function consumeEventStream(
  response: Response,
  onEvent: (event: StreamEvent) => void
): Promise<void> {
  if (!response.ok) {
    throw new Error(`流式请求失败：${response.status}`)
  }

  if (!response.body) {
    throw new Error('流式响应体为空')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done })

    let boundaryIndex = buffer.indexOf('\n\n')
    while (boundaryIndex >= 0) {
      const rawEvent = buffer.slice(0, boundaryIndex).trim()
      buffer = buffer.slice(boundaryIndex + 2)
      if (rawEvent) {
        const parsed = parseEventBlock(rawEvent)
        if (parsed) {
          onEvent(parsed)
        }
      }
      boundaryIndex = buffer.indexOf('\n\n')
    }

    if (done) {
      break
    }
  }

  const tailEvent = buffer.trim()
  if (tailEvent) {
    const parsed = parseEventBlock(tailEvent)
    if (parsed) {
      onEvent(parsed)
    }
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

/**
 * 解析单个 SSE 事件块，当前只处理 event/data 两类字段。
 */
function parseEventBlock(block: string): StreamEvent | null {
  const lines = block.split('\n')
  let eventName = ''
  const dataLines: string[] = []

  lines.forEach((line) => {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      return
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  })

  if (!eventName || dataLines.length === 0) {
    return null
  }

  const parsedData = JSON.parse(dataLines.join('\n')) as StreamEvent['data']
  return {
    event: eventName as StreamEventName,
    data: parsedData
  } as StreamEvent
}
