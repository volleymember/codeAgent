export interface CreateAgentTaskRequest {
  taskType: 'CI_FAILURE_ANALYSIS' | 'MR_RISK_ANALYSIS'
  projectKey: string
  gitlabMrUrl?: string
  jenkinsBuildUrl?: string
  sonarqubeProjectKey?: string
  jiraIssueKey?: string
  confluencePageUrl?: string
  openApiUrl?: string
}

export interface AgentTask {
  id: number
  taskNo: string
  taskType: string
  projectKey: string
  status: string
  currentRound: number
  maxRounds: number
  requestJson?: string
  finalReport?: string
  createdAt?: string
  updatedAt?: string
}

export interface AgentStep {
  id: number
  taskNo: string
  stepId: string
  agentName: string
  toolName?: string
  status: string
  inputJson?: string
  outputSummary?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
}
