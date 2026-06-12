<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">创建 Agent 任务</h1>
        <p class="page-subtitle">提交真实平台 URL 和项目 Key，由后端拉取证据并执行分析。</p>
      </div>
    </div>

    <el-form class="panel form" label-position="top" :model="form" @submit.prevent>
      <el-form-item label="Task Type" required>
        <el-segmented v-model="form.taskType" :options="taskTypeOptions" />
      </el-form-item>
      <el-form-item label="Project Key" required>
        <el-input v-model="form.projectKey" placeholder="user-service" />
      </el-form-item>
      <el-form-item label="GitLab MR URL">
        <el-input v-model="form.gitlabMrUrl" placeholder="https://gitlab.example.com/group/project/-/merge_requests/128" />
      </el-form-item>
      <el-form-item label="Jenkins Build URL">
        <el-input v-model="form.jenkinsBuildUrl" placeholder="https://jenkins.example.com/job/user-service-ci/102" />
      </el-form-item>
      <el-form-item label="SonarQube Project Key">
        <el-input v-model="form.sonarqubeProjectKey" placeholder="user-service" />
      </el-form-item>
      <el-form-item label="Jira Issue Key">
        <el-input v-model="form.jiraIssueKey" placeholder="USER-123" />
      </el-form-item>
      <el-form-item label="Confluence Page URL">
        <el-input v-model="form.confluencePageUrl" />
      </el-form-item>
      <el-form-item label="OpenAPI / Apifox / YApi URL">
        <el-input v-model="form.openApiUrl" />
      </el-form-item>
      <div class="toolbar">
        <el-button type="primary" :icon="CirclePlus" :loading="store.loading" @click="submit">提交</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CirclePlus, RefreshLeft } from '@element-plus/icons-vue'
import { useTaskStore } from '../store/task'
import type { CreateAgentTaskRequest } from '../types/task'

const router = useRouter()
const store = useTaskStore()
const taskTypeOptions = ['CI_FAILURE_ANALYSIS', 'MR_RISK_ANALYSIS']

const initial = (): CreateAgentTaskRequest => ({
  taskType: 'CI_FAILURE_ANALYSIS',
  projectKey: '',
  gitlabMrUrl: '',
  jenkinsBuildUrl: '',
  sonarqubeProjectKey: ''
})

const form = reactive<CreateAgentTaskRequest>(initial())

function reset() {
  Object.assign(form, initial())
}

async function submit() {
  if (!form.projectKey) {
    ElMessage.error('Project Key 必填')
    return
  }
  const task = await store.create({ ...form })
  router.push(`/tasks/${task.taskNo}`)
}
</script>

<style scoped>
.form {
  max-width: 920px;
}
</style>
