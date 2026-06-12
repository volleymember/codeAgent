import { request } from './request'
import type { IntegrationConfig } from '../types/integration'

export function listIntegrations() {
  return request.get<IntegrationConfig[], IntegrationConfig[]>('/api/integrations')
}

export function saveIntegration(platform: string, payload: Partial<IntegrationConfig>) {
  return request.post<IntegrationConfig, IntegrationConfig>(`/api/integrations/${platform}`, payload)
}
