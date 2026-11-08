export interface HeritageProject {
  id: string
  name: string
  description: string | null
  heritageType: string
  collectionCode: string | null
  era: string | null
  material: string | null
  dimensions: string | null
  provenance: string | null
  publicationStatus: 'DRAFT' | 'PUBLISHED' | 'UNPUBLISHED'
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface PublicHeritage {
  id: string
  name: string
  description: string | null
  heritageType: string
  collectionCode: string | null
  era: string | null
  material: string | null
  dimensions: string | null
  provenance: string | null
  artifactId: string
  jobId: string
  artifactName: string
  versionNumber: number
  format: string
  pointCount: number
  faceCount: number
  sizeBytes: number
  publishedAt: string
}

export interface ReconstructionArtifact {
  id: string
  projectId: string
  jobId: string
  name: string
  versionNumber: number
  format: string
  pointCount: number
  faceCount: number
  sizeBytes: number
  resultPath: string
  current: boolean
  createdAt: string
  updatedAt: string
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
  algorithm: 'COLMAP_SPARSE' | 'COLMAP_MESH' | 'COLMAP_DENSE' | 'DEMO_SPARSE' | 'DEMO_MESH'
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
