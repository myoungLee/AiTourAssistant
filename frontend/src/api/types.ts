/*
 * @author myoung
 */

/**
 * 后端普通 REST 接口统一响应结构。
 *
 * @author myoung
 */
export interface Result<T> {
  code: number
  msg?: string | null
  data: T
}

/**
 * 认证响应载荷。
 *
 * @author myoung
 */
export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userId: number
  username: string
  nickname: string
}

/**
 * 当前登录用户信息。
 *
 * @author myoung
 */
export interface CurrentUserResponse {
  id: number
  username: string
  nickname: string
  avatarUrl?: string | null
  phone?: string | null
  email?: string | null
}

/**
 * 用户画像响应。
 *
 * @author myoung
 */
export interface ProfileResponse {
  gender?: string | null
  ageRange?: string | null
  travelStyle?: string | null
  defaultBudgetLevel?: string | null
  preferredTransport?: string | null
  preferencesJson?: string | null
}

/**
 * 更新用户画像请求字段。
 *
 * @author myoung
 */
export interface UpdateProfileRequest {
  gender?: string | null
  ageRange?: string | null
  travelStyle?: string | null
  defaultBudgetLevel?: string | null
  preferredTransport?: string | null
  preferencesJson?: string | null
}

/**
 * 创建行程请求字段。
 *
 * @author myoung
 */
export interface CreateTripRequest {
  destination: string
  startDate: string
  days: number
  budget?: number | null
  peopleCount: number
  preferences?: string[]
  userInput?: string | null
}

/**
 * 行程概要响应。
 *
 * @author myoung
 */
export interface TripSummaryResponse {
  id: number
  title: string
  destination: string
  status: string
  totalBudget?: number | null
}

/**
 * 行程条目响应。
 *
 * @author myoung
 */
export interface TripItemResponse {
  timeSlot: string
  placeName: string
  placeType?: string | null
  address?: string | null
  durationMinutes?: number | null
  transportSuggestion?: string | null
  estimatedCost?: number | null
  reason?: string | null
}

/**
 * 每日行程响应。
 *
 * @author myoung
 */
export interface TripDayResponse {
  dayIndex: number
  date: string
  city: string
  weatherSummary?: string | null
  items: TripItemResponse[]
}

/**
 * 预算明细响应。
 *
 * @author myoung
 */
export interface BudgetResponse {
  hotelCost?: number | null
  foodCost?: number | null
  transportCost?: number | null
  ticketCost?: number | null
  otherCost?: number | null
}

/**
 * 行程详情响应。
 *
 * @author myoung
 */
export interface TripDetailResponse {
  id: number
  title: string
  summary?: string | null
  status: string
  totalBudget?: number | null
  days: TripDayResponse[]
  budget?: BudgetResponse | null
}

/**
 * 工具状态响应。
 *
 * @author myoung
 */
export interface ToolStatusResponse {
  mode: string
  tools: string[]
}

/**
 * SSE 事件名称。
 *
 * @author myoung
 */
export type StreamEventName = 'progress' | 'tool_result' | 'plan_snapshot' | 'ai_delta' | 'completed' | 'error'

/**
 * SSE 进度事件。
 *
 * @author myoung
 */
export interface ProgressEvent {
  step: string
  message: string
  percent: number
}

/**
 * SSE 工具结果事件。
 *
 * @author myoung
 */
export interface ToolResultEvent {
  tool: string
  summary: string
  data?: Record<string, unknown>
}

/**
 * SSE 行程快照事件。
 *
 * @author myoung
 */
export interface PlanSnapshotEvent {
  dayIndex: number
  items: TripItemResponse[]
}

/**
 * SSE AI 增量事件。
 *
 * @author myoung
 */
export interface AiDeltaEvent {
  text: string
}

/**
 * SSE 完成事件。
 *
 * @author myoung
 */
export interface CompletedEvent {
  planId: number
  status: string
}

/**
 * SSE 错误事件。
 *
 * @author myoung
 */
export interface ErrorEvent {
  code: string
  message: string
}

/**
 * SSE 解析结果，按事件名返回结构化数据。
 *
 * @author myoung
 */
export type StreamEvent =
  | { event: 'progress'; data: ProgressEvent }
  | { event: 'tool_result'; data: ToolResultEvent }
  | { event: 'plan_snapshot'; data: PlanSnapshotEvent }
  | { event: 'ai_delta'; data: AiDeltaEvent }
  | { event: 'completed'; data: CompletedEvent }
  | { event: 'error'; data: ErrorEvent }
