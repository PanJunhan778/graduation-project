<script setup lang="ts">
import { ref } from 'vue'
import Sidebar from './components/Sidebar.vue'
import Topbar from './components/Topbar.vue'

const sidebarCollapsed = ref(false)

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}
</script>

<template>
  <div class="main-layout">
    <Sidebar :collapsed="sidebarCollapsed" @toggle="toggleSidebar" />
    <div class="main-content" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <Topbar />
      <div class="page-container">
        <router-view />
      </div>
    </div>
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  min-height: 100vh;
  background: #f6f5f4;
}

.main-content {
  margin-left: 220px;
  width: calc(100vw - 220px);
  transition: margin-left 0.3s ease, width 0.3s ease;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow-x: hidden;
}

.main-content.sidebar-collapsed {
  margin-left: 64px;
  width: calc(100vw - 64px);
}

.page-container {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
