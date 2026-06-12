import { request } from './request'
import type { AgentStep, AgentTask, CreateAgentTaskRequest } from '../types/task'
import type { EvidenceRecord } from '../types/evidence'

export function createAgentTask(payload: CreateAgentTaskRequest) {
  return request.post<AgentTask, AgentTask>('/api/agent/tasks', payload)
}

export function getAgentTask(taskNo: string) {
  return request.get<AgentTask, AgentTask>(`/api/agent/tasks/${taskNo}`)
}

export function getAgentSteps(taskNo: string) {
  return request.get<AgentStep[], AgentStep[]>(`/api/agent/tasks/${taskNo}/steps`)
}

export function getAgentEvidence(taskNo: string) {
  return request.get<EvidenceRecord[], EvidenceRecord[]>(`/api/agent/tasks/${taskNo}/evidence`)
}
