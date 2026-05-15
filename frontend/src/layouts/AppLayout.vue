<!--
  @author myoung
-->
<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

/**
 * 根据当前用户信息展示导航标题。
 */
const profileName = computed(() => authStore.currentUser?.nickname || authStore.currentUser?.username || '未登录')

/**
 * 首次进入业务壳层时补拉用户信息，支持刷新页面后的状态恢复。
 */
onMounted(async () => {
  if (authStore.isAuthenticated && !authStore.currentUser) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      await router.replace('/login')
    }
  }
})

/**
 * 执行退出并跳回登录页。
 */
function logout(): void {
  authStore.logout()
  void router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <el-container class="app-shell__container">
      <el-aside class="app-shell__aside" width="220px">
        <div class="app-shell__brand">
          <p>AI Tour Assistant</p>
          <h1>AI 旅游助手</h1>
        </div>
        <el-menu class="app-shell__menu" :default-active="$route.path" router>
          <el-menu-item index="/planner">行程规划</el-menu-item>
          <el-menu-item index="/trips">历史行程</el-menu-item>
          <el-menu-item index="/profile">用户资料</el-menu-item>
          <el-menu-item index="/tools">工具状态</el-menu-item>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="app-shell__header">
          <div>
            <h2>{{ $route.meta.title || '工作台' }}</h2>
            <p>围绕出行需求生成、查看和调整智能行程。</p>
          </div>
          <div class="app-shell__header-actions">
            <span>{{ profileName }}</span>
            <el-button link type="primary" @click="logout">退出</el-button>
          </div>
        </el-header>
        <el-main class="app-shell__main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>
