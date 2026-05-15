/*
 * @author myoung
 */
import type { RouterHistory } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'

import { ACCESS_TOKEN_KEY } from '@/api/http'
import AppLayout from '@/layouts/AppLayout.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import ProfileView from '@/views/ProfileView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ToolStatusView from '@/views/ToolStatusView.vue'
import TripDetailView from '@/views/TripDetailView.vue'
import TripHistoryView from '@/views/TripHistoryView.vue'
import TripPlannerView from '@/views/TripPlannerView.vue'

const protectedNames = new Set([
  'trip-planner',
  'trip-history',
  'trip-detail',
  'profile',
  'tool-status'
])

/**
 * 创建前端路由实例，测试场景可注入 memory history。
 *
 * @author myoung
 */
export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      {
        path: '/',
        redirect: '/home'
      },
      {
        path: '/home',
        name: 'home',
        component: HomeView
      },
      {
        path: '/login',
        name: 'login',
        component: LoginView
      },
      {
        path: '/register',
        name: 'register',
        component: RegisterView
      },
      {
        path: '/',
        component: AppLayout,
        children: [
          {
            path: '/planner',
            name: 'trip-planner',
            component: TripPlannerView
          },
          {
            path: '/trips',
            name: 'trip-history',
            component: TripHistoryView
          },
          {
            path: '/trips/:id',
            name: 'trip-detail',
            component: TripDetailView
          },
          {
            path: '/profile',
            name: 'profile',
            component: ProfileView
          },
          {
            path: '/tools',
            name: 'tool-status',
            component: ToolStatusView
          }
        ]
      }
    ]
  })

  router.beforeEach((to) => {
    if (!protectedNames.has(String(to.name))) {
      return true
    }

    if (readAccessToken()) {
      return true
    }

    return {
      name: 'login',
      query: {
        redirect: to.fullPath
      }
    }
  })

  return router
}

/**
 * 读取本地 token，供路由守卫判断业务页访问权限。
 */
function readAccessToken(): string | null {
  if (typeof window === 'undefined') {
    return null
  }
  return window.localStorage.getItem(ACCESS_TOKEN_KEY)
}

const router = createAppRouter()

export default router
