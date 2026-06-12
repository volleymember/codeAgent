<template>
  <el-timeline>
    <el-timeline-item
      v-for="step in steps"
      :key="step.id"
      :type="statusType(step.status)"
      :timestamp="formatDate(step.finishedAt || step.startedAt)"
    >
      <div class="step-title">{{ step.stepId }} · {{ step.agentName }}</div>
      <div class="step-sub">{{ step.toolName || 'internal' }}</div>
      <el-alert
        v-if="step.errorMessage"
        type="error"
        :closable="false"
        :title="step.errorMessage"
        show-icon
      />
    </el-timeline-item>
  </el-timeline>
</template>

<script setup lang="ts">
import type { AgentStep } from '../types/task'
import { formatDate, statusType } from '../utils/format'

defineProps<{ steps: AgentStep[] }>()
</script>

<style scoped>
.step-title {
  font-weight: 650;
}

.step-sub {
  margin-top: 4px;
  color: #65717d;
  font-size: 12px;
}
</style>
