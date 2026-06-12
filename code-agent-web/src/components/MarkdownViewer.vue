<template>
  <MdPreview :model-value="content || ''" preview-theme="github" />
</template>

<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'

defineProps<{ content?: string }>()
</script>
