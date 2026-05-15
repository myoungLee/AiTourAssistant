<!--
  @author myoung
-->
<template>
  <main class="home-page">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">AI Tour Assistant</p>
        <h1>AI 旅游助手</h1>
        <p class="summary">
          输入目的地、预算、天数和旅行偏好，系统将生成包含景点、天气、路线和预算的智能行程。
        </p>
        <div class="hero-actions">
          <el-button type="primary" @click="$router.push('/login')">登录开始使用</el-button>
          <el-button @click="$router.push('/register')">注册账号</el-button>
        </div>
      </div>

      <el-card class="status-card" shadow="never">
        <template #header>
          <span>当前能力</span>
        </template>
        <div class="capability-list">
          <el-tag type="success">统一 Result 响应</el-tag>
          <el-tag type="success">登录鉴权</el-tag>
          <el-tag type="success">SSE 流式生成</el-tag>
          <el-tag type="success">历史行程</el-tag>
          <el-tag type="success">工具状态</el-tag>
        </div>
        <p>进入系统后可发起行程规划、查看历史详情、维护画像并观察 MCP 工具调用状态。</p>
      </el-card>
    </section>
  </main>
</template>
