<!--
  @author myoung
-->
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useTripStore } from '@/stores/trip'

const router = useRouter()
const tripStore = useTripStore()
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  destination: '',
  startDate: '',
  days: 3,
  budget: 3000,
  peopleCount: 2,
  preferences: [] as string[],
  userInput: ''
})

const preferenceOptions = ['美食', '文化', '亲子', '夜景', '自然', '轻松']

/**
 * 将流式 AI 文本拆成段落，便于页面展示。
 */
const aiParagraphs = computed(() => tripStore.aiSummary.split('\n').filter(Boolean))

/**
 * 发起行程流式生成，并在完成后跳转详情页。
 */
async function submit(): Promise<void> {
  submitting.value = true
  errorMessage.value = ''

  try {
    const planId = await tripStore.streamPlan({
      destination: form.destination,
      startDate: form.startDate,
      days: form.days,
      budget: form.budget,
      peopleCount: form.peopleCount,
      preferences: form.preferences,
      userInput: form.userInput
    })

    if (planId) {
      await router.push(`/trips/${planId}`)
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '生成失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="workspace-grid">
    <el-card shadow="never">
      <template #header>
        <div class="section-header">
          <span>出行需求</span>
          <el-tag type="primary">字段参数提交</el-tag>
        </div>
      </template>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="目的地">
          <el-input v-model="form.destination" placeholder="例如：成都" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="出发日期">
            <el-date-picker v-model="form.startDate" value-format="YYYY-MM-DD" type="date" class="w-full" />
          </el-form-item>
          <el-form-item label="出行天数">
            <el-input-number v-model="form.days" :min="1" :max="15" class="w-full" />
          </el-form-item>
          <el-form-item label="总预算">
            <el-input-number v-model="form.budget" :min="0" :step="100" class="w-full" />
          </el-form-item>
          <el-form-item label="人数">
            <el-input-number v-model="form.peopleCount" :min="1" :max="20" class="w-full" />
          </el-form-item>
        </div>
        <el-form-item label="偏好标签">
          <el-select v-model="form.preferences" multiple collapse-tags collapse-tags-tooltip placeholder="选择偏好">
            <el-option v-for="item in preferenceOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="补充需求">
          <el-input v-model="form.userInput" type="textarea" :rows="4" placeholder="例如：想吃火锅，第二天节奏轻一点" />
        </el-form-item>
        <el-alert v-if="errorMessage || tripStore.streamError" type="error" :closable="false" :title="errorMessage || tripStore.streamError" />
        <el-button type="primary" :loading="submitting || tripStore.generating" @click="submit">
          开始生成
        </el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="section-header">
          <span>流式生成过程</span>
          <el-tag v-if="tripStore.progress" type="success">{{ tripStore.progress.percent }}%</el-tag>
        </div>
      </template>
      <div class="stream-panel">
        <div v-if="tripStore.progress" class="stream-progress">
          <strong>{{ tripStore.progress.message }}</strong>
          <el-progress :percentage="tripStore.progress.percent" />
        </div>

        <section>
          <h3>工具结果</h3>
          <el-timeline v-if="tripStore.toolResults.length">
            <el-timeline-item v-for="item in tripStore.toolResults" :key="`${item.tool}-${item.summary}`" :timestamp="item.tool">
              <div>{{ item.summary }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="尚未收到工具结果" />
        </section>

        <section>
          <h3>行程快照</h3>
          <div v-if="tripStore.snapshots.length" class="snapshot-list">
            <el-card v-for="snapshot in tripStore.snapshots" :key="snapshot.dayIndex" shadow="never">
              <template #header>第 {{ snapshot.dayIndex }} 天</template>
              <ul class="snapshot-items">
                <li v-for="item in snapshot.items" :key="`${snapshot.dayIndex}-${item.timeSlot}-${item.placeName}`">
                  <strong>{{ item.timeSlot }}</strong>
                  <span>{{ item.placeName }}</span>
                </li>
              </ul>
            </el-card>
          </div>
          <el-empty v-else description="尚未收到每日快照" />
        </section>

        <section>
          <h3>AI 摘要</h3>
          <p v-for="(paragraph, index) in aiParagraphs" :key="index" class="summary-line">{{ paragraph }}</p>
          <el-empty v-if="!tripStore.aiSummary" description="尚未收到 AI 文本增量" />
        </section>
      </div>
    </el-card>
  </section>
</template>
