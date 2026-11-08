<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { PLYLoader } from 'three/examples/jsm/loaders/PLYLoader.js'

const props = defineProps<{
  resultUrl?: string
  pointCloudUrl?: string
  resultKind: 'POINT_CLOUD' | 'SURFACE_MODEL'
}>()

const container = ref<HTMLDivElement>()
const loading = ref(false)
const viewerError = ref('')
const detailCount = ref(0)
const mode = ref<'model' | 'cloud'>('model')
const activeUrl = computed(() => mode.value === 'cloud' ? props.pointCloudUrl ?? props.resultUrl : props.resultUrl)

let renderer: THREE.WebGLRenderer | undefined
let scene: THREE.Scene | undefined
let camera: THREE.PerspectiveCamera | undefined
let controls: OrbitControls | undefined
let content: THREE.Object3D | undefined
let resizeObserver: ResizeObserver | undefined
let animationFrame = 0

function initialize() {
  if (!container.value) return
  scene = new THREE.Scene()
  scene.background = new THREE.Color('#101b1d')
  scene.fog = new THREE.FogExp2('#101b1d', 0.035)
  camera = new THREE.PerspectiveCamera(42, 1, 0.01, 1000)
  renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  container.value.appendChild(renderer.domElement)
  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.07

  scene.add(new THREE.HemisphereLight('#f2e8d4', '#203b3b', 2.2))
  const keyLight = new THREE.DirectionalLight('#fff5df', 2.8)
  keyLight.position.set(3, 4, 2)
  scene.add(keyLight)
  const rimLight = new THREE.DirectionalLight('#78a9a1', 1.5)
  rimLight.position.set(-3, 1, -2)
  scene.add(rimLight)
  const grid = new THREE.GridHelper(8, 16, '#31504d', '#1d3332')
  grid.position.y = -1.45
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

function disposeContent() {
  if (!scene || !content) return
  scene.remove(content)
  content.traverse((object) => {
    const mesh = object as THREE.Mesh
    mesh.geometry?.dispose()
    const materials = Array.isArray(mesh.material) ? mesh.material : mesh.material ? [mesh.material] : []
    for (const material of materials) material.dispose()
  })
  content = undefined
}

function frameContent(object: THREE.Object3D) {
  const box = new THREE.Box3().setFromObject(object)
  const center = box.getCenter(new THREE.Vector3())
  const size = box.getSize(new THREE.Vector3()).length()
  const scale = size > 0 ? 3.2 / size : 1
  object.scale.setScalar(scale)
  object.position.copy(center).multiplyScalar(-scale)
  resetView()
}

async function loadResult() {
  if (!scene) return
  disposeContent()
  detailCount.value = 0
  viewerError.value = ''
  const url = activeUrl.value
  if (!url) return
  loading.value = true
  try {
    if (mode.value === 'model' && props.resultKind === 'SURFACE_MODEL') {
      const gltf = await new GLTFLoader().loadAsync(url)
      content = gltf.scene
      content.traverse((object) => {
        const mesh = object as THREE.Mesh
        if (!mesh.isMesh) return
        const geometry = mesh.geometry
        const originalMaterials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
        for (const material of originalMaterials) material?.dispose()
        mesh.material = new THREE.MeshBasicMaterial({
          vertexColors: Boolean(geometry.getAttribute('color')),
          color: geometry.getAttribute('color') ? '#ffffff' : '#d8a66d',
          side: THREE.DoubleSide,
          toneMapped: false,
        })
        detailCount.value += geometry.index
          ? Math.floor(geometry.index.count / 3)
          : Math.floor((geometry.getAttribute('position')?.count ?? 0) / 3)
      })
    } else {
      const geometry = await new PLYLoader().loadAsync(url)
      detailCount.value = geometry.getAttribute('position')?.count ?? 0
      const material = new THREE.PointsMaterial({
        size: Math.max(0.012, 0.026 - Math.log10(Math.max(detailCount.value, 10)) * 0.003),
        sizeAttenuation: true,
        vertexColors: Boolean(geometry.getAttribute('color')),
        color: geometry.getAttribute('color') ? '#ffffff' : '#d39a62',
      })
      content = new THREE.Points(geometry, material)
    }
    scene.add(content)
    frameContent(content)
  } catch (error) {
    viewerError.value = error instanceof Error ? error.message : '三维成果加载失败'
  } finally {
    loading.value = false
  }
}

function selectMode(nextMode: 'model' | 'cloud') {
  mode.value = nextMode
}

function resetView() {
  if (!camera || !controls) return
  camera.position.set(2.8, 1.8, 3.4)
  controls.target.set(0, 0, 0)
  controls.update()
}

watch(() => props.resultKind, (kind) => { mode.value = kind === 'SURFACE_MODEL' ? 'model' : 'cloud' })
watch([activeUrl, () => props.resultKind], () => { void loadResult() })
onMounted(() => {
  mode.value = props.resultKind === 'SURFACE_MODEL' ? 'model' : 'cloud'
  initialize()
  void loadResult()
})
onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  controls?.dispose()
  disposeContent()
  renderer?.dispose()
  renderer?.domElement.remove()
})
</script>

<template>
  <div class="viewer-wrap">
    <div ref="container" class="viewer-canvas" aria-label="三维重建成果交互查看器" />
    <div v-if="!resultUrl" class="viewer-empty">
      <span class="viewer-orbit">◌</span>
      <strong>等待重建结果</strong>
      <p>任务成功后，表面模型或点云将在这里自动加载。</p>
    </div>
    <div v-if="loading" class="viewer-loading">正在解析三维成果…</div>
    <div v-if="viewerError" class="viewer-error">{{ viewerError }}</div>
    <div v-if="resultUrl" class="viewer-mode" aria-label="三维成果显示模式">
      <button v-if="resultKind === 'SURFACE_MODEL'" type="button" :class="{ active: mode === 'model' }" @click="selectMode('model')">表面模型</button>
      <button v-if="pointCloudUrl || resultKind === 'POINT_CLOUD'" type="button" :class="{ active: mode === 'cloud' }" @click="selectMode('cloud')">稀疏点云</button>
    </div>
    <div v-if="detailCount" class="viewer-meta">
      <span>{{ detailCount.toLocaleString() }} {{ mode === 'model' ? 'triangles' : 'points' }}</span>
      <button type="button" @click="resetView">重置视角</button>
    </div>
  </div>
</template>
