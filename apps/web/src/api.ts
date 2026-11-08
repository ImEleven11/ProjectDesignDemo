import type { ApiError, HeritageProject, ProjectImage, ReconstructionJob } from './types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) {
    let message = `请求失败（${response.status}）`
    try {
      const error = (await response.json()) as ApiError
      if (error.message) message = error.message
    } catch {
      // Keep the HTTP fallback when the response is not JSON.
    }
    throw new Error(message)
  }
  return response.json() as Promise<T>
}

async function requestText(path: string): Promise<string> {
  const response = await fetch(path)
  if (!response.ok) throw new Error(await errorMessage(response))
  return response.text()
}

async function requestVoid(path: string, init: RequestInit): Promise<void> {
  const response = await fetch(path, init)
  if (!response.ok) throw new Error(await errorMessage(response))
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const error = (await response.json()) as ApiError
    if (error.message) return error.message
  } catch {
    // Fall through to the HTTP status.
  }
  return `请求失败（${response.status}）`
}

export const api = {
  listProjects: () => request<HeritageProject[]>('/api/projects'),

  createProject: (payload: { name: string; description: string; heritageType: string }) =>
    request<HeritageProject>('/api/projects', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),

  listImages: (projectId: string) =>
    request<ProjectImage[]>(`/api/projects/${projectId}/images`),

  uploadImage: (projectId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    form.append('label', file.name.replace(/\.[^.]+$/, ''))
    return request<ProjectImage>(`/api/projects/${projectId}/images`, {
      method: 'POST',
      body: form,
    })
  },

  imageContentUrl: (projectId: string, imageId: string) =>
    `/api/projects/${projectId}/images/${imageId}/content`,

  deleteImage: (projectId: string, imageId: string) =>
    requestVoid(`/api/projects/${projectId}/images/${imageId}`, { method: 'DELETE' }),

  listJobs: (projectId: string) =>
    request<ReconstructionJob[]>(`/api/projects/${projectId}/jobs`),

  createJob: (projectId: string, algorithm: ReconstructionJob['algorithm']) =>
    request<ReconstructionJob>(`/api/projects/${projectId}/jobs`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ algorithm }),
    }),

  retryJob: (projectId: string, jobId: string) =>
    request<ReconstructionJob>(`/api/projects/${projectId}/jobs/${jobId}/retry`, { method: 'POST' }),

  getJobLog: (projectId: string, jobId: string) =>
    requestText(`/api/projects/${projectId}/jobs/${jobId}/log`),

  jobResultUrl: (projectId: string, jobId: string, version: string) =>
    `/api/projects/${projectId}/jobs/${jobId}/result?v=${encodeURIComponent(version)}`,

  jobPointCloudUrl: (projectId: string, jobId: string, version: string) =>
    `/api/projects/${projectId}/jobs/${jobId}/point-cloud?v=${encodeURIComponent(version)}`,
}
