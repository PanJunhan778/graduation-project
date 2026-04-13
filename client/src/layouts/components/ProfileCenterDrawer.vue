<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { changePassword, getCurrentProfile, updateCompanySettings, updateCurrentProfile } from '@/api/profile'
import { useUserStore } from '@/store/user'
import type {
  ChangePasswordForm,
  CompanySettingsForm,
  ProfileVO,
  UpdateProfileForm,
} from '@/types'
import type { FormInstance, FormRules } from 'element-plus'

const visible = defineModel<boolean>({ default: false })

const userStore = useUserStore()

const loadingProfile = ref(false)
const profile = ref<ProfileVO | null>(null)

const accountFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const companyFormRef = ref<FormInstance>()

const accountSubmitting = ref(false)
const passwordSubmitting = ref(false)
const companySubmitting = ref(false)

const accountForm = reactive<UpdateProfileForm>({
  realName: '',
})

const passwordForm = reactive<ChangePasswordForm>({
  oldPassword: '',
  newPassword: '',
})

const companyForm = reactive<CompanySettingsForm>({
  name: '',
  industry: '',
  taxpayerType: '',
  description: '',
})

const accountRules: FormRules<UpdateProfileForm> = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
}

const passwordRules: FormRules<ChangePasswordForm> = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d).{8,}$/,
      message: '至少8位，须包含大写字母、小写字母和数字',
      trigger: 'blur',
    },
  ],
}

const companyRules: FormRules<CompanySettingsForm> = {
  name: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
}

const isOwner = computed(() => profile.value?.role === 'owner')

watch(visible, (open) => {
  if (!open) {
    return
  }
  void loadProfile()
})

async function loadProfile() {
  loadingProfile.value = true
  try {
    const res = await getCurrentProfile()
    profile.value = res.data
    accountForm.realName = res.data.realName || ''
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    companyForm.name = res.data.companyName || ''
    companyForm.industry = res.data.industry || ''
    companyForm.taxpayerType = res.data.taxpayerType || ''
    companyForm.description = res.data.companyDescription || ''
  } finally {
    loadingProfile.value = false
  }
}

async function handleSubmitAccount() {
  if (!accountFormRef.value) return
  await accountFormRef.value.validate()
  accountSubmitting.value = true
  try {
    const res = await updateCurrentProfile({
      realName: accountForm.realName.trim(),
    })
    profile.value = res.data
    userStore.patchDisplayProfile({
      realName: res.data.realName,
    })
    ElMessage.success('个人信息更新成功')
  } finally {
    accountSubmitting.value = false
  }
}

async function handleSubmitPassword() {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate()
  passwordSubmitting.value = true
  try {
    await changePassword(passwordForm)
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordFormRef.value.clearValidate()
    ElMessage.success('密码修改成功')
  } finally {
    passwordSubmitting.value = false
  }
}

async function handleSubmitCompany() {
  if (!companyFormRef.value) return
  await companyFormRef.value.validate()
  companySubmitting.value = true
  try {
    const res = await updateCompanySettings({
      name: companyForm.name.trim(),
      industry: companyForm.industry.trim(),
      taxpayerType: companyForm.taxpayerType.trim(),
      description: companyForm.description.trim(),
    })
    profile.value = res.data
    userStore.patchDisplayProfile({
      companyName: res.data.companyName,
      companyCode: res.data.companyCode,
      industry: res.data.industry,
      taxpayerType: res.data.taxpayerType,
    })
    ElMessage.success('公司配置更新成功')
  } finally {
    companySubmitting.value = false
  }
}

function getRoleName(role?: string): string {
  const map: Record<string, string> = {
    admin: '系统管理员',
    owner: '企业负责人',
    staff: '录入员',
  }
  return role ? map[role] || role : '--'
}
</script>

<template>
  <el-drawer
    v-model="visible"
    title="个人中心"
    direction="rtl"
    size="520px"
    destroy-on-close
  >
    <div class="profile-center">
      <el-skeleton :loading="loadingProfile" animated>
        <template #template>
          <div class="skeleton-stack">
            <div class="skeleton-card ds-card" />
            <div class="skeleton-card ds-card" />
            <div class="skeleton-card ds-card" />
          </div>
        </template>

        <template #default>
          <section class="profile-summary ds-card">
            <div class="profile-summary__avatar">
              {{ profile?.realName?.charAt(0) || 'U' }}
            </div>
            <div class="profile-summary__content">
              <h3>{{ profile?.realName || '--' }}</h3>
              <p>{{ getRoleName(profile?.role) }}</p>
              <div class="profile-summary__meta">
                <span>账号：{{ profile?.username || '--' }}</span>
                <span>企业：{{ profile?.companyName || '平台后台' }}</span>
              </div>
            </div>
          </section>

          <section class="profile-panel ds-card">
            <div class="panel-heading">
              <h4>账号设置</h4>
              <p>修改你的显示姓名，顶部状态栏会即时同步。</p>
            </div>

            <el-form
              ref="accountFormRef"
              :model="accountForm"
              :rules="accountRules"
              label-position="top"
            >
              <el-form-item label="用户名">
                <el-input :model-value="profile?.username || ''" disabled />
              </el-form-item>
              <el-form-item label="真实姓名" prop="realName">
                <el-input v-model="accountForm.realName" maxlength="50" show-word-limit />
              </el-form-item>
              <div class="panel-actions">
                <el-button type="primary" :loading="accountSubmitting" @click="handleSubmitAccount">
                  保存账号信息
                </el-button>
              </div>
            </el-form>
          </section>

          <section class="profile-panel ds-card">
            <div class="panel-heading">
              <h4>修改密码</h4>
              <p>密码至少 8 位，须包含大写字母、小写字母和数字。</p>
            </div>

            <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-position="top"
            >
              <el-form-item label="旧密码" prop="oldPassword">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="passwordForm.newPassword" type="password" show-password />
              </el-form-item>
              <div class="panel-actions">
                <el-button type="primary" :loading="passwordSubmitting" @click="handleSubmitPassword">
                  更新密码
                </el-button>
              </div>
            </el-form>
          </section>

          <section v-if="isOwner" class="profile-panel ds-card">
            <div class="panel-heading">
              <h4>公司配置</h4>
              <p>这里维护企业基础展示信息与企业画像，AI 企业画像会复用同一字段。</p>
            </div>

            <el-form
              ref="companyFormRef"
              :model="companyForm"
              :rules="companyRules"
              label-position="top"
            >
              <el-form-item label="企业码">
                <el-input :model-value="profile?.companyCode || ''" disabled />
              </el-form-item>
              <el-form-item label="公司名称" prop="name">
                <el-input v-model="companyForm.name" maxlength="100" show-word-limit />
              </el-form-item>
              <div class="grid-two">
                <el-form-item label="所属行业">
                  <el-input v-model="companyForm.industry" maxlength="50" show-word-limit />
                </el-form-item>
                <el-form-item label="纳税人类型">
                  <el-input v-model="companyForm.taxpayerType" maxlength="20" show-word-limit />
                </el-form-item>
              </div>
              <el-form-item label="企业画像">
                <el-input
                  v-model="companyForm.description"
                  type="textarea"
                  :rows="5"
                  maxlength="500"
                  show-word-limit
                  placeholder="例如：主营跨境贸易与供应链服务，核心客户集中在华南制造业。"
                />
              </el-form-item>
              <div class="panel-actions">
                <el-button type="primary" :loading="companySubmitting" @click="handleSubmitCompany">
                  保存公司配置
                </el-button>
              </div>
            </el-form>
          </section>
        </template>
      </el-skeleton>
    </div>
  </el-drawer>
</template>

<style scoped>
.profile-center {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.skeleton-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.skeleton-card {
  height: 180px;
}

.profile-summary,
.profile-panel {
  padding: 20px;
}

.profile-summary {
  display: flex;
  gap: 16px;
  align-items: center;
  background:
    radial-gradient(circle at top right, rgba(0, 117, 222, 0.12), transparent 34%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 48%, #f6f5f4 100%);
}

.profile-summary__avatar {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: linear-gradient(135deg, #2b7fff 0%, #0f62d6 100%);
  color: #ffffff;
  display: grid;
  place-items: center;
  font-size: 22px;
  font-weight: 700;
  box-shadow: 0 18px 34px rgba(15, 98, 214, 0.22);
}

.profile-summary__content h3 {
  font-size: 24px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.95);
}

.profile-summary__content p {
  margin-top: 4px;
  font-size: 14px;
  color: #615d59;
}

.profile-summary__meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
  color: #615d59;
}

.panel-heading h4 {
  font-size: 18px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.95);
}

.panel-heading p {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #615d59;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
}

.grid-two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 640px) {
  .grid-two {
    grid-template-columns: 1fr;
  }
}
</style>
