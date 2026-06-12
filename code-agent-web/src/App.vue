<template>
  <el-container class="shell">
    <el-aside width="232px" class="sidebar">
      <div class="brand">CodeAgent</div>
      <el-menu router :default-active="$route.path" class="nav">
        <el-menu-item index="/">
          <el-icon><DataBoard /></el-icon>
          <span>Dashboard</span>
        </el-menu-item>
        <el-menu-item index="/tasks/create">
          <el-icon><CirclePlus /></el-icon>
          <span>创建任务</span>
        </el-menu-item>
        <el-menu-item index="/evidence">
          <el-icon><DocumentChecked /></el-icon>
          <span>Evidence</span>
        </el-menu-item>
        <el-menu-item index="/rag">
          <el-icon><Collection /></el-icon>
          <span>RAG Center</span>
        </el-menu-item>
        <el-menu-item index="/memory">
          <el-icon><Memo /></el-icon>
          <span>Memory</span>
        </el-menu-item>
        <el-menu-item index="/tools">
          <el-icon><Operation /></el-icon>
          <span>MCP Tools</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><Tickets /></el-icon>
          <span>Tool Audit</span>
        </el-menu-item>
        <el-menu-item index="/integrations">
          <el-icon><Link /></el-icon>
          <span>Integrations</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <span>研发多 Agent 缺陷定位与修复辅助系统</span>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import {
  CirclePlus,
  Collection,
  DataBoard,
  DocumentChecked,
  Link,
  Memo,
  Operation,
  Tickets
} from '@element-plus/icons-vue'
</script>

<style scoped>
.shell {
  min-height: 100vh;
}

.sidebar {
  background: #16202a;
  color: #fff;
}

.brand {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  font-size: 18px;
  font-weight: 700;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.nav {
  border-right: 0;
  background: transparent;
}

.nav :deep(.el-menu-item) {
  color: #d6dee6;
}

.nav :deep(.el-menu-item.is-active) {
  color: #fff;
  background: #25636b;
}

.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #dfe5ec;
  background: #fff;
  color: #44505c;
}

.content {
  background: #f6f8fb;
  padding: 20px;
}
</style>
