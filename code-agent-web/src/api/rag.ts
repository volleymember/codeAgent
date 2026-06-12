import { request } from './request'

export interface RagSearchRequest {
  projectKey?: string
  moduleName?: string
  query: string
}

export interface RagSearchResult {
  chunkId: string
  title: string
  content: string
  sourceUri: string
  score: number
}

export function hybridSearch(payload: RagSearchRequest) {
  return request.post<RagSearchResult[], RagSearchResult[]>('/api/rag/hybrid-search', payload)
}
