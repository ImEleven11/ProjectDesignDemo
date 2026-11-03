<script setup lang="ts">
const modules = [
  '文博项目创建与查询 API',
  '项目图像上传与元数据管理',
  'COLMAP 稀疏重建任务队列',
  'PostgreSQL 持久化与 Flyway 迁移',
]
</script>

<template>
  <main class="shell">
    <p class="eyebrow">ProjectDesignDemo</p>
    <h1>数字文博稀疏三维重建 Demo</h1>
    <p class="intro">业务基础 API 已就绪，可创建项目、上传图像并提交稀疏重建任务。</p>
    <ul>
      <li v-for="module in modules" :key="module">{{ module }}</li>
    </ul>
  </main>
</template>
