<!-- @author myoung -->

# AI 旅游助手前端核心页面 Implementation Plan

> **历史计划，停止按此执行。** 本文保留为 2026-05-14 阶段拆分记录，代码片段和配置不再作为后续实施依据。当前项目已调整为 Spring Boot + Spring AI + `Result<T>` + `service.impl` 架构，后续执行以 `docs/superpowers/plans/2026-05-15-ai-tour-assistant-current-architecture-plan.md` 和根目录 `AGENTS.md` 为准。
>
> 旧内容中可能包含前端直接读取 Axios 原始响应、JSON RequestBody 提交和旧接口契约等过期约束；后续前端必须适配 `Result<T>` 解包和字段参数提交。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Vue 3 前端核心体验：登录注册、用户资料、旅行需求表单、SSE 流式生成面板、历史行程列表、行程详情和工具状态页。

**Architecture:** 前端使用 Pinia 管理认证态和当前行程生成状态。普通接口使用 Axios，流式接口使用 `fetch + ReadableStream`。页面采用 Element Plus，优先做清晰、实用的助手型应用界面。

**Tech Stack:** Vue 3、Vite、Element Plus、Pinia、Vue Router、Axios、ReadableStream、Vitest。

---

## 前置条件

- 基础工程计划已完成。
- 认证、行程、AI/MCP、SSE 后端计划已完成。

## 文件结构规划

```text
frontend/src/api/auth.ts
frontend/src/api/trips.ts
frontend/src/api/tools.ts
frontend/src/api/stream.ts
frontend/src/stores/auth.ts
frontend/src/stores/trip.ts
frontend/src/router/index.ts
frontend/src/layouts/AppLayout.vue
frontend/src/views/LoginView.vue
frontend/src/views/RegisterView.vue
frontend/src/views/HomeView.vue
frontend/src/views/PlansView.vue
frontend/src/views/PlanDetailView.vue
frontend/src/views/ProfileView.vue
frontend/src/views/ToolsView.vue
frontend/src/components/TripRequestForm.vue
frontend/src/components/StreamingPlanPanel.vue
frontend/src/components/ProgressTimeline.vue
frontend/src/components/DayPlanTabs.vue
frontend/src/components/BudgetBreakdown.vue
frontend/src/components/WeatherAlert.vue
frontend/src/components/ToolStatusPanel.vue
frontend/src/types/api.ts
frontend/src/types/sse.ts
```

## Task 1: 类型、HTTP 和认证状态

**Files:**

- Create: `frontend/src/types/api.ts`
- Create: `frontend/src/types/sse.ts`
- Modify: `frontend/src/api/http.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/stores/auth.test.ts`

- [ ] **Step 1: 创建 API 类型**

```ts
export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userId: number
  username: string
  nickname: string
}

export interface CurrentUser {
  id: number
  username: string
  nickname: string
  avatarUrl?: string
  phone?: string
  email?: string
}

export interface TripRequestPayload {
  destination: string
  startDate: string
  days: number
  budget?: number
  peopleCount: number
  preferences: string[]
  userInput?: string
}

export interface TripSummary {
  id: number
  title: string
  destination: string
  status: string
  totalBudget?: number
}
```

- [ ] **Step 2: 创建 SSE 类型**

```ts
export type SseEventName = 'progress' | 'ai_delta' | 'tool_result' | 'plan_snapshot' | 'completed' | 'error'

export interface ProgressEvent {
  step: string
  message: string
  percent: number
}

export interface AiDeltaEvent {
  text: string
}

export interface CompletedEvent {
  planId: number
  status: string
}

export interface ParsedSseEvent {
  event: SseEventName
  data: unknown
}
```

- [ ] **Step 3: 修改 Axios 客户端注入 token**

```ts
import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('aitour_access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

- [ ] **Step 4: 创建认证 API**

```ts
import { http } from './http'
import type { AuthResponse, CurrentUser } from '@/types/api'

export function registerApi(payload: { username: string; password: string; nickname?: string }) {
  return http.post<AuthResponse>('/auth/register', payload)
}

export function loginApi(payload: { username: string; password: string }) {
  return http.post<AuthResponse>('/auth/login', payload)
}

export function currentUserApi() {
  return http.get<CurrentUser>('/users/me')
}
```

- [ ] **Step 5: 创建认证 Store**

```ts
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { currentUserApi, loginApi, registerApi } from '@/api/auth'
import type { CurrentUser } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('aitour_access_token') || '')
  const user = ref<CurrentUser | null>(null)
  const isLoggedIn = computed(() => Boolean(token.value))

  function setAuth(accessToken: string) {
    token.value = accessToken
    localStorage.setItem('aitour_access_token', accessToken)
  }

  async function login(username: string, password: string) {
    const { data } = await loginApi({ username, password })
    setAuth(data.accessToken)
    user.value = { id: data.userId, username: data.username, nickname: data.nickname }
  }

  async function register(username: string, password: string, nickname?: string) {
    const { data } = await registerApi({ username, password, nickname })
    setAuth(data.accessToken)
    user.value = { id: data.userId, username: data.username, nickname: data.nickname }
  }

  async function loadCurrentUser() {
    const { data } = await currentUserApi()
    user.value = data
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('aitour_access_token')
  }

  return { token, user, isLoggedIn, login, register, loadCurrentUser, logout }
})
```

- [ ] **Step 6: 测试认证 Store 初始状态**

```ts
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useAuthStore } from './auth'

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('starts logged out', () => {
    const store = useAuthStore()

    expect(store.isLoggedIn).toBe(false)
  })
})
```

## Task 2: 路由和应用布局

**Files:**

- Modify: `frontend/src/router/index.ts`
- Create: `frontend/src/layouts/AppLayout.vue`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: 创建布局组件**

```vue
<template>
  <el-container class="app-shell">
    <el-aside width="220px" class="sidebar">
      <h1>AI 旅游助手</h1>
      <el-menu router :default-active="$route.path">
        <el-menu-item index="/home">生成行程</el-menu-item>
        <el-menu-item index="/plans">历史行程</el-menu-item>
        <el-menu-item index="/profile">个人资料</el-menu-item>
        <el-menu-item index="/tools">工具状态</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">智能旅行规划工作台</el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.sidebar {
  border-right: 1px solid #e5e7eb;
  background: #ffffff;
}

.sidebar h1 {
  margin: 0;
  padding: 20px;
  font-size: 18px;
}

.topbar {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}
</style>
```

- [ ] **Step 2: 更新路由**

```ts
import { createRouter, createWebHistory } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/register', component: RegisterView },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/home' },
        { path: 'home', component: HomeView },
        { path: 'plans', component: () => import('@/views/PlansView.vue') },
        { path: 'plans/:id', component: () => import('@/views/PlanDetailView.vue') },
        { path: 'profile', component: () => import('@/views/ProfileView.vue') },
        { path: 'tools', component: () => import('@/views/ToolsView.vue') }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const publicPages = ['/login', '/register']
  const token = localStorage.getItem('aitour_access_token')
  if (!publicPages.includes(to.path) && !token) {
    return '/login'
  }
  return true
})

export default router
```

- [ ] **Step 3: 保持 App 只渲染路由**

```vue
<template>
  <el-config-provider>
    <router-view />
  </el-config-provider>
</template>
```

## Task 3: 登录和注册页面

**Files:**

- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/RegisterView.vue`
- Create: `frontend/src/views/LoginView.test.ts`

- [ ] **Step 1: 创建登录页**

```vue
<template>
  <main class="auth-page">
    <el-card class="auth-card" shadow="never">
      <h1>登录 AI 旅游助手</h1>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading">登录</el-button>
        <el-button text @click="$router.push('/register')">注册账号</el-button>
      </el-form>
    </el-card>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    await router.push('/home')
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 2: 创建注册页**

```vue
<template>
  <main class="auth-page">
    <el-card class="auth-card" shadow="never">
      <h1>注册账号</h1>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading">注册并登录</el-button>
      </el-form>
    </el-card>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', password: '' })

async function submit() {
  loading.value = true
  try {
    await auth.register(form.username, form.password, form.nickname)
    await router.push('/home')
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 3: 增加认证页面样式**

在 `frontend/src/styles/main.css` 追加：

```css
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f6f8fb;
}

.auth-card {
  width: min(420px, calc(100vw - 32px));
  border-radius: 8px;
}
```

## Task 4: 流式请求工具和 Home 页面

**Files:**

- Create: `frontend/src/api/stream.ts`
- Create: `frontend/src/api/trips.ts`
- Create: `frontend/src/stores/trip.ts`
- Create: `frontend/src/components/TripRequestForm.vue`
- Create: `frontend/src/components/StreamingPlanPanel.vue`
- Modify: `frontend/src/views/HomeView.vue`

- [ ] **Step 1: 创建 SSE 解析工具**

```ts
import type { ParsedSseEvent, SseEventName } from '@/types/sse'

export async function streamPost<TBody>(
  url: string,
  body: TBody,
  onEvent: (event: ParsedSseEvent) => void
) {
  const token = localStorage.getItem('aitour_access_token')
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(body)
  })

  if (!response.body) {
    throw new Error('浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() || ''
    for (const chunk of chunks) {
      const eventLine = chunk.split('\n').find((line) => line.startsWith('event:'))
      const dataLine = chunk.split('\n').find((line) => line.startsWith('data:'))
      if (eventLine && dataLine) {
        onEvent({
          event: eventLine.replace('event:', '').trim() as SseEventName,
          data: JSON.parse(dataLine.replace('data:', '').trim())
        })
      }
    }
  }
}
```

- [ ] **Step 2: 创建 Trip API**

```ts
import { http } from './http'
import type { TripRequestPayload, TripSummary } from '@/types/api'

export function listTripsApi() {
  return http.get<TripSummary[]>('/trips')
}

export function getTripApi(id: string | number) {
  return http.get(`/trips/${id}`)
}

export type { TripRequestPayload }
```

- [ ] **Step 3: 创建 Trip Store**

```ts
import { defineStore } from 'pinia'
import { ref } from 'vue'

import { streamPost } from '@/api/stream'
import type { TripRequestPayload } from '@/types/api'
import type { ParsedSseEvent } from '@/types/sse'

export const useTripStore = defineStore('trip', () => {
  const generating = ref(false)
  const progressText = ref('')
  const percent = ref(0)
  const aiText = ref('')
  const planId = ref<number | null>(null)
  const toolSummaries = ref<string[]>([])

  async function streamPlan(payload: TripRequestPayload) {
    generating.value = true
    progressText.value = '准备生成行程'
    percent.value = 0
    aiText.value = ''
    toolSummaries.value = []
    await streamPost('/api/trips/stream-plan', payload, handleEvent)
    generating.value = false
  }

  function handleEvent(event: ParsedSseEvent) {
    if (event.event === 'progress') {
      const data = event.data as { message: string; percent: number }
      progressText.value = data.message
      percent.value = data.percent
    }
    if (event.event === 'ai_delta') {
      const data = event.data as { text: string }
      aiText.value += data.text
    }
    if (event.event === 'tool_result') {
      const data = event.data as { summary: string }
      toolSummaries.value.push(data.summary)
    }
    if (event.event === 'completed') {
      const data = event.data as { planId: number }
      planId.value = data.planId
      percent.value = 100
      progressText.value = '行程生成完成'
    }
  }

  return { generating, progressText, percent, aiText, planId, toolSummaries, streamPlan }
})
```

- [ ] **Step 4: 创建需求表单**

```vue
<template>
  <el-card shadow="never">
    <template #header>出行需求</template>
    <el-form :model="form" label-position="top">
      <el-form-item label="目的地">
        <el-input v-model="form.destination" />
      </el-form-item>
      <el-form-item label="出发日期">
        <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="天数">
        <el-input-number v-model="form.days" :min="1" :max="15" />
      </el-form-item>
      <el-form-item label="预算">
        <el-input-number v-model="form.budget" :min="0" />
      </el-form-item>
      <el-form-item label="人数">
        <el-input-number v-model="form.peopleCount" :min="1" :max="20" />
      </el-form-item>
      <el-form-item label="偏好">
        <el-checkbox-group v-model="form.preferences">
          <el-checkbox-button label="美食" />
          <el-checkbox-button label="历史" />
          <el-checkbox-button label="亲子" />
          <el-checkbox-button label="摄影" />
          <el-checkbox-button label="轻松" />
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="补充说明">
        <el-input v-model="form.userInput" type="textarea" :rows="4" />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="$emit('submit', form)">生成行程</el-button>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { reactive } from 'vue'

defineProps<{ loading: boolean }>()
defineEmits<{ submit: [payload: typeof form] }>()

const form = reactive({
  destination: '成都',
  startDate: '2099-06-01',
  days: 3,
  budget: 3000,
  peopleCount: 2,
  preferences: ['美食', '轻松'],
  userInput: '想吃火锅，不想太赶'
})
</script>
```

- [ ] **Step 5: 创建流式面板**

```vue
<template>
  <el-card shadow="never">
    <template #header>生成过程</template>
    <el-progress :percentage="percent" />
    <p class="progress-text">{{ progressText || '等待提交需求' }}</p>
    <el-timeline>
      <el-timeline-item v-for="item in toolSummaries" :key="item">{{ item }}</el-timeline-item>
    </el-timeline>
    <section class="ai-output">
      {{ aiText || 'AI 说明会在这里流式显示。' }}
    </section>
  </el-card>
</template>

<script setup lang="ts">
defineProps<{
  percent: number
  progressText: string
  aiText: string
  toolSummaries: string[]
}>()
</script>

<style scoped>
.progress-text {
  color: #667085;
}

.ai-output {
  min-height: 180px;
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  white-space: pre-wrap;
  line-height: 1.7;
}
</style>
```

- [ ] **Step 6: 更新 Home 页面**

```vue
<template>
  <div class="home-grid">
    <TripRequestForm :loading="trip.generating" @submit="trip.streamPlan" />
    <StreamingPlanPanel
      :percent="trip.percent"
      :progress-text="trip.progressText"
      :ai-text="trip.aiText"
      :tool-summaries="trip.toolSummaries"
    />
  </div>
</template>

<script setup lang="ts">
import StreamingPlanPanel from '@/components/StreamingPlanPanel.vue'
import TripRequestForm from '@/components/TripRequestForm.vue'
import { useTripStore } from '@/stores/trip'

const trip = useTripStore()
</script>

<style scoped>
.home-grid {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 20px;
}

@media (max-width: 900px) {
  .home-grid {
    grid-template-columns: 1fr;
  }
}
</style>
```

## Task 5: 历史行程、详情、资料和工具页面

**Files:**

- Create: `frontend/src/views/PlansView.vue`
- Create: `frontend/src/views/PlanDetailView.vue`
- Create: `frontend/src/views/ProfileView.vue`
- Create: `frontend/src/views/ToolsView.vue`
- Create: `frontend/src/api/tools.ts`

- [ ] **Step 1: 创建历史行程页面**

```vue
<template>
  <el-card shadow="never">
    <template #header>历史行程</template>
    <el-table :data="plans">
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="totalBudget" label="预算" width="120" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button text @click="$router.push(`/plans/${row.id}`)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { listTripsApi } from '@/api/trips'
import type { TripSummary } from '@/types/api'

const plans = ref<TripSummary[]>([])

onMounted(async () => {
  const { data } = await listTripsApi()
  plans.value = data
})
</script>
```

- [ ] **Step 2: 创建行程详情页**

```vue
<template>
  <el-card shadow="never">
    <template #header>行程详情</template>
    <pre>{{ detail }}</pre>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getTripApi } from '@/api/trips'

const route = useRoute()
const detail = ref<unknown>(null)

onMounted(async () => {
  const { data } = await getTripApi(String(route.params.id))
  detail.value = data
})
</script>
```

- [ ] **Step 3: 创建资料页**

```vue
<template>
  <el-card shadow="never">
    <template #header>个人资料</template>
    <el-descriptions v-if="auth.user" border>
      <el-descriptions-item label="用户名">{{ auth.user.username }}</el-descriptions-item>
      <el-descriptions-item label="昵称">{{ auth.user.nickname }}</el-descriptions-item>
      <el-descriptions-item label="邮箱">{{ auth.user.email || '未填写' }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'

import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

onMounted(() => {
  auth.loadCurrentUser()
})
</script>
```

- [ ] **Step 4: 创建工具 API 和工具页**

`api/tools.ts`：

```ts
import { http } from './http'

export function toolStatusApi() {
  return http.get<{ mode: string; tools: string[] }>('/tools/status')
}
```

`ToolsView.vue`：

```vue
<template>
  <el-card shadow="never">
    <template #header>工具状态</template>
    <el-alert title="当前工具模式" :description="mode" type="success" show-icon />
    <el-space wrap class="tool-list">
      <el-tag v-for="tool in tools" :key="tool">{{ tool }}</el-tag>
    </el-space>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { toolStatusApi } from '@/api/tools'

const mode = ref('')
const tools = ref<string[]>([])

onMounted(async () => {
  const { data } = await toolStatusApi()
  mode.value = data.mode
  tools.value = data.tools
})
</script>

<style scoped>
.tool-list {
  margin-top: 16px;
}
</style>
```

## Task 6: 前端验证和提交

**Files:**

- Modify: `README.md`

- [ ] **Step 1: 运行前端测试**

Run:

```bash
cd frontend
npm run test
```

Expected:

```text
passed
```

- [ ] **Step 2: 运行前端构建**

Run:

```bash
cd frontend
npm run build
```

Expected:

```text
dist
```

- [ ] **Step 3: 在 README 增加前端页面说明**

````markdown
## 前端页面

- `/login` 登录
- `/register` 注册
- `/home` 流式生成行程
- `/plans` 历史行程
- `/plans/:id` 行程详情
- `/profile` 用户资料
- `/tools` 工具状态
````

- [ ] **Step 4: 提交前端核心页面**

Run:

```bash
git add frontend README.md
git commit -m "feat: add frontend trip workflow"
```

Expected:

```text
feat: add frontend trip workflow
```

## 自检清单

- 登录注册页面可访问。
- `/home` 可以提交需求并消费 SSE 事件。
- 历史行程、详情、资料、工具状态页面都有路由。
- token 通过 Axios 拦截器和流式请求工具传递。
- 前端测试和构建通过。
