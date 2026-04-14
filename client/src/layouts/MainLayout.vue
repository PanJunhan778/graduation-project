<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './components/Sidebar.vue'

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
    <main class="main-content" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <div class="page-container" :class="pageContainerClass">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  min-height: 0;
  background:
    radial-gradient(circle at top right, rgba(0, 117, 222, 0.08), transparent 30%),
    linear-gradient(180deg, #f6f9fd 0%, #f4f2ef 100%);
}

.main-content {
  margin-left: 228px;
  width: calc(100vw - 228px);
  transition: margin-left 0.3s ease, width 0.3s ease;
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  overflow: hidden;
}

.main-content.sidebar-collapsed {
  margin-left: 80px;
  width: calc(100vw - 80px);
}

.page-container {
  flex: 1;
  padding: 16px;
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
