<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { api } from './api'
import type { PublicHeritage } from './types'

const ReconstructionViewer = defineAsyncComponent(() => import('./components/ReconstructionViewer.vue'))
const items = ref<PublicHeritage[]>([])
const selected = ref<PublicHeritage>()
const loading = ref(true)
const errorMessage = ref('')
const detailId = window.location.pathname.split('/').filter(Boolean)[1]
const modelUrl = computed(() => selected.value
  ? api.publicModelUrl(selected.value.id, selected.value.versionNumber)
  : undefined)
const resultKind = computed<'POINT_CLOUD' | 'SURFACE_MODEL'>(() =>
  selected.value?.format === 'GLB' ? 'SURFACE_MODEL' : 'POINT_CLOUD')

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date(value))
}

function formatBytes(value: number) {
  return value < 1024 * 1024 ? `${Math.round(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`
}

onMounted(async () => {
  try {
    if (detailId) selected.value = await api.getPublicHeritage(detailId)
    else items.value = await api.listPublicHeritage()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '公开展览加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="public-shell">
    <header class="public-header">
      <a href="/exhibits" class="public-brand"><span>文</span><strong>数智文博</strong><small>DIGITAL HERITAGE COLLECTION</small></a>
      <a href="/" class="console-link">管理工作台 ↗</a>
    </header>

    <main v-if="selected" class="exhibit-detail">
      <nav><a href="/exhibits">← 返回数字馆藏</a><span>{{ selected.collectionCode || selected.id.slice(0, 8) }}</span></nav>
      <section class="exhibit-hero">
        <div class="exhibit-copy">
          <p>{{ selected.heritageType }} · {{ selected.era || '年代待考' }}</p>
          <h1>{{ selected.name }}</h1>
          <blockquote>{{ selected.description || '通过多视角影像与三维重建技术保存的数字文物档案。' }}</blockquote>
          <dl>
            <div><dt>藏品编号</dt><dd>{{ selected.collectionCode || '—' }}</dd></div>
            <div><dt>年代</dt><dd>{{ selected.era || '—' }}</dd></div>
            <div><dt>材质</dt><dd>{{ selected.material || '—' }}</dd></div>
            <div><dt>尺寸</dt><dd>{{ selected.dimensions || '—' }}</dd></div>
            <div><dt>来源</dt><dd>{{ selected.provenance || '—' }}</dd></div>
          </dl>
        </div>
        <img :src="api.publicCoverUrl(selected.id)" :alt="`${selected.name}代表图片`" />
      </section>
      <section class="public-model">
        <div class="public-section-heading"><div><small>INTERACTIVE 3D</small><h2>三维数字藏品</h2></div><p>拖动旋转 · 滚轮缩放 · 右键平移</p></div>
        <ReconstructionViewer :result-url="modelUrl" :result-kind="resultKind" />
        <footer>
          <span>{{ selected.artifactName }} · v{{ selected.versionNumber }}</span>
          <span>{{ selected.pointCount.toLocaleString() }} 点 · {{ selected.faceCount.toLocaleString() }} 面 · {{ formatBytes(selected.sizeBytes) }}</span>
        </footer>
      </section>
      <p class="published-note">数字化发布于 {{ formatDate(selected.publishedAt) }}</p>
    </main>

    <main v-else class="collection-page">
      <section class="collection-intro"><p>UESTC · DIGITAL HERITAGE</p><h1>数字馆藏</h1><span>让文物以可交互的三维形态被看见、研究与传承。</span></section>
      <div v-if="items.length" class="collection-grid">
        <a v-for="item in items" :key="item.id" :href="`/exhibits/${item.id}`" class="collection-card">
          <img :src="api.publicCoverUrl(item.id)" :alt="item.name" />
          <div><small>{{ item.heritageType }} · {{ item.era || '年代待考' }}</small><h2>{{ item.name }}</h2><p>{{ item.description || '数字三维文物档案' }}</p><span>进入三维展览 →</span></div>
        </a>
      </div>
      <div v-else-if="!loading && !errorMessage" class="collection-empty">当前暂无已发布的数字藏品。</div>
      <div v-if="loading" class="collection-empty">正在加载数字馆藏…</div>
      <div v-if="errorMessage" class="collection-empty error">{{ errorMessage }}</div>
    </main>
  </div>
</template>
