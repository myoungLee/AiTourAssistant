<!--
  @author myoung
-->
<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useTripStore } from '@/stores/trip'

const route = useRoute()
const router = useRouter()
const tripStore = useTripStore()

const tripId = computed(() => Number(route.params.id))

/**
 * 进入详情页后查询指定行程。
 */
onMounted(async () => {
  if (!Number.isNaN(tripId.value)) {
    await tripStore.loadDetail(tripId.value)
  }
})
</script>

<template>
  <section class="detail-page">
    <el-card shadow="never">
      <template #header>
        <div class="section-header">
          <span>{{ tripStore.currentTrip?.title || '行程详情' }}</span>
          <el-button type="primary" link @click="router.push('/trips')">返回列表</el-button>
        </div>
      </template>

      <div v-if="tripStore.currentTrip" class="detail-content">
        <p class="detail-summary">{{ tripStore.currentTrip.summary || '暂无 AI 摘要' }}</p>
        <div class="detail-meta">
          <el-tag>{{ tripStore.currentTrip.status }}</el-tag>
          <span>总预算：{{ tripStore.currentTrip.totalBudget ?? '-' }}</span>
        </div>
        <div class="detail-days">
          <el-card v-for="day in tripStore.currentTrip.days" :key="day.dayIndex" shadow="never">
            <template #header>
              第 {{ day.dayIndex }} 天 · {{ day.date }} · {{ day.city }}
            </template>
            <p class="detail-weather">{{ day.weatherSummary || '暂无天气摘要' }}</p>
            <ul class="snapshot-items">
              <li v-for="item in day.items" :key="`${day.dayIndex}-${item.timeSlot}-${item.placeName}`">
                <div>
                  <strong>{{ item.timeSlot }}</strong>
                  <span>{{ item.placeName }}</span>
                </div>
                <small>{{ item.reason || '无补充说明' }}</small>
              </li>
            </ul>
          </el-card>
        </div>
      </div>

      <el-empty v-else description="未找到行程详情" />
    </el-card>
  </section>
</template>
