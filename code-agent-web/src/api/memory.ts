import { request } from './request'

export function listCoreMemory(projectKey: string) {
  return request.get<unknown[], unknown[]>('/api/memory/core', { params: { projectKey } })
}

export function createCoreMemory(payload: Record<string, unknown>) {
  return request.post<unknown, unknown>('/api/memory/core', payload)
}

export function searchEpisodes(projectKey: string) {
  return request.post<unknown[], unknown[]>('/api/memory/episode/search', { projectKey })
}

export function listMemoryRecalls(params: { projectKey?: string; taskNo?: string; sessionId?: string }) {
  return request.get<unknown[], unknown[]>('/api/memory/center/recalls', { params })
}
