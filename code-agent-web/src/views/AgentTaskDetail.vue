<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ taskNo }}</h1>
        <p class="page-subtitle">状态、执行步骤、Evidence Pack 与最终报告。</p>
      </div>
      <div class="toolbar">
        <el-tag v-if="task" :type="statusType(task.status)" size="large">{{ task.status }}</el-tag>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="split">
      <aside class="panel">
        <AgentTimeline :steps="steps" />
      </aside>

      <main class="panel">
        <el-tabs>
          <el-tab-pane label="最终报告">
            <MarkdownViewer :content="task?.finalReport || ''" />
          </el-tab-pane>
          <el-tab-pane label="请求">
            <JsonViewer :value="task?.requestJson" />
          </el-tab-pane>
          <el-tab-pane label="工具调用">
            <ToolCallTable :calls="toolCalls" />
          </el-tab-pane>
          <el-tab-pane label="记忆召回">
            <JsonViewer :value="memoryRecalls" />
          </el-tab-pane>
        </el-tabs>
      </main>

      <aside class="evidence-list">
        <EvidenceCard v-for="item in evidence" :key="item.id" :evidence="item" />
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import AgentTimeline from '../components/AgentTimeline.vue'
import EvidenceCard from '../components/EvidenceCard.vue'
import JsonViewer from '../components/JsonViewer.vue'
import MarkdownViewer from '../components/MarkdownViewer.vue'
import ToolCallTable from '../components/ToolCallTable.vue'
import { useTaskStore } from '../store/task'
import { statusType } from '../utils/format'
import { terminalStatuses } from '../utils/constants'

const props = defineProps<{ taskNo: string }>()
const store = useTaskStore()
const task = computed(() => store.current)
const steps = computed(() => store.steps)
const evidence = computed(() => store.evidence)
const toolCalls = computed(() => store.toolCalls)
const memoryRecalls = computed(() => store.memoryRecalls)
let timer: number | undefined

async function load() {
  await store.refresh(props.taskNo)
  if (task.value && terminalStatuses.includes(task.value.status) && timer) {
    window.clearInterval(timer)
  }
}

onMounted(() => {
  load()
  timer = window.setInterval(load, 2000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.evidence-list {
  display: grid;
  gap: 12px;
}
</style>
