<!--
  @author myoung
-->
<script setup lang="ts">
import { reactive, ref } from 'vue'

import { usersApi } from '@/api/users'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const saving = ref(false)
const successMessage = ref('')
const errorMessage = ref('')

const form = reactive({
  gender: '',
  ageRange: '',
  travelStyle: '',
  defaultBudgetLevel: '',
  preferredTransport: '',
  preferencesJson: ''
})

/**
 * 提交用户画像更新请求。
 */
async function submit(): Promise<void> {
  saving.value = true
  successMessage.value = ''
  errorMessage.value = ''

  try {
    await usersApi.updateProfile(form)
    successMessage.value = '画像已更新'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '更新失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="profile-page">
    <el-card shadow="never">
      <template #header>
        <div class="section-header">
          <span>用户资料</span>
          <el-tag type="info">{{ authStore.currentUser?.nickname || authStore.currentUser?.username || '当前用户' }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="2" border class="profile-user-card">
        <el-descriptions-item label="用户名">{{ authStore.currentUser?.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ authStore.currentUser?.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机">{{ authStore.currentUser?.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ authStore.currentUser?.email || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <el-form label-position="top" @submit.prevent="submit">
        <div class="form-grid">
          <el-form-item label="性别">
            <el-input v-model="form.gender" />
          </el-form-item>
          <el-form-item label="年龄段">
            <el-input v-model="form.ageRange" />
          </el-form-item>
          <el-form-item label="旅行风格">
            <el-input v-model="form.travelStyle" />
          </el-form-item>
          <el-form-item label="默认预算等级">
            <el-input v-model="form.defaultBudgetLevel" />
          </el-form-item>
          <el-form-item label="偏好交通方式">
            <el-input v-model="form.preferredTransport" />
          </el-form-item>
        </div>
        <el-form-item label="偏好 JSON">
          <el-input v-model="form.preferencesJson" type="textarea" :rows="4" placeholder='例如：{"food":["火锅"],"pace":"轻松"}' />
        </el-form-item>
        <el-alert v-if="successMessage" type="success" :closable="false" :title="successMessage" />
        <el-alert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" />
        <el-button type="primary" :loading="saving" @click="submit">保存画像</el-button>
      </el-form>
    </el-card>
  </section>
</template>
