/*
 * @author myoung
 */
import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'

import type { Result } from './types'

const ACCESS_TOKEN_KEY = 'aitour_access_token'

/**
 * 读取本地 accessToken，供请求头注入使用。
 */
function resolveAccessToken(): string | null {
  if (typeof window === 'undefined') {
    return null
  }
  return window.localStorage.getItem(ACCESS_TOKEN_KEY)
}

/**
 * 统一后端 HTTP 客户端，负责认证头注入和 Result.data 解包。
 *
 * @author myoung
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

/**
 * 请求发出前自动补充 Bearer Token。
 */
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const accessToken = resolveAccessToken()
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

/**
 * 对普通 REST 响应做统一解包，业务层直接拿到 data。
 */
http.interceptors.response.use((response) => {
  const body = response.data as Result<unknown>
  if (body && typeof body === 'object' && 'code' in body) {
    if (body.code !== 1) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    response.data = body.data
  }
  return response
})

export { ACCESS_TOKEN_KEY }
