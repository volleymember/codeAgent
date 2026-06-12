<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Evidence List</h1>
        <p class="page-subtitle">按任务号查询真实来源证据。</p>
      </div>
      <div class="toolbar">
        <el-input v-model="taskNo" placeholder="TASK-..." clearable />
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>
    </div>
    <div class="evidence-grid">
      <EvidenceCard v-for="item in evidence" :key="item.id" :evidence="item" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import EvidenceCard from '../components/EvidenceCard.vue'
import { getAgentEvidence } from '../api/agentTask'
import type { EvidenceRecord } from '../types/evidence'

const taskNo = ref('')
const evidence = ref<EvidenceRecord[]>([])

async function load() {
  if (!taskNo.value) return
  evidence.value = await getAgentEvidence(taskNo.value)
}
</script>

<style scoped>
.evidence-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}
</style>
