<template>
  <article class="evidence">
    <div class="row">
      <strong>{{ evidence.evidenceNo || evidence.title }}</strong>
      <el-tag size="small" type="info">{{ evidence.sourceType || 'unknown' }}</el-tag>
    </div>
    <p>{{ evidence.summary || '-' }}</p>
    <div class="meta">
      <span>score {{ Number(evidence.score || 0).toFixed(2) }}</span>
      <a v-if="evidence.sourceUri" :href="evidence.sourceUri" target="_blank" rel="noreferrer">source</a>
      <span v-if="evidence.rawRef">{{ evidence.rawRef }}</span>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { EvidenceRecord } from '../types/evidence'

defineProps<{ evidence: EvidenceRecord }>()
</script>

<style scoped>
.evidence {
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

p {
  margin: 10px 0;
  color: #33404c;
  line-height: 1.55;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #65717d;
  font-size: 12px;
}
</style>
