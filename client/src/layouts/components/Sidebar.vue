<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import {
  HomeFilled,
  DataAnalysis,
  Wallet,
  Document,
  User,
  UserFilled,
  ChatDotRound,
  OfficeBuilding,
  Fold,
  Expand,
  Tickets,
} from '@element-plus/icons-vue'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

interface MenuEntry {
  path: string
  title: string
  icon: typeof HomeFilled
  roles: string[]
}

const allMenus: MenuEntry[] = [
  { path: '/home', title: '首页', icon: HomeFilled, roles: ['owner'] },
  { path: '/dashboard', title: '数据看板', icon: DataAnalysis, roles: ['owner'] },
  { path: '/finance', title: '财务账本', icon: Wallet, roles: ['owner', 'staff'] },
  { path: '/tax', title: '税务档案', icon: Document, roles: ['owner', 'staff'] },
  { path: '/employee', title: '员工名册', icon: User, roles: ['owner', 'staff'] },
  { path: '/audit', title: '审计日志', icon: Tickets, roles: ['owner'] },
  { path: '/users', title: '用户管理', icon: UserFilled, roles: ['owner', 'admin'] },
  { path: '/ai-chat', title: 'AI 智能助理', icon: ChatDotRound, roles: ['owner'] },
]

const visibleMenus = computed(() => {
  const role = userStore.role
  if (role === 'admin') {
    return [
      { path: '/admin/company', title: '租户管理', icon: OfficeBuilding, roles: ['admin'] },
    ]
  }
  return allMenus.filter((m) => m.roles.includes(role))
})

const activePath = computed(() => route.path)

function navigate(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="sidebar" :class="{ collapsed }">
    <div class="sidebar-header">
      <template v-if="!collapsed">
        <span class="logo-text">EMS</span>
      </template>
      <template v-else>
        <span class="logo-text-mini">E</span>
      </template>
    </div>

    <nav class="sidebar-nav">
      <div
        class="nav-list"
        :id="userStore.role === 'staff' ? 'staff-guide-sidebar-menu' : undefined"
      >
        <div
          v-for="menu in visibleMenus"
          :key="menu.path"
          class="nav-item"
          :class="{ active: activePath === menu.path }"
          @click="navigate(menu.path)"
        >
          <el-icon :size="20"><component :is="menu.icon" /></el-icon>
          <span v-if="!collapsed" class="nav-label">{{ menu.title }}</span>
        </div>
      </div>
    </nav>

    <div class="sidebar-footer" @click="emit('toggle')">
      <el-icon :size="18">
        <Fold v-if="!collapsed" />
        <Expand v-else />
      </el-icon>
      <span v-if="!collapsed" class="nav-label">收起菜单</span>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 220px;
  background: #ffffff;
  border-right: 1px solid rgba(0,0,0,0.1);
  display: flex;
  flex-direction: column;
  z-index: 100;
  transition: width 0.3s ease;
}

.sidebar.collapsed {
  width: 64px;
}

.sidebar-header {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(0,0,0,0.1);
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #0075de;
  letter-spacing: -0.5px;
}

.logo-text-mini {
  font-size: 20px;
  font-weight: 700;
  color: #0075de;
  margin: 0 auto;
}

.sidebar-nav {
  flex: 1;
  padding: 8px 0;
  overflow-y: auto;
}

.nav-list {
  display: flex;
  flex-direction: column;
}

.nav-item {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 16px;
  cursor: pointer;
  color: rgba(0,0,0,0.65);
  font-size: 14px;
  font-weight: 500;
  gap: 8px;
  transition: all 0.2s ease;
  border-left: 3px solid transparent;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding: 0;
}

.nav-item:hover {
  background: #f6f5f4;
  color: rgba(0,0,0,0.95);
}

.nav-item.active {
  color: #0075de;
  background: #f2f9ff;
  border-left-color: #0075de;
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 16px;
  border-top: 1px solid rgba(0,0,0,0.1);
  cursor: pointer;
  color: rgba(0,0,0,0.45);
  font-size: 13px;
  gap: 8px;
  transition: color 0.2s;
}

.sidebar.collapsed .sidebar-footer {
  justify-content: center;
  padding: 0;
}

.sidebar-footer:hover {
  color: rgba(0,0,0,0.75);
}
</style>
