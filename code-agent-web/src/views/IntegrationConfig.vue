<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">Integration Config</h1>
        <p class="page-subtitle">记录平台配置引用；运行时 Token 仍由后端环境变量读取。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="panel form-row">
      <el-select v-model="platform">
        <el-option label="GitLab" value="gitlab" />
        <el-option label="Jenkins" value="jenkins" />
        <el-option label="SonarQube" value="sonarqube" />
      </el-select>
      <el-input v-model="baseUrl" placeholder="Base URL" />
      <el-input v-model="secretRef" placeholder="例如 env:GITLAB_TOKEN" />
      <el-button type="primary" :icon="Check" @click="save">保存</el-button>
    </div>

    <el-table :data="items" border stripe>
      <el-table-column prop="platform" label="Platform" width="140" />
      <el-table-column prop="projectKey" label="Project" width="140" />
      <el-table-column prop="baseUrl" label="Base URL" min-width="260" />
      <el-table-column prop="secretRef" label="Secret Ref" min-width="220" />
      <el-table-column prop="connectionStatus" label="Status" width="140" />
      <el-table-column prop="enabled" label="Enabled" width="100" />
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Check, Refresh } from '@element-plus/icons-vue'
import { listIntegrations, saveIntegration } from '../api/integration'
import type { IntegrationConfig } from '../types/integration'

const items = ref<IntegrationConfig[]>([])
const platform = ref('gitlab')
const baseUrl = ref('')
const secretRef = ref('env:GITLAB_TOKEN')

async function load() {
  items.value = await listIntegrations()
}

async function save() {
  await saveIntegration(platform.value, {
    baseUrl: baseUrl.value,
    secretRef: secretRef.value,
    authType: 'ENV_SECRET_REF',
    enabled: true
  })
  await load()
}

onMounted(load)
</script>

<style scoped>
.form-row {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) 240px auto;
  gap: 10px;
}

@media (max-width: 900px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
