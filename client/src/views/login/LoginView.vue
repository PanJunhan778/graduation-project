<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import type { FormInstance, FormRules } from 'element-plus'
import type { LoginForm, RoleGuide } from '@/types'
import { Lock, User } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const selectedRole = ref<RoleGuide>('owner')

const roleOptions = [
  {
    key: 'admin' as RoleGuide,
    label: '平台管理员',
    desc: '管理租户与系统配置',
    icon: '🛡️',
  },
  {
    key: 'owner' as RoleGuide,
    label: '企业负责人',
    desc: '查看经营数据与决策分析',
    icon: '👔',
  },
  {
    key: 'staff' as RoleGuide,
    label: '企业员工',
    desc: '录入与管理业务数据',
    icon: '📋',
  },
]

const welcomeText = computed(() => {
  const map: Record<RoleGuide, string> = {
    admin: '欢迎回来，系统管理员',
    owner: '欢迎回来，掌握全局',
    staff: '欢迎回来，开始工作',
  }
  return map[selectedRole.value]
})

const subtitleText = computed(() => {
  const map: Record<RoleGuide, string> = {
    admin: '登录以管理平台租户与系统配置',
    owner: '登录以查看企业经营数据与智能分析',
    staff: '登录以进行财务与人事数据录入',
  }
  return map[selectedRole.value]
})

const form = reactive<LoginForm>({
  username: '',
  password: '',
})

const rules: FormRules<LoginForm> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    /* error handled by interceptor */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-container">
      <!-- Left Branding -->
      <div class="login-branding">
        <div class="brand-content">
          <h1 class="brand-title">智能轻量化<br />企业管理系统</h1>
          <p class="brand-subtitle">
            数据驱动决策，AI 赋能经营
          </p>
          <div class="brand-features">
            <div class="feature-item">
              <span class="feature-dot" />
              <span>统一数据录入底座</span>
            </div>
            <div class="feature-item">
              <span class="feature-dot" />
              <span>深度 BI 数据看板</span>
            </div>
            <div class="feature-item">
              <span class="feature-dot" />
              <span>AI 智能经营助理</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Form -->
      <div class="login-form-area">
        <div class="form-wrapper">
          <!-- Role Guide -->
          <div class="role-guide">
            <div
              v-for="opt in roleOptions"
              :key="opt.key"
              class="role-card"
              :class="{ active: selectedRole === opt.key }"
              @click="selectedRole = opt.key"
            >
              <span class="role-icon">{{ opt.icon }}</span>
              <span class="role-label">{{ opt.label }}</span>
            </div>
          </div>

          <!-- Welcome -->
          <h2 class="form-title">{{ welcomeText }}</h2>
          <p class="form-subtitle">{{ subtitleText }}</p>

          <!-- Form -->
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            size="large"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="loading"
                class="login-btn"
                @click="handleLogin"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span class="footer-text">企业员工？</span>
            <router-link to="/register" class="register-link">注册账号</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(135deg, rgba(0, 117, 222, 0.08) 0%, rgba(246, 250, 255, 0.72) 30%, rgba(246, 245, 244, 0.96) 100%);
  padding: 32px;
  position: relative;
  overflow: hidden;
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(13, 102, 194, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(13, 102, 194, 0.04) 1px, transparent 1px);
  background-size: 36px 36px;
  mask-image: linear-gradient(135deg, rgba(0, 0, 0, 0.72), transparent 72%);
  pointer-events: none;
}

.login-container {
  display: flex;
  width: 100%;
  max-width: 1080px;
  min-height: 600px;
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.98), rgba(248, 251, 255, 0.94));
  border: 1px solid rgba(52, 78, 121, 0.12);
  border-radius: 28px;
  overflow: hidden;
  box-shadow: 0 34px 82px rgba(15, 23, 42, 0.14);
  position: relative;
  z-index: 1;
}

/* Left Branding */
.login-branding {
  flex: 0 0 440px;
  background:
    linear-gradient(145deg, rgba(0, 117, 222, 0.96) 0%, rgba(13, 102, 194, 0.98) 45%, #213183 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px;
  position: relative;
  overflow: hidden;
}

.login-branding::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(120deg, transparent 0 40%, rgba(255, 255, 255, 0.1) 40% 40.8%, transparent 41% 100%),
    repeating-linear-gradient(0deg, rgba(255, 255, 255, 0.06) 0 1px, transparent 1px 18px);
  opacity: 0.55;
}

.login-branding::after {
  content: '';
  position: absolute;
  right: -24%;
  top: 0;
  width: 42%;
  height: 100%;
  background: rgba(255, 255, 255, 0.08);
  transform: skewX(-14deg);
}

.brand-content {
  color: #ffffff;
  position: relative;
  z-index: 1;
}

.brand-title {
  font-size: 36px;
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: 0;
  margin-bottom: 18px;
  text-shadow: 0 16px 34px rgba(2, 20, 62, 0.22);
}

.brand-subtitle {
  font-size: 16px;
  font-weight: 600;
  opacity: 0.9;
  margin-bottom: 38px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
}

.feature-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 0 0 5px rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}

/* Right Form Area */
.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 250, 255, 0.58));
}

.form-wrapper {
  width: 100%;
  max-width: 390px;
}

/* Role Guide Cards */
.role-guide {
  display: flex;
  gap: 10px;
  margin-bottom: 34px;
}

.role-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 10px;
  border-radius: 14px;
  border: 1px solid rgba(31, 41, 55, 0.12);
  background: rgba(255, 255, 255, 0.86);
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
  gap: 8px;
}

.role-card:hover {
  transform: translateY(-2px);
  border-color: rgba(13, 102, 194, 0.24);
  background: #ffffff;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
}

.role-card.active {
  border-color: #0d66c2;
  background: linear-gradient(180deg, rgba(242, 249, 255, 0.94), rgba(255, 255, 255, 0.92));
  box-shadow: 0 18px 34px rgba(13, 102, 194, 0.12);
}

.role-icon {
  font-size: 22px;
  line-height: 1;
}

.role-label {
  font-size: 12px;
  font-weight: 800;
  color: #4b5565;
  white-space: nowrap;
}

.role-card.active .role-label {
  color: #0075de;
}

/* Form */
.form-title {
  font-size: 26px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.96);
  margin-bottom: 8px;
  letter-spacing: 0;
}

.form-subtitle {
  font-size: 14px;
  line-height: 1.7;
  color: #5f6675;
  margin-bottom: 30px;
}

.form-wrapper :deep(.el-form-item) {
  margin-bottom: 20px;
}

.form-wrapper :deep(.el-input__wrapper) {
  min-height: 52px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 0 1px rgba(31, 41, 55, 0.12) inset;
  transition:
    box-shadow 0.18s ease,
    background 0.18s ease,
    transform 0.18s ease;
}

.form-wrapper :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(13, 102, 194, 0.24) inset;
}

.form-wrapper :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow:
    0 0 0 1px #0d66c2 inset,
    0 12px 24px rgba(13, 102, 194, 0.1);
}

.login-btn {
  width: 100%;
  height: 52px;
  font-size: 15px;
  font-weight: 800;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #0d66c2, #3394f5);
  box-shadow: 0 16px 30px rgba(13, 102, 194, 0.24);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    background 0.18s ease;
}

.login-btn:hover,
.login-btn:focus-visible {
  transform: translateY(-1px);
  background: linear-gradient(135deg, #0b5cad, #2789ec);
  box-shadow: 0 20px 34px rgba(13, 102, 194, 0.3);
}

.form-footer {
  text-align: center;
  margin-top: 24px;
}

.footer-text {
  font-size: 14px;
  color: #615d59;
}

.register-link {
  font-size: 14px;
  color: #0075de;
  text-decoration: none;
  font-weight: 800;
  margin-left: 4px;
  transition: color 0.18s ease;
}

.register-link:hover {
  color: #005bab;
}

/* Responsive */
@media (max-width: 768px) {
  .login-branding {
    display: none;
  }

  .login-container {
    max-width: 420px;
    min-height: 0;
  }

  .login-form-area {
    padding: 32px 24px;
  }
}
</style>
