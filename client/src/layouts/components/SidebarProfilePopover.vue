<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import {
  ChatDotRound,
  CloseBold,
  Lock,
  OfficeBuilding,
  SwitchButton,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { changePassword, getCurrentProfile, updateCompanySettings } from '@/api/profile'
import type { ChangePasswordForm, ProfileVO } from '@/types'

interface AnchorRect {
  top: number
  right: number
  bottom: number
  left: number
  width: number
  height: number
}

const props = defineProps<{
  visible: boolean
  anchorRect: AnchorRect | null
}>()

const emit = defineEmits<{
  close: []
}>()

const route = useRoute()
const userStore = useUserStore()

const profile = ref<ProfileVO | null>(null)
const loadingProfile = ref(false)
const profileError = ref('')
const passwordSubmitting = ref(false)
const companySubmitting = ref(false)
const activePanel = ref<'password' | 'company' | null>(null)
const passwordFormRef = ref<FormInstance>()
const companyFormRef = ref<FormInstance>()
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440)
const viewportHeight = ref(typeof window !== 'undefined' ? window.innerHeight : 900)

const passwordForm = reactive<ChangePasswordForm>({
  oldPassword: '',
  newPassword: '',
})

const companyForm = reactive({
  description: '',
})

const passwordRules: FormRules<ChangePasswordForm> = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d).{8,}$/,
      message: '至少 8 位，且需包含大小写字母和数字',
      trigger: 'blur',
    },
  ],
}

const companyRules: FormRules<typeof companyForm> = {
  description: [{ max: 500, message: '企业画像不能超过 500 个字符', trigger: 'blur' }],
}

const displayName = computed(() => profile.value?.realName || userStore.realName || '当前用户')
const roleLabel = computed(() => getRoleName(profile.value?.role || userStore.role))
const isOwner = computed(() => (profile.value?.role || userStore.role) === 'owner')
const avatarText = computed(() => displayName.value.charAt(0).toUpperCase() || 'U')

const panelMetrics = computed(() => {
  const mainWidth = clamp(viewportWidth.value - 32, 336, 404)
  const detailWidth = clamp(viewportWidth.value - 32, 320, 396)
  const mainLeft = clamp((props.anchorRect?.right ?? 96) + 18, 16, viewportWidth.value - mainWidth - 16)
  const bottom = clamp(
    viewportHeight.value - (props.anchorRect?.bottom ?? viewportHeight.value - 96),
    16,
    viewportHeight.value - 64,
  )
  const canShowSideBySide = Boolean(
    activePanel.value
      && mainLeft + mainWidth + 16 + detailWidth + 16 <= viewportWidth.value
      && viewportWidth.value >= 1180,
  )
  const detailLeft = canShowSideBySide
    ? mainLeft + mainWidth + 16
    : clamp(mainLeft + 8, 16, viewportWidth.value - detailWidth - 16)

  return {
    mainWidth,
    detailWidth,
    mainLeft,
    detailLeft,
    bottom,
  }
})

const mainCardStyle = computed(() => ({
  width: `${panelMetrics.value.mainWidth}px`,
  left: `${panelMetrics.value.mainLeft}px`,
  bottom: `${panelMetrics.value.bottom}px`,
}))

const detailCardStyle = computed(() => ({
  width: `${panelMetrics.value.detailWidth}px`,
  left: `${panelMetrics.value.detailLeft}px`,
  bottom: `${panelMetrics.value.bottom}px`,
}))

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      syncViewport()
      window.addEventListener('resize', syncViewport)
      void loadProfile()
      return
    }

    window.removeEventListener('resize', syncViewport)
    resetPanels()
  },
)

watch(
  () => route.fullPath,
  () => {
    if (props.visible) {
      emit('close')
    }
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
})

async function loadProfile() {
  loadingProfile.value = true
  profileError.value = ''

  try {
    const res = await getCurrentProfile()
    profile.value = res.data
    companyForm.description = res.data.companyDescription || ''
  } catch (error) {
    profileError.value = (error as { message?: string })?.message || '个人信息加载失败，请稍后重试'
  } finally {
    loadingProfile.value = false
  }
}

function syncViewport() {
  viewportWidth.value = window.innerWidth
  viewportHeight.value = window.innerHeight
}

function resetPanels() {
  activePanel.value = null
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  companyForm.description = profile.value?.companyDescription || ''
  passwordFormRef.value?.clearValidate()
  companyFormRef.value?.clearValidate()
  passwordSubmitting.value = false
  companySubmitting.value = false
}

function openPasswordPanel() {
  activePanel.value = 'password'
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordFormRef.value?.clearValidate()
}

function openCompanyPanel() {
  activePanel.value = 'company'
  companyForm.description = profile.value?.companyDescription || ''
  companyFormRef.value?.clearValidate()
}

async function handleSubmitPassword() {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate()
  passwordSubmitting.value = true

  try {
    await changePassword(passwordForm)
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    activePanel.value = null
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
      description: companyForm.description.trim(),
    })
    profile.value = res.data
    companyForm.description = res.data.companyDescription || ''
    activePanel.value = null
    ElMessage.success('企业画像已更新')
  } finally {
    companySubmitting.value = false
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      customClass: 'app-message-card app-message-card--warning',
      confirmButtonClass: 'app-message-card__button app-message-card__button--primary',
      cancelButtonClass: 'app-message-card__button app-message-card__button--secondary',
      type: 'warning',
    })
    emit('close')
    userStore.logout()
  } catch {
    /* cancelled */
  }
}

function getRoleName(role?: string) {
  const map: Record<string, string> = {
    admin: '系统管理员',
    owner: '老板',
    staff: '数据填写员',
  }
  return role ? map[role] || role : '当前角色'
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="profile-overlay">
      <div v-if="visible" class="profile-overlay" @click="emit('close')" />
    </Transition>

    <Transition name="profile-card">
      <section
        v-if="visible && anchorRect"
        class="profile-card profile-card--main"
        :style="mainCardStyle"
        @click.stop
      >
        <button class="close-button" type="button" @click="emit('close')">
          <el-icon><CloseBold /></el-icon>
        </button>

        <template v-if="loadingProfile">
          <div class="profile-loading">
            <div class="profile-loading__avatar" />
            <div class="profile-loading__line large" />
            <div class="profile-loading__line medium" />
            <div class="profile-loading__line small" />
          </div>
        </template>

        <template v-else>
          <div class="identity-card">
            <div class="identity-avatar">
              <span>{{ avatarText }}</span>
            </div>
            <div class="identity-copy">
              <p class="identity-greeting">{{ displayName }}，您好</p>
              <strong>{{ roleLabel }}</strong>
              <span v-if="profile?.username" class="identity-account">{{ profile.username }}</span>
            </div>
          </div>

          <button
            v-if="isOwner"
            type="button"
            class="company-entry hover-card"
            @click="openCompanyPanel"
          >
            <div>
              <span class="section-label">企业画像</span>
              <strong>管理您的公司信息</strong>
              <p>{{ profile?.companyName || '当前企业' }}</p>
            </div>
            <el-icon><OfficeBuilding /></el-icon>
          </button>

          <div class="action-matrix hover-card">
            <button type="button" class="matrix-action" @click="openPasswordPanel">
              <span class="matrix-icon">
                <el-icon><Lock /></el-icon>
              </span>
              <span class="matrix-copy">
                <strong>修改密码</strong>
              </span>
            </button>

            <button type="button" class="matrix-action is-danger" @click="handleLogout">
              <span class="matrix-icon">
                <el-icon><SwitchButton /></el-icon>
              </span>
              <span class="matrix-copy">
                <strong>退出登录</strong>
              </span>
            </button>
          </div>

          <p v-if="profileError" class="profile-error">{{ profileError }}</p>
        </template>
      </section>
    </Transition>

    <Transition name="profile-card">
      <section
        v-if="visible && anchorRect && activePanel"
        class="profile-card profile-card--detail"
        :style="detailCardStyle"
        @click.stop
      >
        <button class="close-button" type="button" @click="activePanel = null">
          <el-icon><CloseBold /></el-icon>
        </button>

        <template v-if="activePanel === 'password'">
          <div class="detail-header">
            <span class="section-label">账户安全</span>
            <h3>修改密码</h3>
            <p>更新后请使用新密码重新登录，建议与企业常用账号区分开。</p>
          </div>

          <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <div class="detail-actions">
              <button type="button" class="profile-action-button profile-action-button--secondary" @click="activePanel = null">
                取消
              </button>
              <button
                type="button"
                class="profile-action-button profile-action-button--primary"
                :disabled="passwordSubmitting"
                @click="handleSubmitPassword"
              >
                保存新密码
              </button>
            </div>
          </el-form>
        </template>

        <template v-else>
          <div class="detail-header">
            <span class="section-label">企业画像</span>
            <h3>更新公司描述</h3>
            <p>这段描述会被首页 AI 速记和 AI 助理复用，用来理解你的业务背景。</p>
          </div>

          <el-form ref="companyFormRef" :model="companyForm" :rules="companyRules" label-position="top">
            <el-form-item label="企业画像" prop="description">
              <el-input
                v-model="companyForm.description"
                type="textarea"
                :rows="7"
                maxlength="500"
                show-word-limit
                placeholder="例如：主营跨境贸易与供应链协同服务，客户集中在华南制造业企业。"
              />
            </el-form-item>
            <div class="detail-tip">
              <el-icon><ChatDotRound /></el-icon>
              <span>写清主营业务、客户类型和经营重心，AI 的总结会更贴近真实情况。</span>
            </div>
            <div class="detail-actions">
              <button type="button" class="profile-action-button profile-action-button--secondary" @click="activePanel = null">
                取消
              </button>
              <button
                type="button"
                class="profile-action-button profile-action-button--primary"
                :disabled="companySubmitting"
                @click="handleSubmitCompany"
              >
                保存企业画像
              </button>
            </div>
          </el-form>
        </template>
      </section>
    </Transition>
  </Teleport>
</template>

<style scoped>
.profile-overlay {
  position: fixed;
  inset: 0;
  background: rgba(18, 31, 48, 0.12);
  z-index: 1500;
}

.profile-card {
  position: fixed;
  z-index: 1501;
  border-radius: 30px;
  padding: 28px 28px 24px;
  background:
    radial-gradient(circle at top right, rgba(0, 117, 222, 0.16), transparent 38%),
    linear-gradient(165deg, #f9fbff 0%, #f1f5ff 54%, #f8f6f4 100%);
  border: 1px solid rgba(52, 78, 121, 0.12);
  box-shadow: 0 32px 72px rgba(20, 32, 54, 0.2);
}

.profile-card--detail {
  background:
    radial-gradient(circle at top right, rgba(42, 157, 153, 0.12), transparent 34%),
    linear-gradient(160deg, #ffffff 0%, #f6fbff 48%, #f8f6f4 100%);
}

.close-button {
  position: absolute;
  top: 18px;
  right: 18px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.8);
  color: rgba(20, 32, 54, 0.72);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, color 0.18s ease;
}

.close-button:hover {
  color: rgba(20, 32, 54, 0.92);
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(20, 32, 54, 0.12);
}

.identity-card,
.company-entry,
.action-matrix {
  position: relative;
  border-radius: 24px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.hover-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 40px rgba(15, 23, 42, 0.1);
}

.identity-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 6px 4px 10px;
  text-align: center;
  border: none;
  background: transparent;
  box-shadow: none;
}

.identity-avatar {
  width: 94px;
  height: 94px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #287ef0 0%, #5a62ea 55%, #8a46d9 100%);
  color: #ffffff;
  font-size: 38px;
  font-weight: 700;
  box-shadow: 0 24px 40px rgba(41, 82, 170, 0.24);
}

.identity-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.identity-greeting {
  font-size: 28px;
  font-weight: 700;
  color: rgba(16, 24, 38, 0.96);
  letter-spacing: -0.04em;
}

.identity-copy strong {
  font-size: 15px;
  color: #586174;
}

.identity-account {
  font-size: 13px;
  color: #8b93a3;
}

.company-entry {
  width: 100%;
  margin-top: 16px;
  border: 1px solid rgba(0, 117, 222, 0.14);
  background: rgba(255, 255, 255, 0.72);
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  text-align: left;
  cursor: pointer;
}

.company-entry strong,
.matrix-action strong {
  display: block;
  font-size: 16px;
  color: rgba(16, 24, 38, 0.95);
  white-space: nowrap;
}

.company-entry p,
.detail-header p,
.detail-tip span {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: #667085;
}

.action-matrix {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid rgba(20, 32, 54, 0.08);
  background: rgba(255, 255, 255, 0.74);
  overflow: hidden;
}

.matrix-action {
  border: none;
  background: transparent;
  padding: 22px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  text-align: left;
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease;
}

.matrix-action + .matrix-action {
  border-left: 1px solid rgba(20, 32, 54, 0.08);
}

.matrix-action:hover {
  background: rgba(246, 250, 255, 0.88);
}

.matrix-action.is-danger:hover {
  background: rgba(255, 243, 240, 0.9);
}

.matrix-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 117, 222, 0.1);
  color: #0075de;
  flex-shrink: 0;
}

.matrix-action.is-danger .matrix-icon {
  background: rgba(220, 68, 55, 0.12);
  color: #d44737;
}

.matrix-copy {
  min-width: 0;
  display: flex;
  align-items: center;
}

.section-label {
  display: inline-flex;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #8b93a3;
  text-transform: uppercase;
}

.detail-header {
  margin-bottom: 22px;
}

.detail-header h3 {
  font-size: 28px;
  font-weight: 700;
  color: rgba(16, 24, 38, 0.96);
  letter-spacing: -0.04em;
}

.detail-tip {
  margin-top: 4px;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(42, 157, 153, 0.08);
  color: #2a9d99;
}

.detail-actions {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.profile-action-button {
  min-width: 118px;
  min-height: 44px;
  padding: 0 22px;
  border: none;
  border-radius: 14px;
  font: inherit;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease,
    opacity 0.18s ease;
}

.profile-action-button:hover,
.profile-action-button:focus-visible {
  transform: translateY(-1px);
  outline: none;
}

.profile-action-button--secondary {
  border: 1px solid rgba(31, 41, 55, 0.14);
  background: rgba(255, 255, 255, 0.84);
  color: #4b5565;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.06);
}

.profile-action-button--secondary:hover,
.profile-action-button--secondary:focus-visible {
  border-color: rgba(20, 115, 230, 0.2);
  background: #ffffff;
  color: #0d66c2;
}

.profile-action-button--primary {
  background: linear-gradient(135deg, #0d66c2, #3394f5);
  color: #ffffff;
  box-shadow: 0 14px 26px rgba(13, 102, 194, 0.24);
}

.profile-action-button--primary:hover,
.profile-action-button--primary:focus-visible {
  background: linear-gradient(135deg, #0b5cad, #2789ec);
  box-shadow: 0 18px 30px rgba(13, 102, 194, 0.3);
}

.profile-action-button:disabled,
.profile-action-button:disabled:hover,
.profile-action-button:disabled:focus-visible {
  cursor: not-allowed;
  transform: none;
  opacity: 0.62;
  box-shadow: none;
}

.profile-error {
  margin-top: 14px;
  font-size: 13px;
  color: #d44737;
}

.profile-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.profile-loading__avatar,
.profile-loading__line {
  background: #edf1f8;
  border-radius: 999px;
}

.profile-loading__avatar {
  width: 96px;
  height: 96px;
  border-radius: 50%;
}

.profile-loading__line.large {
  width: 56%;
  height: 24px;
}

.profile-loading__line.medium {
  width: 40%;
  height: 16px;
}

.profile-loading__line.small {
  width: 68%;
  height: 14px;
}

.profile-overlay-enter-active,
.profile-overlay-leave-active,
.profile-card-enter-active,
.profile-card-leave-active {
  transition: opacity 0.2s ease, transform 0.22s ease;
}

.profile-overlay-enter-from,
.profile-overlay-leave-to,
.profile-card-enter-from,
.profile-card-leave-to {
  opacity: 0;
}

.profile-card-enter-from,
.profile-card-leave-to {
  transform: translateY(10px) scale(0.98);
}

@media (max-width: 820px) {
  .profile-card {
    left: 16px !important;
    right: 16px;
    width: auto !important;
    bottom: 16px !important;
  }

  .action-matrix {
    grid-template-columns: 1fr;
  }

  .matrix-action + .matrix-action {
    border-left: none;
    border-top: 1px solid rgba(20, 32, 54, 0.08);
  }
}
</style>
