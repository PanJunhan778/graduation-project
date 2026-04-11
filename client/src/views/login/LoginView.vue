<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
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
  background: #f6f5f4;
  padding: 24px;
}

.login-container {
  display: flex;
  width: 100%;
  max-width: 960px;
  min-height: 560px;
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow:
    rgba(0,0,0,0.04) 0px 4px 18px,
    rgba(0,0,0,0.027) 0px 2.025px 7.84688px,
    rgba(0,0,0,0.02) 0px 0.8px 2.925px,
    rgba(0,0,0,0.01) 0px 0.175px 1.04062px;
}

/* Left Branding */
.login-branding {
  flex: 0 0 400px;
  background: linear-gradient(135deg, #0075de 0%, #213183 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.brand-content {
  color: #ffffff;
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -1px;
  margin-bottom: 16px;
}

.brand-subtitle {
  font-size: 16px;
  font-weight: 400;
  opacity: 0.85;
  margin-bottom: 32px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 500;
  opacity: 0.9;
}

.feature-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ffffff;
  flex-shrink: 0;
}

/* Right Form Area */
.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.form-wrapper {
  width: 100%;
  max-width: 360px;
}

/* Role Guide Cards */
.role-guide {
  display: flex;
  gap: 8px;
  margin-bottom: 32px;
}

.role-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px;
  border-radius: 8px;
  border: 1px solid rgba(0,0,0,0.1);
  cursor: pointer;
  transition: all 0.2s ease;
  gap: 4px;
}

.role-card:hover {
  border-color: rgba(0,117,222,0.3);
  background: #fafafa;
}

.role-card.active {
  border-color: #0075de;
  background: #f2f9ff;
}

.role-icon {
  font-size: 20px;
  line-height: 1;
}

.role-label {
  font-size: 12px;
  font-weight: 600;
  color: rgba(0,0,0,0.65);
  white-space: nowrap;
}

.role-card.active .role-label {
  color: #0075de;
}

/* Form */
.form-title {
  font-size: 22px;
  font-weight: 700;
  color: rgba(0,0,0,0.95);
  margin-bottom: 6px;
  letter-spacing: -0.25px;
}

.form-subtitle {
  font-size: 14px;
  color: #615d59;
  margin-bottom: 28px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 4px;
}

.form-footer {
  text-align: center;
  margin-top: 20px;
}

.footer-text {
  font-size: 14px;
  color: #615d59;
}

.register-link {
  font-size: 14px;
  color: #0075de;
  text-decoration: none;
  font-weight: 500;
  margin-left: 4px;
}

.register-link:hover {
  text-decoration: underline;
}

/* Responsive */
@media (max-width: 768px) {
  .login-branding {
    display: none;
  }

  .login-container {
    max-width: 420px;
  }

  .login-form-area {
    padding: 32px 24px;
  }
}
</style>
