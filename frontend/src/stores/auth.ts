/*
 * @author myoung
 */
import { defineStore } from 'pinia'

import { ACCESS_TOKEN_KEY } from '@/api/http'
import { authApi } from '@/api/auth'
import { usersApi } from '@/api/users'
import type { CurrentUserResponse } from '@/api/types'

interface LoginForm {
  username: string
  password: string
}

interface RegisterForm extends LoginForm {
  nickname?: string
}

/**
 * 维护登录态、当前用户和认证流程状态。
 *
 * @author myoung
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: readAccessToken(),
    currentUser: null as CurrentUserResponse | null,
    loading: false
  }),
  getters: {
    /**
     * 标记当前是否已经具备登录态。
     */
    isAuthenticated(state): boolean {
      return Boolean(state.accessToken)
    }
  },
  actions: {
    /**
     * 处理登录并同步当前用户信息。
     */
    async login(payload: LoginForm): Promise<void> {
      this.loading = true
      try {
        const response = await authApi.login(payload.username, payload.password)
        this.accessToken = response.accessToken
        authApi.saveAccessToken(response.accessToken)
        await this.fetchCurrentUser()
      } finally {
        this.loading = false
      }
    },

    /**
     * 处理注册并同步当前用户信息。
     */
    async register(payload: RegisterForm): Promise<void> {
      this.loading = true
      try {
        const response = await authApi.register(payload.username, payload.password, payload.nickname)
        this.accessToken = response.accessToken
        authApi.saveAccessToken(response.accessToken)
        await this.fetchCurrentUser()
      } finally {
        this.loading = false
      }
    },

    /**
     * 查询当前登录用户资料，token 失效时自动清理本地状态。
     */
    async fetchCurrentUser(): Promise<void> {
      if (!this.accessToken) {
        this.currentUser = null
        return
      }

      try {
        this.currentUser = await usersApi.currentUser()
      } catch (error) {
        this.logout()
        throw error
      }
    },

    /**
     * 清理本地登录态。
     */
    logout(): void {
      this.accessToken = null
      this.currentUser = null
      authApi.clearAccessToken()
    }
  }
})

/**
 * 从浏览器本地缓存读取 accessToken，支持页面刷新后的登录态恢复。
 */
function readAccessToken(): string | null {
  if (typeof window === 'undefined') {
    return null
  }
  return window.localStorage.getItem(ACCESS_TOKEN_KEY)
}
