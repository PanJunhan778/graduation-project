<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tools, House, Wallet } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const pageTitle = computed(() => String(route.meta.title || '功能建设中'))
const description = computed(() => {
  const metaDescription = route.meta.comingSoonDescription
  if (typeof metaDescription === 'string' && metaDescription.trim()) {
    return metaDescription
  }
  return '这一块能力正在按计划推进，当前先为你保留入口与信息结构。'
})

const fallbackPath = computed(() => (userStore.role === 'staff' ? '/finance' : '/home'))
const fallbackLabel = computed(() => (userStore.role === 'staff' ? '返回财务账本' : '返回首页驾驶舱'))

function goBackToWork() {
  router.push(fallbackPath.value)
}
</script>

<template>
  <div class="coming-soon-page">
    <div class="coming-soon-card ds-card">
      <div class="coming-soon-badge">
        <el-icon><Tools /></el-icon>
        <span>规划中</span>
      </div>

      <h1 class="coming-soon-title">{{ pageTitle }}</h1>
      <p class="coming-soon-description">{{ description }}</p>

      <div class="coming-soon-highlights">
        <div class="highlight-item">
          <el-icon><House /></el-icon>
          <span>入口已预留，后续将直接接入正式业务能力。</span>
        </div>
        <div class="highlight-item">
          <el-icon><Wallet /></el-icon>
          <span>当前阶段优先保证 M8 首页驾驶舱与基础数据链路完整可用。</span>
        </div>
      </div>

      <div class="coming-soon-actions">
        <el-button type="primary" @click="goBackToWork">{{ fallbackLabel }}</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.coming-soon-page {
  min-height: calc(100vh - 104px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
}

.coming-soon-card {
  width: min(760px, 100%);
  padding: 40px;
}

.coming-soon-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 9999px;
  background: #f2f9ff;
  color: #097fe8;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.125px;
}

.coming-soon-title {
  margin-top: 18px;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -1px;
  color: rgba(0, 0, 0, 0.95);
}

.coming-soon-description {
  margin-top: 14px;
  font-size: 15px;
  line-height: 1.7;
  color: #615d59;
  max-width: 560px;
}

.coming-soon-highlights {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 28px;
}

.highlight-item {
  display: flex;
  gap: 10px;
  padding: 16px 18px;
  border-radius: 12px;
  background: #f6f5f4;
  color: rgba(0, 0, 0, 0.75);
  font-size: 14px;
  line-height: 1.6;
}

.highlight-item :deep(.el-icon) {
  margin-top: 2px;
  color: #0075de;
  flex-shrink: 0;
}

.coming-soon-actions {
  margin-top: 28px;
}

@media (max-width: 768px) {
  .coming-soon-card {
    padding: 28px 24px;
  }

  .coming-soon-title {
    font-size: 28px;
  }

  .coming-soon-highlights {
    grid-template-columns: 1fr;
  }
}
</style>
