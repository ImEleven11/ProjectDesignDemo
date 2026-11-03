<script setup lang="ts">
const modules = [
  'Vue 3 Web 应用',
  'Spring Boot API',
  'FastAPI 重建工作进程',
  'PostgreSQL 数据库',
]
</script>

<template>
  <main class="shell">
    <p class="eyebrow">ProjectDesignDemo</p>
    <h1>数字文博稀疏三维重建 Demo</h1>
    <p class="intro">基础设施已就绪。下一步将实现重建项目、图像上传和任务状态模型。</p>
    <ul>
      <li v-for="module in modules" :key="module">{{ module }}</li>
    </ul>
  </main>
</template>
