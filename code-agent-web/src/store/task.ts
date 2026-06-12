import { defineStore } from 'pinia'
import { createAgentTask, getAgentEvidence, getAgentSteps, getAgentTask } from '../api/agentTask'
import { listToolCalls } from '../api/tool'
import { listMemoryRecalls } from '../api/memory'
import type { AgentStep, AgentTask, CreateAgentTaskRequest } from '../types/task'
import type { EvidenceRecord } from '../types/evidence'
import type { ToolCallRecord } from '../types/tool'

export const useTaskStore = defineStore('task', {
  state: () => ({
    current: null as AgentTask | null,
    steps: [] as AgentStep[],
    evidence: [] as EvidenceRecord[],
    toolCalls: [] as ToolCallRecord[],
    memoryRecalls: [] as unknown[],
    loading: false
  }),
  actions: {
    async create(payload: CreateAgentTaskRequest) {
      this.loading = true
      try {
        this.current = await createAgentTask(payload)
        return this.current
      } finally {
        this.loading = false
      }
    },
    async refresh(taskNo: string) {
      const [task, steps, evidence, toolCalls, memoryRecalls] = await Promise.all([
        getAgentTask(taskNo),
        getAgentSteps(taskNo),
        getAgentEvidence(taskNo),
        listToolCalls(taskNo),
        listMemoryRecalls({ taskNo })
      ])
      this.current = task
      this.steps = steps
      this.evidence = evidence
      this.toolCalls = toolCalls
      this.memoryRecalls = memoryRecalls
    }
  }
})
