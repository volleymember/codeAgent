import { defineStore } from 'pinia'
import { listCoreMemory } from '../api/memory'

export const useMemoryStore = defineStore('memory', {
  state: () => ({ core: [] as unknown[] }),
  actions: {
    async load(projectKey: string) {
      this.core = await listCoreMemory(projectKey)
    }
  }
})
