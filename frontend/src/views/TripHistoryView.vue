<!--
  @author myoung
-->
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

import { useTripStore } from '@/stores/trip'

const router = useRouter()
const tripStore = useTripStore()

/**
 * 页面加载后查询历史行程。
 */
onMounted(async () => {
  await tripStore.loadHistories()
})

/**
 * 打开指定行程详情页。
 */
function openDetail(id: number): void {
  void router.push(`/trips/${id}`)
}
</script>

<template>
  <section class="list-page">
    <el-card shadow="never">
      <template #header>
        <div class="section-header">
          <span>历史行程</span>
          <el-button type="primary" link @click="router.push('/planner')">新建规划</el-button>
        </div>
      </template>

      <el-table :data="tripStore.histories" v-loading="tripStore.loading" empty-text="暂无历史行程">
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="destination" label="目的地" min-width="140" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="totalBudget" label="预算" width="140" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>
