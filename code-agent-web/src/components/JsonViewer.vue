<template>
  <VueJsonPretty :data="parsed" :deep="2" show-length show-line />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VueJsonPretty from 'vue-json-pretty'

const props = defineProps<{ value?: string | Record<string, unknown> | unknown[] }>()

const parsed = computed(() => {
  if (!props.value) return {}
  if (typeof props.value !== 'string') return props.value
  try {
    return JSON.parse(props.value)
  } catch {
    return { value: props.value }
  }
})
</script>
