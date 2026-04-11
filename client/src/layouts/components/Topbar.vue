<script setup lang="ts">
import { useUserStore } from '@/store/user'
import { SwitchButton } from '@element-plus/icons-vue'

const userStore = useUserStore()

const isBusinessUser = () => userStore.role === 'owner' || userStore.role === 'staff'

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    userStore.logout()
  } catch {
    /* cancelled */
  }
}

function getRoleName(role: string): string {
  const map: Record<string, string> = {
    admin: '系统管理员',
    owner: '企业负责人',
    staff: '录入员',
  }
  return map[role] || role
}
</script>

<template>
  <div class="topbar">
    <div class="topbar-left">
      <template v-if="isBusinessUser() && userStore.companyName">
        <span class="company-name">{{ userStore.companyName }}</span>
        <span v-if="userStore.companyCode" class="company-code">{{ userStore.companyCode }}</span>
        <el-divider v-if="userStore.industry || userStore.taxpayerType" direction="vertical" />
        <span v-if="userStore.industry" class="company-meta">{{ userStore.industry }}</span>
        <span v-if="userStore.taxpayerType" class="company-meta taxpayer-tag">{{ userStore.taxpayerType }}</span>
      </template>
      <span v-else class="company-name">管理后台</span>
    </div>

    <div class="topbar-right">
      <el-dropdown trigger="click">
        <div class="user-avatar-area">
          <div class="avatar-circle">
            {{ userStore.realName?.charAt(0) || 'U' }}
          </div>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              <div class="user-info-card">
                <div class="user-name">{{ userStore.realName }}</div>
                <div class="user-role">{{ getRoleName(userStore.role) }}</div>
              </div>
            </el-dropdown-item>
            <el-dropdown-item divided :icon="SwitchButton" @click="handleLogout">
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.topbar {
  height: 56px;
  background: #ffffff;
  border-bottom: 1px solid rgba(0,0,0,0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.company-name {
  font-size: 15px;
  font-weight: 600;
  color: rgba(0,0,0,0.95);
}

.company-code {
  display: inline-block;
  padding: 1px 8px;
  background: #f2f9ff;
  color: #097fe8;
  font-size: 12px;
  font-weight: 600;
  border-radius: 9999px;
  letter-spacing: 0.5px;
  font-variant-numeric: tabular-nums;
}

.company-meta {
  font-size: 13px;
  color: #615d59;
}

.taxpayer-tag {
  padding: 1px 8px;
  background: #f6f5f4;
  border-radius: 4px;
  font-size: 12px;
}

.topbar-right {
  display: flex;
  align-items: center;
}

.user-avatar-area {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 8px;
}

.avatar-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #0075de;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  border: 1px solid rgba(0,0,0,0.1);
}

.user-info-card {
  padding: 4px 0;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: rgba(0,0,0,0.95);
}

.user-role {
  font-size: 12px;
  color: #615d59;
  margin-top: 2px;
}
</style>
