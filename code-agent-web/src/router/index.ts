import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import AgentTaskCreate from '../views/AgentTaskCreate.vue'
import AgentTaskDetail from '../views/AgentTaskDetail.vue'
import EvidenceList from '../views/EvidenceList.vue'
import RagCenter from '../views/RagCenter.vue'
import MemoryCenter from '../views/MemoryCenter.vue'
import McpTools from '../views/McpTools.vue'
import ToolAudit from '../views/ToolAudit.vue'
import IntegrationConfig from '../views/IntegrationConfig.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Dashboard },
    { path: '/tasks/create', component: AgentTaskCreate },
    { path: '/tasks/:taskNo', component: AgentTaskDetail, props: true },
    { path: '/evidence', component: EvidenceList },
    { path: '/rag', component: RagCenter },
    { path: '/memory', component: MemoryCenter },
    { path: '/tools', component: McpTools },
    { path: '/audit', component: ToolAudit },
    { path: '/integrations', component: IntegrationConfig }
  ]
})
