/*
 * @author myoung
 */
import { AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it } from 'vitest'

import { ACCESS_TOKEN_KEY, http } from './http'
import { formParams } from './form'

/**
 * 璇诲彇璇锋眰鎷︽埅鍣ㄥ洖璋冿紝鐢ㄤ簬楠岃瘉 token 娉ㄥ叆琛屼负銆? *
 * @author myoung
 */
function resolveRequestInterceptor() {
  const interceptor = http.interceptors.request.handlers?.[0]?.fulfilled
  expect(interceptor).toBeTypeOf('function')
  return interceptor as (config: InternalAxiosRequestConfig) => Promise<InternalAxiosRequestConfig> | InternalAxiosRequestConfig
}

/**
 * 璇诲彇鍝嶅簲鎷︽埅鍣ㄥ洖璋冿紝鐢ㄤ簬楠岃瘉 Result 瑙ｅ寘琛屼负銆? *
 * @author myoung
 */
function resolveResponseInterceptor() {
  const interceptor = http.interceptors.response.handlers?.[0]?.fulfilled
  expect(interceptor).toBeTypeOf('function')
  return interceptor as (response: AxiosResponse) => Promise<AxiosResponse> | AxiosResponse
}

describe('http api helpers', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('adds bearer token header from localStorage', async () => {
    window.localStorage.setItem(ACCESS_TOKEN_KEY, 'token-123')

    const interceptor = resolveRequestInterceptor()
    const config = await interceptor({
      headers: new AxiosHeaders()
    } as InternalAxiosRequestConfig)

    expect(config?.headers.get('Authorization')).toBe('Bearer token-123')
  })

  it('unwraps successful result payload', async () => {
    const interceptor = resolveResponseInterceptor()
    const response = await interceptor({
      data: {
        code: 1,
        msg: null,
        data: { username: 'alice' }
      },
      status: 200,
      statusText: 'OK',
      headers: new AxiosHeaders(),
      config: {
        headers: new AxiosHeaders()
      } as InternalAxiosRequestConfig
    })

    expect(response?.data).toEqual({ username: 'alice' })
  })

  it('builds repeated params for array fields', () => {
    const params = formParams({
      destination: '成都',
      preferences: ['美食', '文化'],
      userInput: ''
    })

    expect(params.get('destination')).toBe('成都')
    expect(params.getAll('preferences')).toEqual(['美食', '文化'])
    expect(params.has('userInput')).toBe(false)
  })
})
