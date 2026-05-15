<!--
  @author myoung
-->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: '',
  nickname: ''
})

/**
 * 提交注册请求，成功后直接进入规划页。
 */
async function submit(): Promise<void> {
  submitting.value = true
  errorMessage.value = ''

  try {
    await authStore.register(form)
    await router.replace('/planner')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '注册失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-panel__intro">
        <p>创建账户</p>
        <h1>注册 AI 旅游助手</h1>
        <span>完成注册后即可保存历史行程、偏好设置和工具调用结果。</span>
      </div>
      <el-form class="auth-panel__form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="至少 3 位字符" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位字符" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="可选" />
        </el-form-item>
        <el-alert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" />
        <el-button type="primary" :loading="submitting" class="auth-panel__submit" @click="submit">
          注册
        </el-button>
        <el-button link type="primary" @click="router.push('/login')">
          已有账号，去登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>
