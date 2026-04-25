<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/auth'
import type { FormInstance, FormRules } from 'element-plus'
import type { RegisterForm } from '@/types'
import { Lock, User, Ticket, Postcard } from '@element-plus/icons-vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<RegisterForm>({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  companyCode: '',
})

const validatePassword = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入密码'))
    return
  }
  if (value.length < 8) {
    callback(new Error('密码至少 8 位'))
    return
  }
  if (!/[A-Z]/.test(value)) {
    callback(new Error('密码须包含大写字母'))
    return
  }
  if (!/[a-z]/.test(value)) {
    callback(new Error('密码须包含小写字母'))
    return
  }
  if (!/\d/.test(value)) {
    callback(new Error('密码须包含数字'))
    return
  }
  callback()
}

const validateConfirm = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validateCompanyCode = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入企业码'))
  } else if (value.length !== 6) {
    callback(new Error('企业码必须为 6 位'))
  } else {
    callback()
  }
}

const rules: FormRules<RegisterForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 个字符', trigger: 'blur' },
  ],
  password: [{ required: true, validator: validatePassword, trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  companyCode: [{ required: true, validator: validateCompanyCode, trigger: 'blur' }],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      realName: form.realName,
      companyCode: form.companyCode,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    /* error handled by interceptor */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="register-page">
    <div class="register-container">
      <div class="register-card">
        <div class="card-header">
          <h2 class="card-title">员工账号注册</h2>
          <p class="card-subtitle">请填写信息并输入企业码完成注册</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          label-position="top"
          @keyup.enter="handleRegister"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item label="真实姓名" prop="realName">
            <el-input
              v-model="form.realName"
              placeholder="请输入真实姓名"
              :prefix-icon="Postcard"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="至少 8 位，含大小写字母和数字"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item label="企业码" prop="companyCode">
            <el-input
              v-model="form.companyCode"
              placeholder="请输入 6 位企业码"
              :prefix-icon="Ticket"
              maxlength="6"
            />
            <template #error="{ error }">
              <div class="custom-error">{{ error }}</div>
            </template>
          </el-form-item>
          <div class="code-hint">企业码由您的公司负责人提供</div>

          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              class="register-btn"
              @click="handleRegister"
            >
              注 册
            </el-button>
          </el-form-item>
        </el-form>

        <div class="card-footer">
          <span class="footer-text">已有账号？</span>
          <router-link to="/login" class="login-link">返回登录</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
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

.register-page::before {
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

.register-container {
  width: 100%;
  max-width: 500px;
  position: relative;
  z-index: 1;
}

.register-card {
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.98), rgba(248, 251, 255, 0.94));
  border: 1px solid rgba(52, 78, 121, 0.12);
  border-radius: 28px;
  padding: 36px 42px 34px;
  box-shadow: 0 34px 82px rgba(15, 23, 42, 0.14);
  position: relative;
  overflow: hidden;
}

.register-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #0d66c2, #3394f5, #213183);
}

.card-header {
  margin-bottom: 24px;
}

.card-title {
  font-size: 28px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.96);
  margin-bottom: 8px;
  letter-spacing: 0;
}

.card-subtitle {
  font-size: 14px;
  line-height: 1.7;
  color: #5f6675;
}

.register-card :deep(.el-form-item) {
  margin-bottom: 16px;
}

.register-card :deep(.el-form-item__label) {
  margin-bottom: 8px;
  color: rgba(15, 23, 42, 0.88) !important;
  font-weight: 700 !important;
}

.register-card :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 0 1px rgba(31, 41, 55, 0.12) inset;
  transition:
    box-shadow 0.18s ease,
    background 0.18s ease;
}

.register-card :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(13, 102, 194, 0.24) inset;
}

.register-card :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow:
    0 0 0 1px #0d66c2 inset,
    0 12px 24px rgba(13, 102, 194, 0.1);
}

.code-hint {
  font-size: 12px;
  color: #7f8795;
  margin-top: -8px;
  margin-bottom: 14px;
}

.custom-error {
  color: #e03e3e;
  font-size: 12px;
}

.register-btn {
  width: 100%;
  height: 50px;
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

.register-btn:hover,
.register-btn:focus-visible {
  transform: translateY(-1px);
  background: linear-gradient(135deg, #0b5cad, #2789ec);
  box-shadow: 0 20px 34px rgba(13, 102, 194, 0.3);
}

.card-footer {
  text-align: center;
  margin-top: 18px;
}

.footer-text {
  font-size: 14px;
  color: #615d59;
}

.login-link {
  font-size: 14px;
  color: #0075de;
  text-decoration: none;
  font-weight: 800;
  margin-left: 4px;
  transition: color 0.18s ease;
}

.login-link:hover {
  color: #005bab;
}

@media (max-width: 640px) {
  .register-page {
    padding: 20px;
  }

  .register-card {
    padding: 34px 24px 30px;
    border-radius: 24px;
  }
}
</style>
