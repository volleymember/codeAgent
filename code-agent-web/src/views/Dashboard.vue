<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Dashboard</h1>
        <p class="page-subtitle">任务、工具、LLM 和证据的运行概览。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="metrics">
      <MetricCard label="任务总数" :value="metrics.tasks" />
      <MetricCard label="工具调用" :value="metrics.toolCalls" />
      <MetricCard label="LLM 调用" :value="metrics.llmCalls" />
      <MetricCard label="Evidence" :value="metrics.evidence" />
    </div>

    <div class="panel">
      <h2>运行质量</h2>
      <JsonViewer :value="qualityMetrics" />
    </div>

    <div class="panel">
      <h2>Health</h2>
      <JsonViewer :value="health" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import MetricCard from '../components/MetricCard.vue'
import JsonViewer from '../components/JsonViewer.vue'
import { getHealth, getMetrics, type Metrics } from '../api/metrics'

const metrics = reactive<Metrics>({ tasks: 0, toolCalls: 0, llmCalls: 0, evidence: 0 })
const health = ref<Record<string, unknown>>({})
const qualityMetrics = computed(() => ({
  taskStatus: metrics.taskStatus || {},
  taskSuccessRate: metrics.taskSuccessRate || 0,
  toolFailureRate: metrics.toolFailureRate || 0,
  averageToolLatencyMs: metrics.averageToolLatencyMs || 0,
  averageLlmInputTokens: metrics.averageLlmInputTokens || 0,
  averageLlmOutputTokens: metrics.averageLlmOutputTokens || 0,
  groundedReportRate: metrics.groundedReportRate || 0
}))

async function load() {
  Object.assign(metrics, await getMetrics())
  health.value = await getHealth()
}

onMounted(load)
</script>

<style scoped>
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

@media (max-width: 900px) {
  .metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
