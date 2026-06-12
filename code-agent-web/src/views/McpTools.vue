<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">MCP Tools</h1>
        <p class="page-subtitle">固定注册的外部研发平台工具。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-table :data="tools" border stripe>
      <el-table-column prop="platform" label="Platform" width="140" />
      <el-table-column prop="name" label="Tool" min-width="240" />
      <el-table-column prop="description" label="Description" min-width="320" />
      <el-table-column label="Required Inputs" min-width="220">
        <template #default="{ row }">
          <el-tag v-for="input in row.requiredInputs" :key="input" size="small">{{ input }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="timeoutMs" label="Timeout" width="110" />
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listTools } from '../api/tool'
import type { ToolDefinition } from '../types/tool'

const tools = ref<ToolDefinition[]>([])

async function load() {
  tools.value = await listTools()
}

onMounted(load)
</script>
