import { request } from './request'
import type { ToolCallRecord, ToolDefinition } from '../types/tool'

export function listTools() {
  return request.get<ToolDefinition[], ToolDefinition[]>('/api/tools')
}

export function listToolCalls(taskNo: string) {
  return request.get<ToolCallRecord[], ToolCallRecord[]>(`/api/tools/calls/${taskNo}`)
}
