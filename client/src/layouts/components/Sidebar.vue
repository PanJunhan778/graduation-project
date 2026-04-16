<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import SidebarProfilePopover from './SidebarProfilePopover.vue'
import {
  ChatDotRound,
  DataAnalysis,
  Document,
  Expand,
  Fold,
  HomeFilled,
  OfficeBuilding,
  Tickets,
  User,
  UserFilled,
  Wallet,
} from '@element-plus/icons-vue'

const props = defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const profileVisible = ref(false)
const profileTriggerRef = ref<HTMLElement | null>(null)
const profileAnchorRect = ref<{
  top: number
  right: number
  bottom: number
  left: number
  width: number
  height: number
} | null>(null)

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
  return allMenus.filter((menu) => menu.roles.includes(role))
})

const activePath = computed(() => route.path)
const displayName = computed(() => userStore.realName || '当前用户')
const roleLabel = computed(() => getRoleName(userStore.role))
const avatarText = computed(() => displayName.value.charAt(0).toUpperCase() || 'U')

watch(
  () => route.fullPath,
  () => {
    closeProfilePopover()
  },
)

watch(
  () => props.collapsed,
  async () => {
    if (!profileVisible.value) return
    await nextTick()
    syncProfileAnchorRect()
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleWindowResize)
})

function navigate(path: string) {
  router.push(path)
}

function toggleProfilePopover() {
  if (profileVisible.value) {
    closeProfilePopover()
    return
  }

  syncProfileAnchorRect()
  profileVisible.value = true
  window.addEventListener('resize', handleWindowResize)
}

function closeProfilePopover() {
  profileVisible.value = false
  window.removeEventListener('resize', handleWindowResize)
}

function syncProfileAnchorRect() {
  const rect = profileTriggerRef.value?.getBoundingClientRect()
  if (!rect) {
    profileAnchorRect.value = null
    return
  }

  profileAnchorRect.value = {
    top: rect.top,
    right: rect.right,
    bottom: rect.bottom,
    left: rect.left,
    width: rect.width,
    height: rect.height,
  }
}

function handleWindowResize() {
  if (!profileVisible.value) return
  syncProfileAnchorRect()
}

function getRoleName(role?: string) {
  const map: Record<string, string> = {
    admin: '系统管理员',
    owner: '老板',
    staff: '数据填写员',
  }
  return role ? map[role] || role : '当前角色'
}
</script>

<template>
  <aside class="sidebar" :class="{ collapsed: props.collapsed }">
    <div class="sidebar-header">
      <div class="brand-mark">{{ props.collapsed ? 'E' : 'ERP' }}</div>
      <div v-if="!props.collapsed" class="brand-copy">
        <strong>Enterprise Resource Planning</strong>
        <span>智能轻量化企业管理系统</span>
      </div>
    </div>

    <nav class="sidebar-nav">
      <div
        class="nav-list"
        :id="userStore.role === 'staff' ? 'staff-guide-sidebar-menu' : undefined"
      >
        <button
          v-for="menu in visibleMenus"
          :key="menu.path"
          type="button"
          class="nav-item"
          :class="{ active: activePath === menu.path }"
          :title="props.collapsed ? menu.title : undefined"
          @click="navigate(menu.path)"
        >
          <el-icon :size="20"><component :is="menu.icon" /></el-icon>
          <span v-if="!props.collapsed" class="nav-label">{{ menu.title }}</span>
        </button>
      </div>
    </nav>

    <div class="sidebar-bottom">
      <button
        ref="profileTriggerRef"
        type="button"
        class="profile-trigger"
        :class="{ compact: props.collapsed, active: profileVisible }"
        :title="props.collapsed ? `${displayName} · ${roleLabel}` : undefined"
        @click="toggleProfilePopover"
      >
        <span class="profile-avatar">{{ avatarText }}</span>
        <span v-if="!props.collapsed" class="profile-copy">
          <strong>{{ displayName }}</strong>
          <small>{{ roleLabel }}</small>
        </span>
      </button>

      <button type="button" class="collapse-control" @click="emit('toggle')">
        <el-icon :size="18">
          <Fold v-if="!props.collapsed" />
          <Expand v-else />
        </el-icon>
        <span v-if="!props.collapsed">收起菜单</span>
      </button>
    </div>

    <SidebarProfilePopover
      :visible="profileVisible"
      :anchor-rect="profileAnchorRect"
      @close="closeProfilePopover"
    />
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  width: 228px;
  padding: 18px 14px 16px;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top left, rgba(0, 117, 222, 0.08), transparent 28%),
    linear-gradient(180deg, #ffffff 0%, #f8fbff 54%, #f5f4f2 100%);
  border-right: 1px solid rgba(31, 41, 55, 0.08);
  display: flex;
  flex-direction: column;
  gap: 18px;
  z-index: 100;
  transition: width 0.3s ease, padding 0.3s ease;
}

.sidebar.collapsed {
  width: 80px;
  padding-inline: 10px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 64px;
  padding: 8px 6px 10px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #1678e5 0%, #3d92ff 100%);
  color: #ffffff;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 0.04em;
  box-shadow: 0 16px 28px rgba(22, 120, 229, 0.22);
  flex-shrink: 0;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.brand-copy strong {
  font-size: 15px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.94);
}

.brand-copy span {
  font-size: 12px;
  color: #667085;
}

.sidebar-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nav-item {
  width: 100%;
  border: none;
  border-radius: 18px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: transparent;
  color: #556072;
  cursor: pointer;
  text-align: left;
  transition:
    transform 0.18s ease,
    background 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding-inline: 0;
}

.nav-item:hover {
  transform: translateX(2px);
  background: rgba(255, 255, 255, 0.92);
  color: rgba(15, 23, 42, 0.96);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.06);
}

.nav-item.active {
  background: linear-gradient(135deg, rgba(0, 117, 222, 0.12), rgba(59, 130, 246, 0.04));
  color: #0d66c2;
  box-shadow: 0 16px 28px rgba(13, 102, 194, 0.12);
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 600;
}

.sidebar-bottom {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 10px;
}

.profile-trigger,
.collapse-control {
  width: 100%;
  border: none;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  cursor: pointer;
}

.profile-trigger {
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.profile-trigger:hover,
.profile-trigger.active {
  transform: translateY(-2px);
  background: #ffffff;
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.1);
}

.profile-trigger.compact {
  justify-content: center;
  padding-inline: 0;
}

.profile-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #2576e8 0%, #7c4dff 100%);
  color: #ffffff;
  font-size: 18px;
  font-weight: 700;
  flex-shrink: 0;
}

.profile-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  text-align: left;
}

.profile-copy strong {
  font-size: 14px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.95);
}

.profile-copy small {
  color: #667085;
  font-size: 12px;
}

.collapse-control {
  padding: 11px 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #5d6779;
  font-size: 13px;
  font-weight: 600;
  transition: color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.collapse-control:hover {
  color: rgba(15, 23, 42, 0.94);
  background: #ffffff;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}
</style>
