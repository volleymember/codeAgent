export interface ToolDefinition {
  name: string
  platform: string
  description: string
  requiredInputs: string[]
  timeoutMs: number
}

export interface ToolCallRecord {
  id: number
  taskNo: string
  toolName: string
  inputJson?: string
  outputSummary?: string
  rawOutputUri?: string
  status?: string
  latencyMs?: number
  errorMessage?: string
  createdAt?: string
}
