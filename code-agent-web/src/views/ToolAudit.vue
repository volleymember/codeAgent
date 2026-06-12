<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Tool Audit</h1>
        <p class="page-subtitle">查看指定任务的外部工具调用审计记录。</p>
      </div>
      <div class="toolbar">
        <el-input v-model="taskNo" placeholder="TASK-..." clearable />
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>
    </div>
    <ToolCallTable :calls="calls" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import ToolCallTable from '../components/ToolCallTable.vue'
import { listToolCalls } from '../api/tool'
import type { ToolCallRecord } from '../types/tool'

const taskNo = ref('')
const calls = ref<ToolCallRecord[]>([])

async function load() {
  if (!taskNo.value) return
  calls.value = await listToolCalls(taskNo.value)
}
</script>
