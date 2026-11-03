export interface HeritageProject {
  id: string
  name: string
  description: string | null
  heritageType: string
  createdAt: string
}

export interface ProjectImage {
  id: string
  projectId: string
  originalFilename: string
  contentType: string | null
  sizeBytes: number
  label: string | null
  createdAt: string
}

export type JobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface ReconstructionJob {
  id: string
  projectId: string
  algorithm: 'COLMAP_SPARSE' | 'DEMO_SPARSE'
  status: JobStatus
  imageCount: number
  message: string | null
  resultPath: string | null
  createdAt: string
  updatedAt: string
}

export interface ApiError {
  message?: string
}
