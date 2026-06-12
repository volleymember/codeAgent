<template>
  <el-table :data="calls" border stripe>
    <el-table-column prop="taskNo" label="Task No" min-width="170" />
    <el-table-column prop="toolName" label="Tool" min-width="210" />
    <el-table-column label="Status" width="110">
      <template #default="{ row }">
        <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="latencyMs" label="Latency" width="110" />
    <el-table-column prop="outputSummary" label="Summary" min-width="260" show-overflow-tooltip />
    <el-table-column prop="rawOutputUri" label="Raw Ref" min-width="220" show-overflow-tooltip />
    <el-table-column prop="errorMessage" label="Error" min-width="220" show-overflow-tooltip />
  </el-table>
</template>

<script setup lang="ts">
import type { ToolCallRecord } from '../types/tool'
import { statusType } from '../utils/format'

defineProps<{ calls: ToolCallRecord[] }>()
</script>
