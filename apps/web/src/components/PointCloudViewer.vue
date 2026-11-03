<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { PLYLoader } from 'three/examples/jsm/loaders/PLYLoader.js'

const props = defineProps<{ resultUrl?: string }>()

const container = ref<HTMLDivElement>()
const loading = ref(false)
const viewerError = ref('')
const pointCount = ref(0)

let renderer: THREE.WebGLRenderer | undefined
let scene: THREE.Scene | undefined
let camera: THREE.PerspectiveCamera | undefined
let controls: OrbitControls | undefined
let cloud: THREE.Points | undefined
let resizeObserver: ResizeObserver | undefined
let animationFrame = 0

function initialize() {
  if (!container.value) return
  scene = new THREE.Scene()
  scene.background = new THREE.Color('#101b1d')
  scene.fog = new THREE.FogExp2('#101b1d', 0.035)
  camera = new THREE.PerspectiveCamera(42, 1, 0.01, 1000)
  camera.position.set(2.8, 2, 3.5)
  renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  container.value.appendChild(renderer.domElement)
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.07
  const grid = new THREE.GridHelper(8, 16, '#31504d', '#1d3332')
  grid.position.y = -1.4
  scene.add(grid)
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(container.value)
  resize()
  animate()
}

function resize() {
  if (!container.value || !renderer || !camera) return
  const width = container.value.clientWidth
  const height = container.value.clientHeight
  renderer.setSize(width, height, false)
  camera.aspect = width / Math.max(height, 1)
  camera.updateProjectionMatrix()
}

function animate() {
  animationFrame = requestAnimationFrame(animate)
  controls?.update()
  if (renderer && scene && camera) renderer.render(scene, camera)
}

async function loadCloud(url?: string) {
  if (!scene) return
  if (cloud) {
    scene.remove(cloud)
    cloud.geometry.dispose()
    ;(cloud.material as THREE.Material).dispose()
    cloud = undefined
  }
  pointCount.value = 0
  viewerError.value = ''
  if (!url) return
  loading.value = true
  try {
    const geometry = await new PLYLoader().loadAsync(url)
    geometry.computeBoundingBox()
    geometry.center()
    const box = geometry.boundingBox
    const size = box ? box.getSize(new THREE.Vector3()).length() : 1
    const scale = size > 0 ? 3.2 / size : 1
    geometry.scale(scale, scale, scale)
    pointCount.value = geometry.getAttribute('position')?.count ?? 0
    const material = new THREE.PointsMaterial({
      size: Math.max(0.012, 0.026 - Math.log10(Math.max(pointCount.value, 10)) * 0.003),
      sizeAttenuation: true,
      vertexColors: Boolean(geometry.getAttribute('color')),
      color: geometry.getAttribute('color') ? '#ffffff' : '#d39a62',
    })
    cloud = new THREE.Points(geometry, material)
    scene.add(cloud)
    resetView()
  } catch (error) {
    viewerError.value = error instanceof Error ? error.message : '点云加载失败'
  } finally {
    loading.value = false
  }
}

function resetView() {
  if (!camera || !controls) return
  camera.position.set(2.8, 1.8, 3.4)
  controls.target.set(0, 0, 0)
  controls.update()
}

watch(() => props.resultUrl, loadCloud)
onMounted(() => {
  initialize()
  void loadCloud(props.resultUrl)
})
onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  controls?.dispose()
  renderer?.dispose()
  renderer?.domElement.remove()
})
</script>

<template>
  <div class="viewer-wrap">
    <div ref="container" class="viewer-canvas" aria-label="三维点云交互查看器" />
    <div v-if="!resultUrl" class="viewer-empty">
      <span class="viewer-orbit">◌</span>
      <strong>等待重建结果</strong>
      <p>任务成功后，点云将在这里自动加载。</p>
    </div>
    <div v-if="loading" class="viewer-loading">正在解析 PLY 点云…</div>
    <div v-if="viewerError" class="viewer-error">{{ viewerError }}</div>
    <div v-if="pointCount" class="viewer-meta">
      <span>{{ pointCount.toLocaleString() }} points</span>
      <button type="button" @click="resetView">重置视角</button>
    </div>
  </div>
</template>
