import { defineStore } from 'pinia'
import { hybridSearch, type RagSearchResult } from '../api/rag'

export const useRagStore = defineStore('rag', {
  state: () => ({ results: [] as RagSearchResult[] }),
  actions: {
    async search(projectKey: string, query: string) {
      this.results = await hybridSearch({ projectKey, query })
    }
  }
})
