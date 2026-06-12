export interface IntegrationConfig {
  id: number
  platform: string
  projectKey?: string
  baseUrl: string
  authType: string
  secretRef: string
  enabled: boolean
  connectionStatus?: string
  lastVerifiedAt?: string
}
