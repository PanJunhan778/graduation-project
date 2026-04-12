<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './components/Sidebar.vue'
import Topbar from './components/Topbar.vue'

const sidebarCollapsed = ref(false)
const route = useRoute()

const pageContainerClass = computed(() => ({
  'page-container--immersive': route.name === 'AiChat',
}))

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}
</script>

<template>
  <div class="main-layout">
    <Sidebar :collapsed="sidebarCollapsed" @toggle="toggleSidebar" />
    <div class="main-content" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <Topbar />
      <div class="page-container" :class="pageContainerClass">
        <router-view />
      </div>
    </div>
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  min-height: 0;
  background: #f6f5f4;
}

.main-content {
  margin-left: 220px;
  width: calc(100vw - 220px);
  transition: margin-left 0.3s ease, width 0.3s ease;
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  overflow: hidden;
}

.main-content.sidebar-collapsed {
  margin-left: 64px;
  width: calc(100vw - 64px);
}

.page-container {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  min-height: 0;
  box-sizing: border-box;
}

.page-container--immersive {
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.page-container--immersive > * {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
</style>
