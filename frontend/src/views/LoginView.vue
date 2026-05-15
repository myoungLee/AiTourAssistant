<!--
  @author myoung
-->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: ''
})

/**
 * 提交登录请求，并在成功后回跳原始目标页。
 */
async function submit(): Promise<void> {
  submitting.value = true
  errorMessage.value = ''

  try {
    await authStore.login(form)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/planner'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-panel__intro">
        <p>欢迎回来</p>
        <h1>登录 AI 旅游助手</h1>
        <span>先登录，再发起出行规划、查看历史和维护个人偏好。</span>
      </div>
      <el-form class="auth-panel__form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-alert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" />
        <el-button type="primary" :loading="submitting" class="auth-panel__submit" @click="submit">
          登录
        </el-button>
        <el-button link type="primary" @click="router.push('/register')">
          还没有账号，去注册
        </el-button>
      </el-form>
    </section>
  </main>
</template>
