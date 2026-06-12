import { request } from './request'

export interface Metrics {
  tasks: number
  toolCalls: number
  llmCalls: number
  evidence: number
  taskStatus?: Record<string, number>
  taskSuccessRate?: number
  averageToolLatencyMs?: number
  toolFailureRate?: number
  averageLlmInputTokens?: number
  averageLlmOutputTokens?: number
  groundedReportRate?: number
}

export function getMetrics() {
  return request.get<Metrics, Metrics>('/api/metrics/tasks')
}

export function getHealth() {
  return request.get<Record<string, unknown>, Record<string, unknown>>('/api/health')
}
