<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">RAG Center</h1>
        <p class="page-subtitle">Hybrid Search 调试入口；同步接口仅保留真实平台入库入口。</p>
      </div>
    </div>
    <div class="panel search">
      <el-input v-model="projectKey" placeholder="Project Key" />
      <el-input v-model="query" placeholder="检索问题或错误信息" />
      <el-button type="primary" :icon="Search" @click="run">检索</el-button>
    </div>
    <el-table :data="results" border stripe>
      <el-table-column prop="title" label="Title" min-width="180" />
      <el-table-column prop="content" label="Content" min-width="360" show-overflow-tooltip />
      <el-table-column prop="sourceUri" label="Source" min-width="240" show-overflow-tooltip />
      <el-table-column prop="score" label="Score" width="100" />
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { hybridSearch, type RagSearchResult } from '../api/rag'

const projectKey = ref('')
const query = ref('')
const results = ref<RagSearchResult[]>([])

async function run() {
  results.value = await hybridSearch({ projectKey: projectKey.value, query: query.value })
}
</script>

<style scoped>
.search {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) auto;
  gap: 10px;
}

@media (max-width: 800px) {
  .search {
    grid-template-columns: 1fr;
  }
}
</style>
