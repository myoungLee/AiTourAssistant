/*
 * @author myoung
 */
import { defineStore } from 'pinia'

/**
 * 保存应用级基础状态，后续认证和行程状态会拆到独立 store。
 *
 * @author myoung
 */
export const useAppStore = defineStore('app', {
  state: () => ({
    appName: 'AI 旅游助手'
  })
})
