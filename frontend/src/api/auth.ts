/*
 * @author myoung
 */
import { ACCESS_TOKEN_KEY, http } from './http'
import { formParams } from './form'
import type { AuthResponse } from './types'

/**
 * 认证接口封装。
 *
 * @author myoung
 */
export const authApi = {
  /**
   * 用户注册。
   */
  async register(username: string, password: string, nickname?: string): Promise<AuthResponse> {
    const response = await http.post<AuthResponse>(
      '/auth/register',
      formParams({ username, password, nickname }),
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      }
    )
    return response.data
  },

  /**
   * 用户登录。
   */
  async login(username: string, password: string): Promise<AuthResponse> {
    const response = await http.post<AuthResponse>(
      '/auth/login',
      formParams({ username, password }),
      {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      }
    )
    return response.data
  },

  /**
   * 持久化 accessToken，供后续请求使用。
   */
  saveAccessToken(accessToken: string): void {
    if (typeof window === 'undefined') {
      return
    }
    window.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  },

  /**
   * 清理本地 accessToken。
   */
  clearAccessToken(): void {
    if (typeof window === 'undefined') {
      return
    }
    window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  }
}
