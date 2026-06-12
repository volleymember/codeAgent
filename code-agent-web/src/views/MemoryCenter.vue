<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Memory Center</h1>
        <p class="page-subtitle">维护项目规则与历史经验。</p>
      </div>
      <div class="toolbar">
        <el-input v-model="projectKey" placeholder="Project Key" />
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="panel form-row">
      <el-input v-model="type" placeholder="type，例如 coding_rule" />
      <el-input v-model="content" placeholder="规则内容" />
      <el-input-number v-model="priority" :min="0" :max="1000" />
      <el-button type="primary" :icon="Plus" @click="create">新增</el-button>
    </div>

    <el-table :data="items" border stripe>
      <el-table-column prop="projectKey" label="Project" width="160" />
      <el-table-column prop="type" label="Type" width="160" />
      <el-table-column prop="priority" label="Priority" width="100" />
      <el-table-column prop="content" label="Content" min-width="360" />
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { createCoreMemory, listCoreMemory } from '../api/memory'

const projectKey = ref('')
const type = ref('coding_rule')
const content = ref('')
const priority = ref(100)
const items = ref<unknown[]>([])

async function load() {
  if (!projectKey.value) return
  items.value = await listCoreMemory(projectKey.value)
}

async function create() {
  await createCoreMemory({ projectKey: projectKey.value, type: type.value, content: content.value, priority: priority.value })
  content.value = ''
  await load()
}
</script>

<style scoped>
.form-row {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 140px auto;
  gap: 10px;
}

@media (max-width: 900px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
