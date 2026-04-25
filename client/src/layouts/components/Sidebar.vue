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
  Guide,
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
const STAFF_FINANCE_GUIDE_START_EVENT = 'staff-finance-guide:start'

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
const ownerGuideLabel = computed(() =>
  userStore.hasCompletedOwnerOnboardingTour() || userStore.hasDismissedOwnerOnboardingTour()
    ? '重新查看引导'
    : '查看功能引导',
)
const sidebarGuideLabel = computed(() => {
  if (userStore.role === 'owner') return ownerGuideLabel.value
  if (userStore.role === 'staff') return '查看功能引导'
  return ''
})
const showGuideEntry = computed(() => userStore.role === 'owner' || userStore.role === 'staff')

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

function startOwnerGuide() {
  window.dispatchEvent(new CustomEvent('owner-onboarding:start'))
}

async function startStaffGuide() {
  if (route.path !== '/finance') {
    await router.push('/finance')
  }
  await nextTick()
  window.dispatchEvent(new CustomEvent(STAFF_FINANCE_GUIDE_START_EVENT))
}

function startGuide() {
  if (userStore.role === 'owner') {
    startOwnerGuide()
    return
  }

  if (userStore.role === 'staff') {
    void startStaffGuide()
  }
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
      <div class="brand-mark">
        <div class="geometric-logo"></div>
      </div>
      <div v-if="!props.collapsed" class="brand-copy">
        <strong>数智引航</strong>
        <span>Smart Management</span>
      </div>
    </div>

    <nav class="sidebar-nav">
      <div
        class="nav-list"
        :id="userStore.role === 'staff' ? 'staff-guide-sidebar-menu' : undefined"
        :data-guide="userStore.role === 'owner' ? 'owner-sidebar-menu' : userStore.role === 'staff' ? 'staff-sidebar-menu' : undefined"
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
        v-if="showGuideEntry"
        type="button"
        class="guide-entry"
        :class="{ compact: props.collapsed }"
        :title="props.collapsed ? sidebarGuideLabel : undefined"
        @click="startGuide"
      >
        <el-icon :size="18"><Guide /></el-icon>
        <span v-if="!props.collapsed">{{ sidebarGuideLabel }}</span>
      </button>

      <button
        ref="profileTriggerRef"
        type="button"
        class="profile-trigger"
        :class="{ compact: props.collapsed, active: profileVisible }"
        :title="props.collapsed ? `${displayName} · ${roleLabel}` : undefined"
        :data-guide="userStore.role === 'owner' ? 'owner-profile-entry' : undefined"
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

.sidebar.collapsed .sidebar-header {
  justify-content: center;
  padding-inline: 0;
}

.brand-mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.geometric-logo {
  position: relative;
  width: 24px;
  height: 24px;
}

.geometric-logo::before,
.geometric-logo::after {
  content: '';
  position: absolute;
  border-radius: 6px;
  transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.geometric-logo::before {
  top: 0;
  left: 0;
  width: 16px;
  height: 16px;
  background: linear-gradient(135deg, #005bab 0%, #0075de 100%);
  z-index: 2;
  box-shadow: 0 4px 12px rgba(0, 117, 222, 0.2);
}

.geometric-logo::after {
  bottom: 0;
  right: 0;
  width: 16px;
  height: 16px;
  background: linear-gradient(135deg, #0075de 0%, #4292ff 100%);
  opacity: 0.85;
  z-index: 1;
}

.sidebar-header:hover .geometric-logo::before {
  transform: translate(2px, 2px) scale(1.05);
}

.sidebar-header:hover .geometric-logo::after {
  transform: translate(-2px, -2px) scale(1.05);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
  white-space: nowrap;
}

.brand-copy strong {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: rgba(15, 23, 42, 0.94);
}

.brand-copy span {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #8c97a8;
  text-transform: uppercase;
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
.collapse-control,
.guide-entry {
  width: 100%;
  border: none;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  cursor: pointer;
}

.guide-entry {
  min-height: 42px;
  padding: 11px 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: 1px solid rgba(13, 102, 194, 0.14);
  background: linear-gradient(135deg, rgba(20, 115, 230, 0.09), rgba(255, 255, 255, 0.88));
  color: #0d66c2;
  font-size: 13px;
  font-weight: 800;
  box-shadow: 0 12px 24px rgba(13, 102, 194, 0.08);
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.guide-entry:hover,
.guide-entry:focus-visible {
  transform: translateY(-2px);
  border-color: rgba(13, 102, 194, 0.24);
  background: #ffffff;
  color: #075aaa;
  box-shadow: 0 16px 28px rgba(13, 102, 194, 0.14);
  outline: none;
}

.guide-entry.compact {
  padding-inline: 0;
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
