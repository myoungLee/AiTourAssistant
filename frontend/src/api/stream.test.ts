/*
 * @author myoung
 */
import { describe, expect, it } from 'vitest'

import { consumeEventStream } from './stream'

describe('consumeEventStream', () => {
  it('parses structured SSE events from a readable stream', async () => {
    const encoder = new TextEncoder()
    const events: unknown[] = []
    const stream = new ReadableStream<Uint8Array>({
      /**
       * 模拟后端分片推送事件流，验证前端能正确缓存并按事件边界解析。
       */
      start(controller) {
        controller.enqueue(encoder.encode('event: progress\ndata: {"step":"parse","message":"正在解析","percent":10}\n\n'))
        controller.enqueue(encoder.encode('event: tool_result\ndata: {"tool":"weather","summary":"晴天","data":{"temperature":26'))
        controller.enqueue(encoder.encode('}}\n\nevent: ai_delta\ndata: {"text":"第一段总结"}\n\n'))
        controller.close()
      }
    })

    await consumeEventStream(new Response(stream, { status: 200 }), (event) => {
      events.push(event)
    })

    expect(events).toEqual([
      {
        event: 'progress',
        data: {
          step: 'parse',
          message: '正在解析',
          percent: 10
        }
      },
      {
        event: 'tool_result',
        data: {
          tool: 'weather',
          summary: '晴天',
          data: {
            temperature: 26
          }
        }
      },
      {
        event: 'ai_delta',
        data: {
          text: '第一段总结'
        }
      }
    ])
  })
})
