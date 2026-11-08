<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api } from './api'
import type { HeritageProject, ProjectImage, ReconstructionArtifact, ReconstructionJob } from './types'

const ReconstructionViewer = defineAsyncComponent(() => import('./components/ReconstructionViewer.vue'))

const projects = ref<HeritageProject[]>([])
const images = ref<ProjectImage[]>([])
const jobs = ref<ReconstructionJob[]>([])
const artifacts = ref<ReconstructionArtifact[]>([])
const selectedProjectId = ref('')
const selectedJobId = ref('')
const showProjectForm = ref(false)
const editingArchive = ref(false)
const loading = ref(true)
const busy = ref(false)
const uploadProgress = ref('')
const errorMessage = ref('')
const jobLog = ref('')
const showJobLog = ref(false)
const logLoading = ref(false)
const emptyProjectForm = () => ({ name: '', description: '', heritageType: '器物', collectionCode: '', era: '', material: '', dimensions: '', provenance: '' })
const projectForm = ref(emptyProjectForm())
const archiveForm = ref(emptyProjectForm())

const selectedProject = computed(() => projects.value.find((project) => project.id === selectedProjectId.value))
const selectedJob = computed(() => jobs.value.find((job) => job.id === selectedJobId.value))
const selectedArtifact = computed(() => artifacts.value.find((artifact) => artifact.jobId === selectedJobId.value))
const resultUrl = computed(() => {
  if (!selectedJob.value || selectedJob.value.status !== 'SUCCEEDED') return undefined
  return api.jobResultUrl(selectedProjectId.value, selectedJob.value.id, selectedJob.value.updatedAt)
})
const hasSurfaceModel = computed(() => Boolean(selectedJob.value && ['COLMAP_MESH', 'COLMAP_DENSE', 'DEMO_MESH'].includes(selectedJob.value.algorithm)))
const pointCloudUrl = computed(() => {
  if (!resultUrl.value || !selectedJob.value || !hasSurfaceModel.value) return undefined
  return api.jobPointCloudUrl(selectedProjectId.value, selectedJob.value.id, selectedJob.value.updatedAt)
})
const resultKind = computed<'POINT_CLOUD' | 'SURFACE_MODEL'>(() => hasSurfaceModel.value ? 'SURFACE_MODEL' : 'POINT_CLOUD')
const hasActiveJob = computed(() => jobs.value.some((job) => ['QUEUED', 'RUNNING'].includes(job.status)))
const canStartColmap = computed(() => images.value.length >= 3 && !hasActiveJob.value)
let pollTimer = 0

async function run(action: () => Promise<void>) {
  errorMessage.value = ''
  try {
    await action()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '操作失败，请稍后重试'
  }
}

async function loadProjects() {
  projects.value = await api.listProjects()
  if (!selectedProjectId.value && projects.value.length) selectedProjectId.value = projects.value[0].id
}

async function loadProjectData() {
  if (!selectedProjectId.value) {
    images.value = []
    jobs.value = []
    artifacts.value = []
    return
  }
  const [nextImages, nextJobs, nextArtifacts] = await Promise.all([
    api.listImages(selectedProjectId.value),
    api.listJobs(selectedProjectId.value),
    api.listArtifacts(selectedProjectId.value),
  ])
  images.value = nextImages
  jobs.value = nextJobs
  artifacts.value = nextArtifacts
  if (!jobs.value.some((job) => job.id === selectedJobId.value)) {
    selectedJobId.value = artifacts.value.find((artifact) => artifact.current)?.jobId ?? jobs.value[0]?.id ?? ''
  }
}

async function createProject() {
  if (!projectForm.value.name.trim() || !projectForm.value.heritageType.trim()) return
  busy.value = true
  await run(async () => {
    const created = await api.createProject(projectForm.value)
    projects.value.unshift(created)
    selectedProjectId.value = created.id
    projectForm.value = emptyProjectForm()
    showProjectForm.value = false
  })
  busy.value = false
}

function beginArchiveEdit() {
  if (!selectedProject.value) return
  archiveForm.value = {
    name: selectedProject.value.name, description: selectedProject.value.description ?? '',
    heritageType: selectedProject.value.heritageType, collectionCode: selectedProject.value.collectionCode ?? '',
    era: selectedProject.value.era ?? '', material: selectedProject.value.material ?? '',
    dimensions: selectedProject.value.dimensions ?? '', provenance: selectedProject.value.provenance ?? '',
  }
  editingArchive.value = true
}

async function saveArchive() {
  if (!selectedProjectId.value || !archiveForm.value.name.trim()) return
  busy.value = true
  await run(async () => {
    const updated = await api.updateProject(selectedProjectId.value, archiveForm.value)
    projects.value = projects.value.map((project) => project.id === updated.id ? updated : project)
    editingArchive.value = false
  })
  busy.value = false
}

async function deleteCurrentProject() {
  if (!selectedProject.value || !window.confirm(`确认删除项目“${selectedProject.value.name}”及全部图片和成果？`)) return
  busy.value = true
  await run(async () => {
    await api.deleteProject(selectedProject.value!.id)
    projects.value = projects.value.filter((project) => project.id !== selectedProjectId.value)
    selectedProjectId.value = projects.value[0]?.id ?? ''
  })
  busy.value = false
}

async function selectArtifact(artifact: ReconstructionArtifact) {
  if (!selectedProjectId.value) return
  await run(async () => {
    await api.selectArtifact(selectedProjectId.value, artifact.id)
    artifacts.value = artifacts.value.map((item) => ({ ...item, current: item.id === artifact.id }))
    selectedJobId.value = artifact.jobId
  })
}

async function renameArtifact(artifact: ReconstructionArtifact) {
  const name = window.prompt('成果名称', artifact.name)?.trim()
  if (!name || !selectedProjectId.value) return
  await run(async () => {
    const updated = await api.renameArtifact(selectedProjectId.value, artifact.id, name)
    artifacts.value = artifacts.value.map((item) => item.id === updated.id ? updated : item)
  })
}

async function deleteArtifact(artifact: ReconstructionArtifact) {
  if (!selectedProjectId.value || !window.confirm(`确认删除成果“${artifact.name}”及模型文件？`)) return
  await run(async () => {
    await api.deleteArtifact(selectedProjectId.value, artifact.id)
    artifacts.value = await api.listArtifacts(selectedProjectId.value)
    selectedJobId.value = artifacts.value.find((item) => item.current)?.jobId ?? jobs.value[0]?.id ?? ''
  })
}

async function uploadFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (!files.length || !selectedProjectId.value) return
  busy.value = true
  await run(async () => {
    for (const [index, file] of files.entries()) {
      uploadProgress.value = `正在上传 ${index + 1}/${files.length}：${file.name}`
      await api.uploadImage(selectedProjectId.value, file)
    }
    await loadProjectData()
  })
  uploadProgress.value = ''
  busy.value = false
  input.value = ''
}

async function createJob(algorithm: ReconstructionJob['algorithm']) {
  if (!selectedProjectId.value) return
  busy.value = true
  await run(async () => {
    const created = await api.createJob(selectedProjectId.value, algorithm)
    jobs.value.unshift(created)
    selectedJobId.value = created.id
  })
  busy.value = false
}

async function deleteImage(image: ProjectImage) {
  if (!selectedProjectId.value || !window.confirm(`确认删除图片“${image.originalFilename}”？`)) return
  busy.value = true
  await run(async () => {
    await api.deleteImage(selectedProjectId.value, image.id)
    images.value = images.value.filter((item) => item.id !== image.id)
  })
  busy.value = false
}

async function retrySelectedJob() {
  if (!selectedProjectId.value || !selectedJob.value) return
  busy.value = true
  await run(async () => {
    const created = await api.retryJob(selectedProjectId.value, selectedJob.value!.id)
    jobs.value.unshift(created)
    selectedJobId.value = created.id
  })
  busy.value = false
}

async function toggleJobLog() {
  if (!selectedProjectId.value || !selectedJob.value) return
  if (showJobLog.value) {
    showJobLog.value = false
    return
  }
  logLoading.value = true
  await run(async () => {
    jobLog.value = await api.getJobLog(selectedProjectId.value, selectedJob.value!.id)
    showJobLog.value = true
  })
  logLoading.value = false
}

function statusText(status: ReconstructionJob['status']) {
  return { QUEUED: '等待中', RUNNING: '重建中', SUCCEEDED: '已完成', FAILED: '失败' }[status]
}

function algorithmText(algorithm: ReconstructionJob['algorithm']) {
  return {
    COLMAP_SPARSE: 'COLMAP 稀疏重建',
    COLMAP_MESH: 'COLMAP 彩色表面模型',
    COLMAP_DENSE: 'COLMAP CUDA 稠密模型',
    DEMO_SPARSE: 'Demo 点云',
    DEMO_MESH: 'Demo 表面模型',
  }[algorithm]
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

function formatBytes(value: number) {
  return value < 1024 * 1024 ? `${Math.round(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`
}

watch(selectedProjectId, () => {
  selectedJobId.value = ''
  editingArchive.value = false
  void run(loadProjectData)
})

watch(selectedJobId, () => {
  showJobLog.value = false
  jobLog.value = ''
})

onMounted(async () => {
  await run(async () => {
    await loadProjects()
    await loadProjectData()
  })
  loading.value = false
  pollTimer = window.setInterval(() => {
    if (hasActiveJob.value) void run(loadProjectData)
  }, 1500)
})
onBeforeUnmount(() => window.clearInterval(pollTimer))
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand-mark">文</div>
      <div class="brand-copy">
        <strong>数智文博 · 三维重建台</strong>
        <span>ProjectDesignDemo / COLMAP Surface Reconstruction</span>
      </div>
      <div class="system-state"><i /> 本地服务在线</div>
    </header>

    <aside class="project-rail">
      <div class="rail-title">
        <span>文博项目</span>
        <button type="button" aria-label="创建项目" @click="showProjectForm = !showProjectForm">＋</button>
      </div>
      <form v-if="showProjectForm" class="project-form" @submit.prevent="createProject">
        <label>项目名称<input v-model="projectForm.name" maxlength="160" required placeholder="如：青铜器采集" /></label>
        <label>文物类型<input v-model="projectForm.heritageType" maxlength="80" required /></label>
        <label>项目说明<textarea v-model="projectForm.description" maxlength="4000" rows="3" /></label>
        <button class="primary compact" type="submit" :disabled="busy">创建项目</button>
      </form>
      <nav class="project-list" aria-label="项目列表">
        <button v-for="project in projects" :key="project.id" type="button" :class="{ active: project.id === selectedProjectId }" @click="selectedProjectId = project.id">
          <span class="project-symbol">{{ project.heritageType.slice(0, 1) }}</span>
          <span><strong>{{ project.name }}</strong><small>{{ project.heritageType }} · {{ formatDate(project.createdAt) }}</small></span>
        </button>
        <p v-if="!projects.length && !loading" class="rail-empty">点击右上角 ＋ 创建首个项目</p>
      </nav>
    </aside>

    <main class="workspace">
      <div v-if="errorMessage" class="error-banner"><span>{{ errorMessage }}</span><button type="button" @click="errorMessage = ''">×</button></div>
      <section v-if="selectedProject" class="project-heading">
        <div>
          <p class="eyebrow">{{ selectedProject.heritageType }} / DIGITAL HERITAGE</p>
          <h1>{{ selectedProject.name }}</h1>
          <p>{{ selectedProject.description || '通过多视角影像构建可交互的文物表面模型与稀疏点云。' }}</p>
        </div>
        <div class="heading-tools">
          <div class="metric-strip">
            <span><strong>{{ images.length }}</strong> 张影像</span>
            <span><strong>{{ jobs.length }}</strong> 次任务</span>
            <span><strong>{{ artifacts.length }}</strong> 个版本</span>
          </div>
          <div class="archive-actions"><button type="button" @click="beginArchiveEdit">编辑档案</button><button class="danger" type="button" :disabled="busy || hasActiveJob" @click="deleteCurrentProject">删除项目</button></div>
        </div>
      </section>

      <section v-if="selectedProject" class="archive-panel panel">
        <form v-if="editingArchive" class="archive-form" @submit.prevent="saveArchive">
          <label>项目名称<input v-model="archiveForm.name" maxlength="160" required /></label>
          <label>藏品编号<input v-model="archiveForm.collectionCode" maxlength="120" placeholder="如：BW-2026-001" /></label>
          <label>文物类型<input v-model="archiveForm.heritageType" maxlength="80" required /></label>
          <label>年代<input v-model="archiveForm.era" maxlength="160" placeholder="如：商代晚期" /></label>
          <label>材质<input v-model="archiveForm.material" maxlength="240" placeholder="如：青铜" /></label>
          <label>尺寸<input v-model="archiveForm.dimensions" maxlength="240" placeholder="如：高 32 cm，口径 18 cm" /></label>
          <label class="wide">来源<input v-model="archiveForm.provenance" maxlength="500" placeholder="收藏、出土或征集来源" /></label>
          <label class="wide">文物简介<textarea v-model="archiveForm.description" maxlength="4000" rows="3" /></label>
          <div class="wide form-actions"><button type="button" @click="editingArchive = false">取消</button><button class="primary" type="submit" :disabled="busy">保存档案</button></div>
        </form>
        <div v-else class="archive-summary">
          <span><small>藏品编号</small><strong>{{ selectedProject.collectionCode || '待补充' }}</strong></span>
          <span><small>年代</small><strong>{{ selectedProject.era || '待考证' }}</strong></span>
          <span><small>材质</small><strong>{{ selectedProject.material || '待补充' }}</strong></span>
          <span><small>尺寸</small><strong>{{ selectedProject.dimensions || '待测量' }}</strong></span>
          <span><small>来源</small><strong>{{ selectedProject.provenance || '待补充' }}</strong></span>
        </div>
      </section>

      <section v-if="selectedProject" class="content-grid">
        <div class="left-column">
          <article class="panel upload-panel">
            <div class="panel-heading">
              <div><span class="step-index">01</span><h2>采集影像</h2></div>
              <span class="panel-note">JPG · PNG · TIFF · BMP · WebP</span>
            </div>
            <label class="drop-zone" :class="{ disabled: busy }">
              <input type="file" accept="image/jpeg,image/png,image/tiff,image/bmp,image/webp" multiple :disabled="busy" @change="uploadFiles" />
              <span class="upload-icon">↥</span>
              <strong>{{ uploadProgress || '选择或拖入多视角照片' }}</strong>
              <small>真实 COLMAP 至少 3 张；建议围绕文物连续拍摄 20–60 张</small>
            </label>
            <div v-if="images.length" class="image-list">
              <div v-for="image in images.slice(-6)" :key="image.id" class="image-card">
                <img :src="api.imageContentUrl(selectedProjectId, image.id)" :alt="image.label || image.originalFilename" loading="lazy" />
                <span><strong>{{ image.originalFilename }}</strong><small>{{ formatBytes(image.sizeBytes) }}</small></span>
                <button type="button" :disabled="busy || hasActiveJob" :aria-label="`删除 ${image.originalFilename}`" @click="deleteImage(image)">×</button>
              </div>
              <p v-if="images.length > 6">另有 {{ images.length - 6 }} 张影像</p>
            </div>
          </article>

          <article class="panel task-panel">
            <div class="panel-heading">
              <div><span class="step-index">02</span><h2>重建任务</h2></div>
              <div class="reconstruction-actions">
                <button class="secondary" type="button" :disabled="!canStartColmap || busy" title="保留原有快速稀疏点云流程" @click="createJob('COLMAP_SPARSE')">仅点云</button>
                <button class="primary" type="button" :disabled="!canStartColmap || busy" @click="createJob('COLMAP_MESH')">生成表面模型</button>
              </div>
            </div>
            <p v-if="images.length < 3" class="task-hint">再上传 {{ 3 - images.length }} 张图片即可启动真实重建。</p>
            <div class="job-list">
              <button v-for="job in jobs" :key="job.id" type="button" :class="['job-row', { selected: job.id === selectedJobId }]" @click="selectedJobId = job.id">
                <span :class="['status-dot', job.status.toLowerCase()]" />
                <span class="job-main"><strong>{{ algorithmText(job.algorithm) }}</strong><small>{{ job.message }}</small></span>
                <span class="job-side"><b>{{ statusText(job.status) }}</b><small>{{ formatDate(job.createdAt) }}</small></span>
              </button>
              <p v-if="!jobs.length" class="list-empty">上传照片并启动首个重建任务。</p>
            </div>
            <div v-if="selectedJob" class="job-actions">
              <button v-if="selectedJob.algorithm.startsWith('COLMAP_')" type="button" :disabled="logLoading" @click="toggleJobLog">{{ showJobLog ? '关闭日志' : logLoading ? '读取中…' : '查看日志' }}</button>
              <button v-if="selectedJob.status === 'FAILED'" class="retry" type="button" :disabled="busy" @click="retrySelectedJob">重试任务</button>
            </div>
            <div v-if="showJobLog" class="log-panel"><div><span>COLMAP LOG · LAST 30K</span><button type="button" @click="showJobLog = false">×</button></div><pre>{{ jobLog }}</pre></div>
          </article>
        </div>

        <article class="panel viewer-panel">
          <div class="panel-heading viewer-heading">
            <div><span class="step-index">03</span><h2>三维成果</h2></div>
            <a v-if="resultUrl" :href="resultUrl" :download="hasSurfaceModel ? 'model.glb' : 'sparse.ply'">下载 {{ hasSurfaceModel ? 'GLB' : 'PLY' }}</a>
          </div>
          <ReconstructionViewer :result-url="resultUrl" :point-cloud-url="pointCloudUrl" :result-kind="resultKind" />
          <div class="artifact-manager">
            <div class="artifact-title"><strong>成果版本</strong><span>当前展示版本会用于后续公开发布</span></div>
            <div v-if="artifacts.length" class="artifact-list">
              <div v-for="artifact in artifacts" :key="artifact.id" :class="['artifact-row', { current: artifact.current }]">
                <button class="artifact-select" type="button" @click="selectArtifact(artifact)">
                  <span><b>v{{ artifact.versionNumber }}</b><strong>{{ artifact.name }}</strong></span>
                  <small>{{ artifact.format }} · {{ artifact.pointCount.toLocaleString() }} 点 · {{ artifact.faceCount.toLocaleString() }} 面 · {{ formatBytes(artifact.sizeBytes) }}</small>
                </button>
                <i>{{ artifact.current ? '当前' : formatDate(artifact.createdAt) }}</i>
                <button type="button" aria-label="重命名成果" @click="renameArtifact(artifact)">✎</button>
                <button class="danger" type="button" aria-label="删除成果" @click="deleteArtifact(artifact)">×</button>
              </div>
            </div>
            <p v-else>成功完成一次重建后，模型会作为可追溯版本保存在这里。</p>
          </div>
          <div class="viewer-footer">
            <span>左键旋转 · 右键平移 · 滚轮缩放</span>
            <span v-if="selectedArtifact">v{{ selectedArtifact.versionNumber }} · {{ selectedArtifact.name }}</span>
            <span v-else-if="selectedJob">任务 {{ selectedJob.id.slice(0, 8) }}</span>
          </div>
        </article>
      </section>

      <section v-else-if="!loading" class="welcome-state">
        <span>文</span><h1>创建第一个数字文博项目</h1><p>组织多视角照片，运行 COLMAP，并在浏览器中查看表面模型与稀疏点云。</p>
        <button class="primary" type="button" @click="showProjectForm = true">开始创建</button>
      </section>
    </main>
  </div>
</template>
