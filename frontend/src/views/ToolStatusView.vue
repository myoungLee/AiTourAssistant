<!--
  @author myoung
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { toolsApi } from '@/api/tools'
import type { ToolStatusResponse } from '@/api/types'

const loading = ref(false)
const toolStatus = ref<ToolStatusResponse | null>(null)
const errorMessage = ref('')

/**
 * 查询当前后端 MCP 工具状态。
 */
onMounted(async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    toolStatus.value = await toolsApi.status()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '查询失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="tool-page">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="section-header">
          <span>工具状态</span>
          <el-tag v-if="toolStatus" type="success">{{ toolStatus.mode }}</el-tag>
        </div>
      </template>

      <el-alert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" />
      <div v-else-if="toolStatus" class="tool-grid">
        <el-card v-for="tool in toolStatus.tools" :key="tool" shadow="never">
          <strong>{{ tool }}</strong>
          <p>当前可通过 MCP 风格工具链参与行程生成。</p>
        </el-card>
      </div>
      <el-empty v-else description="暂无工具信息" />
    </el-card>
  </section>
</template>
