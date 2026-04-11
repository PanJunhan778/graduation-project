<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
  background: #f6f5f4;
  padding: 24px;
}

.register-container {
  width: 100%;
  max-width: 460px;
}

.register-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 40px;
  box-shadow:
    rgba(0,0,0,0.04) 0px 4px 18px,
    rgba(0,0,0,0.027) 0px 2.025px 7.84688px,
    rgba(0,0,0,0.02) 0px 0.8px 2.925px,
    rgba(0,0,0,0.01) 0px 0.175px 1.04062px;
}

.card-header {
  margin-bottom: 28px;
}

.card-title {
  font-size: 22px;
  font-weight: 700;
  color: rgba(0,0,0,0.95);
  margin-bottom: 6px;
  letter-spacing: -0.25px;
}

.card-subtitle {
  font-size: 14px;
  color: #615d59;
}

.code-hint {
  font-size: 12px;
  color: #a39e98;
  margin-top: -12px;
  margin-bottom: 16px;
}

.custom-error {
  color: #e03e3e;
  font-size: 12px;
}

.register-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 4px;
}

.card-footer {
  text-align: center;
  margin-top: 20px;
}

.footer-text {
  font-size: 14px;
  color: #615d59;
}

.login-link {
  font-size: 14px;
  color: #0075de;
  text-decoration: none;
  font-weight: 500;
  margin-left: 4px;
}

.login-link:hover {
  text-decoration: underline;
}
</style>
