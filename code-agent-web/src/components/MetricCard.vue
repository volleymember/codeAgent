<template>
  <div class="metric">
    <div class="label">{{ label }}</div>
    <div class="value">{{ value }}</div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
}>()
</script>

<style scoped>
.metric {
  min-height: 92px;
  padding: 16px;
  background: #fff;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
}

.label {
  color: #65717d;
  font-size: 13px;
}

.value {
  margin-top: 12px;
  font-size: 28px;
  line-height: 1;
  font-weight: 700;
  color: #172026;
}
</style>
