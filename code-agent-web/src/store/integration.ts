import { defineStore } from 'pinia'
import { listIntegrations } from '../api/integration'
import type { IntegrationConfig } from '../types/integration'

export const useIntegrationStore = defineStore('integration', {
  state: () => ({ items: [] as IntegrationConfig[] }),
  actions: {
    async load() {
      this.items = await listIntegrations()
    }
  }
})
