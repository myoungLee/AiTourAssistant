/*
 * @author myoung
 */
import { http } from './http'
import { formParams } from './form'
import type { CurrentUserResponse, ProfileResponse, UpdateProfileRequest } from './types'

/**
 * 当前用户与画像接口封装。
 *
 * @author myoung
 */
export const usersApi = {
  /**
   * 查询当前登录用户基础信息。
   */
  async currentUser(): Promise<CurrentUserResponse> {
    const response = await http.get<CurrentUserResponse>('/users/me')
    return response.data
  },

  /**
   * 更新当前登录用户画像。
   */
  async updateProfile(payload: UpdateProfileRequest): Promise<ProfileResponse> {
    const response = await http.put<ProfileResponse>('/users/me/profile', formParams(payload), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      }
    })
    return response.data
  }
}
