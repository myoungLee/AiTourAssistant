/*
 * @author myoung
 */
import { defineStore } from 'pinia'

import { streamApi, consumeEventStream } from '@/api/stream'
import { tripsApi } from '@/api/trips'
import type {
  CreateTripRequest,
  PlanSnapshotEvent,
  StreamEvent,
  ToolResultEvent,
  TripDetailResponse,
  TripSummaryResponse
} from '@/api/types'

interface PlannerProgress {
  step: string
  message: string
  percent: number
}

/**
 * 维护行程列表、详情和流式生成状态。
 *
 * @author myoung
 */
export const useTripStore = defineStore('trip', {
  state: () => ({
    loading: false,
    generating: false,
    histories: [] as TripSummaryResponse[],
    currentTrip: null as TripDetailResponse | null,
    draftPlanId: null as number | null,
    progress: null as PlannerProgress | null,
    toolResults: [] as ToolResultEvent[],
    snapshots: [] as PlanSnapshotEvent[],
    aiSummary: '',
    streamStatus: '',
    streamError: ''
  }),
  actions: {
    /**
     * 查询当前用户历史行程列表。
     */
    async loadHistories(): Promise<void> {
      this.loading = true
      try {
        this.histories = await tripsApi.list()
      } finally {
        this.loading = false
      }
    },

    /**
     * 查询指定行程详情。
     */
    async loadDetail(id: number): Promise<void> {
      this.loading = true
      try {
        this.currentTrip = await tripsApi.detail(id)
      } finally {
        this.loading = false
      }
    },

    /**
     * 创建草稿并启动流式行程生成。
     */
    async streamPlan(payload: CreateTripRequest): Promise<number | null> {
      this.resetStreamState()
      this.generating = true

      try {
        const draft = await tripsApi.createDraft(payload)
        this.draftPlanId = draft.planId

        const response = await streamApi.streamPlan(payload)
        await consumeEventStream(response, (event) => {
          this.applyStreamEvent(event)
        })

        if (this.currentTrip?.id) {
          return this.currentTrip.id
        }

        if (this.draftPlanId) {
          await this.loadDetail(this.draftPlanId)
          return this.draftPlanId
        }

        return null
      } finally {
        this.generating = false
      }
    },

    /**
     * 根据事件名更新前端流式状态。
     */
    applyStreamEvent(event: StreamEvent): void {
      if (event.event === 'progress') {
        this.progress = event.data
        return
      }

      if (event.event === 'tool_result') {
        this.toolResults.push(event.data)
        return
      }

      if (event.event === 'plan_snapshot') {
        this.snapshots = [...this.snapshots.filter((item) => item.dayIndex !== event.data.dayIndex), event.data]
          .sort((left, right) => left.dayIndex - right.dayIndex)
        return
      }

      if (event.event === 'ai_delta') {
        this.aiSummary += event.data.text
        return
      }

      if (event.event === 'completed') {
        this.streamStatus = event.data.status
        this.draftPlanId = event.data.planId
        void this.loadDetail(event.data.planId)
        return
      }

      this.streamError = event.data.message
    },

    /**
     * 重置本次流式生成的中间状态，避免不同请求间相互污染。
     */
    resetStreamState(): void {
      this.draftPlanId = null
      this.progress = null
      this.toolResults = []
      this.snapshots = []
      this.aiSummary = ''
      this.streamStatus = ''
      this.streamError = ''
    }
  }
})
