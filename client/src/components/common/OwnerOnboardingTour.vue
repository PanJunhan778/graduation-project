<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHomeDashboard } from '@/api/dashboard'
import { useUserStore } from '@/store/user'

type TourPlacement = 'auto' | 'top' | 'bottom' | 'right' | 'left' | 'center'

interface OwnerTourStep {
  key: string
  route: string
  title: string
  description: string
  targetSelector?: string
  placement?: TourPlacement
  width?: number
  requireInteraction?: boolean
  interactionEvent?: string
  interactionHint?: string
  forceOpenClass?: string
  highlightOffsetY?: number
}

const steps: OwnerTourStep[] = [
  {
    key: 'welcome',
    route: '/home',
    title: '欢迎使用智能轻量化企业管理系统',
    description: '接下来会带你快速了解老板端的核心功能：创建数据填写员、录入基础数据、查看经营看板、使用 AI 助理和追踪审计记录。如果你已经熟悉系统，可以点击“稍后再说”跳过本次引导。',
    placement: 'center',
    width: 480,
  },
  {
    key: 'sidebar',
    route: '/home',
    title: '左侧是老板的功能地图',
    description: '老板可以进入首页、数据看板、三类业务台账、审计日志、用户管理和 AI 智能助理。日常从首页看概况，需要追细节时再进入对应模块。',
    targetSelector: '[data-guide="owner-sidebar-menu"]',
    placement: 'right',
    width: 420,
  },
  {
    key: 'home',
    route: '/home',
    title: '首页负责给你一个经营快照',
    description: '这里会汇总本月收入、支出、净利润、待缴税额、近期趋势和 AI 经营速记。它依赖基础数据，第一批数据录入后会自动变得有内容。',
    targetSelector: '[data-guide="owner-home-cockpit"]',
    placement: 'bottom',
    width: 430,
  },
  {
    key: 'users',
    route: '/users',
    title: '先创建数据填写员账号',
    description: '老板不一定亲自录入所有数据。可以在这里创建员工账号，让数据填写员维护财务、税务和员工名册，老板负责看结果和追踪变化。',
    targetSelector: '[data-guide="owner-user-create"]',
    placement: 'bottom',
    width: 410,
    highlightOffsetY: -8,
  },
  {
    key: 'finance',
    route: '/finance',
    title: '财务账本是经营分析的底座',
    description: '可以单笔新增，也可以下载模板后批量导入 Excel。收入和支出流水录入后，首页趋势、财务看板和 AI 速记都会同步受益。',
    targetSelector: '[data-guide="owner-finance-actions"]',
    placement: 'bottom',
    width: 430,
    highlightOffsetY: -8,
  },
  {
    key: 'employee',
    route: '/employee',
    title: '员工名册用于组织和薪资观察',
    description: '维护姓名、部门、职位、薪资和在职状态后，人事看板可以自动形成部门人数、薪资结构和团队变化观察。',
    targetSelector: '[data-guide="owner-employee-workspace"]',
    placement: 'bottom',
    width: 420,
  },
  {
    key: 'tax',
    route: '/tax',
    title: '税务档案负责沉淀申报和缴纳状态',
    description: '录入税款所属期、税种、税额和缴纳状态后，首页会形成税务时间轴，看板会给出税负率和待缴风险提示。',
    targetSelector: '[data-guide="owner-tax-workspace"]',
    placement: 'bottom',
    width: 420,
  },
  {
    key: 'dashboard',
    route: '/dashboard',
    title: '数据看板用于深入分析',
    description: '这里分为财务剖析、人事洞察和税务健康三类视角，支持切换范围并导出 PDF，适合阶段复盘或汇报。',
    targetSelector: '[data-guide="owner-dashboard-tools"]',
    placement: 'bottom',
    width: 420,
  },
  {
    key: 'dashboard-switch',
    route: '/dashboard',
    title: '右侧可以快速切换分析视图',
    description: '数据看板右侧有一个竖向视图切换栏，可以在财务剖析、人事洞察和税务健康之间切换。请先点击一个非当前视图完成切换，再继续下一步。',
    targetSelector: '[data-guide="owner-dashboard-switch"]',
    placement: 'left',
    width: 430,
    requireInteraction: true,
    interactionEvent: 'owner-dashboard-view-switched',
    interactionHint: '请点击右侧视图切换栏中的非当前视图按钮。',
    forceOpenClass: 'is-guide-open',
  },
  {
    key: 'ai',
    route: '/ai-chat',
    title: 'AI 助理可以直接问经营问题',
    description: '你可以询问支出结构、待缴税金、员工成本变化或经营风险。涉及敏感变更时，AI 会进入人工确认流程，不会直接替你改数据。',
    targetSelector: '[data-guide="owner-ai-composer"]',
    placement: 'top',
    width: 430,
  },
  {
    key: 'audit',
    route: '/audit',
    title: '审计日志用于追踪谁改了什么',
    description: '老板可以按模块、操作类型和日期范围查看新增、编辑、删除、恢复记录，展开后能看到字段级变化。',
    targetSelector: '[data-guide="owner-audit-workspace"]',
    placement: 'bottom',
    width: 420,
  },
  {
    key: 'profile',
    route: '/audit',
    title: '最后记住个人入口',
    description: '侧边栏底部可以打开个人面板。老板可以在里面维护企业画像，AI 速记和 AI 助理会复用这段背景信息。',
    targetSelector: '[data-guide="owner-profile-entry"]',
    placement: 'right',
    width: 410,
  },
]

const OWNER_TOUR_START_EVENT = 'owner-onboarding:start'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const visible = ref(false)
const currentIndex = ref(0)
const targetElement = ref<HTMLElement | null>(null)
const targetRect = ref<DOMRect | null>(null)
const resolvingTarget = ref(false)
const interactionDone = ref(true)
const checkingAutoStart = ref(false)
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440)
const viewportHeight = ref(typeof window !== 'undefined' ? window.innerHeight : 900)
let activationId = 0
let forcedOpenElement: HTMLElement | null = null
let forcedOpenClassName = ''

const currentStep = computed(() => steps[currentIndex.value])
const isLastStep = computed(() => currentIndex.value === steps.length - 1)
const progressText = computed(() => `${currentIndex.value + 1} / ${steps.length}`)
const canContinue = computed(() => !currentStep.value?.requireInteraction || interactionDone.value)
const primaryButtonText = computed(() => {
  if (isLastStep.value) return '完成'
  if (currentStep.value?.requireInteraction && !interactionDone.value) return '先点击切换'
  return '下一步'
})

const cardStyle = computed(() => {
  const step = currentStep.value
  const cardWidth = Math.min(step?.width || 400, viewportWidth.value - 32)

  if (!step || !targetRect.value || step.placement === 'center') {
    return {
      width: `${cardWidth}px`,
      left: '50%',
      top: '50%',
      transform: 'translate(-50%, -50%)',
    }
  }

  const cardHeight = 260
  const gap = 20
  const maxLeft = Math.max(16, viewportWidth.value - cardWidth - 16)
  const centeredLeft = clamp(
    targetRect.value.left + (targetRect.value.width / 2) - (cardWidth / 2),
    16,
    maxLeft,
  )
  const placement = step.placement || 'auto'
  const bottomSpace = viewportHeight.value - targetRect.value.bottom - 16
  const topSpace = targetRect.value.top - 16

  let left = centeredLeft
  let top = 16

  if (placement === 'right') {
    left = clamp(targetRect.value.right + gap, 16, maxLeft)
    top = clamp(targetRect.value.top, 16, Math.max(16, viewportHeight.value - cardHeight - 16))
  } else if (placement === 'left') {
    left = clamp(targetRect.value.left - cardWidth - gap, 16, maxLeft)
    top = clamp(
      targetRect.value.top + (targetRect.value.height / 2) - (cardHeight / 2),
      16,
      Math.max(16, viewportHeight.value - cardHeight - 16),
    )
  } else {
    const shouldPlaceBelow = placement === 'bottom'
      || (placement === 'auto' && (bottomSpace >= cardHeight || bottomSpace >= topSpace))

    top = shouldPlaceBelow
      ? clamp(targetRect.value.bottom + gap, 16, Math.max(16, viewportHeight.value - cardHeight - 16))
      : clamp(targetRect.value.top - cardHeight - gap, 16, Math.max(16, viewportHeight.value - cardHeight - 16))
  }

  return {
    width: `${cardWidth}px`,
    left: `${left}px`,
    top: `${top}px`,
  }
})

const highlightStyle = computed(() => {
  if (!targetRect.value) {
    return {}
  }

  const padding = 12
  const offsetY = currentStep.value?.highlightOffsetY ?? 0
  return {
    left: `${targetRect.value.left - padding}px`,
    top: `${targetRect.value.top - padding + offsetY}px`,
    width: `${targetRect.value.width + padding * 2}px`,
    height: `${targetRect.value.height + padding * 2}px`,
  }
})

watch(
  () => [route.path, userStore.role, userStore.companyCode, userStore.loginSessionId] as const,
  () => {
    void maybeStartAutomatically()
  },
  { immediate: true },
)

watch(currentIndex, () => {
  if (!visible.value) return
  void activateCurrentStep()
})

watch(visible, (nextVisible) => {
  if (!nextVisible) {
    targetElement.value = null
    targetRect.value = null
    cleanupForcedOpenElement()
    return
  }
  void activateCurrentStep()
})

onMounted(() => {
  window.addEventListener('resize', handleViewportChange)
  window.addEventListener('scroll', handleViewportChange, true)
  window.addEventListener(OWNER_TOUR_START_EVENT, handleOwnerTourStart)
  steps
    .filter((step) => step.interactionEvent)
    .forEach((step) => window.addEventListener(step.interactionEvent!, handleStepInteraction))
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleViewportChange)
  window.removeEventListener('scroll', handleViewportChange, true)
  window.removeEventListener(OWNER_TOUR_START_EVENT, handleOwnerTourStart)
  steps
    .filter((step) => step.interactionEvent)
    .forEach((step) => window.removeEventListener(step.interactionEvent!, handleStepInteraction))
  cleanupForcedOpenElement()
})

async function maybeStartAutomatically() {
  if (checkingAutoStart.value || visible.value) return
  if (route.path !== '/home' || userStore.role !== 'owner') return
  if (userStore.hasPromptedOwnerOnboardingInCurrentSession()) return
  if (userStore.hasCompletedOwnerOnboardingTour()) return
  if (userStore.hasDismissedOwnerOnboardingTour()) return

  checkingAutoStart.value = true
  try {
    const res = await getHomeDashboard()
    const setupStatus = res.data.setupStatus
    const shouldGuide = Boolean(setupStatus && (!setupStatus.hasStaffAccount || !setupStatus.hasFinanceRecord))
    if (!shouldGuide) return

    userStore.markOwnerOnboardingPrompted()
    await startTour()
  } catch {
    // 首页本身会处理数据加载错误，这里只避免引导反复打扰。
  } finally {
    checkingAutoStart.value = false
  }
}

async function startTour() {
  currentIndex.value = 0
  visible.value = true
  userStore.markOwnerOnboardingPrompted()
  await activateCurrentStep()
}

async function activateCurrentStep() {
  const step = currentStep.value
  if (!step) return

  const currentActivationId = ++activationId
  resolvingTarget.value = true
  interactionDone.value = !step.requireInteraction
  targetElement.value = null
  targetRect.value = null
  cleanupForcedOpenElement()

  try {
    if (route.path !== step.route) {
      await router.push(step.route)
    }

    await nextTick()
    await waitForPaint()

    const target = step.targetSelector
      ? await waitForElement(step.targetSelector, 5200)
      : null

    if (!visible.value || currentActivationId !== activationId) return

    targetElement.value = target
    if (target) {
      if (step.forceOpenClass) {
        target.classList.add(step.forceOpenClass)
        forcedOpenElement = target
        forcedOpenClassName = step.forceOpenClass
      }
      target.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' })
      await waitForPaint()
      syncTargetRect()
    }
  } finally {
    if (currentActivationId === activationId) {
      resolvingTarget.value = false
    }
  }
}

function handleOwnerTourStart() {
  void startTour()
}

function handleStepInteraction(event: Event) {
  if (!visible.value || !currentStep.value?.requireInteraction) return
  if (event.type !== currentStep.value.interactionEvent) return
  interactionDone.value = true
}

function handleLater() {
  userStore.markOwnerOnboardingPrompted()
  visible.value = false
}

function handleBack() {
  if (currentIndex.value === 0) return
  currentIndex.value -= 1
}

function handlePrimary() {
  if (!canContinue.value) return
  if (isLastStep.value) {
    userStore.markOwnerOnboardingCompleted()
    visible.value = false
    ElMessage.success('老板功能引导已完成')
    return
  }
  currentIndex.value += 1
}

function cleanupForcedOpenElement() {
  if (forcedOpenElement && forcedOpenClassName) {
    forcedOpenElement.classList.remove(forcedOpenClassName)
  }
  forcedOpenElement = null
  forcedOpenClassName = ''
}

function syncTargetRect() {
  if (!visible.value || !targetElement.value) {
    targetRect.value = null
    return
  }
  targetRect.value = targetElement.value.getBoundingClientRect()
}

function syncViewport() {
  viewportWidth.value = window.innerWidth
  viewportHeight.value = window.innerHeight
}

function handleViewportChange() {
  syncViewport()
  syncTargetRect()
}

function waitForPaint() {
  return new Promise<void>((resolve) => {
    requestAnimationFrame(() => resolve())
  })
}

function waitForElement(selector: string, timeoutMs: number): Promise<HTMLElement | null> {
  const startedAt = Date.now()

  return new Promise((resolve) => {
    const find = () => {
      const element = document.querySelector<HTMLElement>(selector)
      if (element) {
        resolve(element)
        return
      }

      if (Date.now() - startedAt >= timeoutMs) {
        resolve(null)
        return
      }

      window.setTimeout(find, 120)
    }

    find()
  })
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}
</script>

<template>
  <teleport to="body">
    <div v-if="visible && currentStep" class="owner-tour">
      <div
        class="owner-tour__backdrop"
        :class="{ 'owner-tour__backdrop--clear-target': targetRect }"
        @click="handleLater"
      />
      <div
        v-if="targetRect"
        class="owner-tour__highlight"
        :style="highlightStyle"
      />
      <section class="owner-tour__card ds-card" :style="cardStyle">
        <div class="owner-tour__meta">
          <span class="owner-tour__eyebrow">老板功能引导</span>
          <span class="owner-tour__progress">{{ progressText }}</span>
        </div>

        <h3>{{ currentStep.title }}</h3>
        <p>{{ currentStep.description }}</p>
        <span v-if="resolvingTarget" class="owner-tour__loading">正在定位页面内容...</span>
        <span
          v-else-if="currentStep.requireInteraction && !interactionDone"
          class="owner-tour__interaction-hint"
        >
          {{ currentStep.interactionHint }}
        </span>

        <div class="owner-tour__actions">
          <div class="owner-tour__quiet-actions">
            <button type="button" class="owner-tour__text-button" @click="handleLater">跳过</button>
          </div>
          <div class="owner-tour__nav-actions">
            <button
              v-if="currentIndex > 0"
              type="button"
              class="owner-tour__step-button owner-tour__step-button--secondary"
              @click="handleBack"
            >
              上一步
            </button>
            <button
              type="button"
              class="owner-tour__step-button owner-tour__step-button--primary"
              :disabled="!canContinue"
              @click="handlePrimary"
            >
              {{ primaryButtonText }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </teleport>
</template>

<style scoped>
.owner-tour {
  position: fixed;
  inset: 0;
  z-index: 3200;
  pointer-events: none;
}

.owner-tour__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.56);
  backdrop-filter: blur(2px);
  pointer-events: auto;
}

.owner-tour__backdrop--clear-target {
  background: transparent;
  backdrop-filter: none;
  pointer-events: none;
}

.owner-tour__highlight {
  position: fixed;
  border-radius: 20px;
  box-shadow:
    0 0 0 9999px rgba(15, 23, 42, 0.56),
    0 0 0 1px rgba(255, 255, 255, 0.9),
    0 24px 60px rgba(15, 23, 42, 0.24);
  pointer-events: none;
}

.owner-tour__card {
  position: fixed;
  padding: 20px 20px 18px;
  border-radius: 24px;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.98), rgba(246, 250, 255, 0.94));
  border: 1px solid rgba(52, 78, 121, 0.12);
  box-shadow: 0 28px 72px rgba(15, 23, 42, 0.22);
  pointer-events: auto;
}

.owner-tour__meta,
.owner-tour__actions,
.owner-tour__quiet-actions,
.owner-tour__nav-actions {
  display: flex;
  align-items: center;
}

.owner-tour__meta {
  justify-content: space-between;
  gap: 16px;
}

.owner-tour__eyebrow {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: #7f8795;
  text-transform: uppercase;
}

.owner-tour__progress {
  font-size: 12px;
  font-weight: 700;
  color: #7b8493;
}

.owner-tour__card h3 {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.35;
  color: rgba(15, 23, 42, 0.96);
  letter-spacing: 0;
}

.owner-tour__card p {
  margin-top: 12px;
  font-size: 14px;
  line-height: 1.8;
  color: #5f6675;
}

.owner-tour__loading,
.owner-tour__interaction-hint {
  display: inline-flex;
  margin-top: 12px;
  font-size: 12px;
  font-weight: 700;
}

.owner-tour__loading {
  color: #2a9d99;
}

.owner-tour__interaction-hint {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(20, 115, 230, 0.075);
  color: #1269c8;
}

.owner-tour__actions {
  margin-top: 20px;
  justify-content: space-between;
  gap: 12px;
}

.owner-tour__quiet-actions,
.owner-tour__nav-actions {
  gap: 10px;
}

.owner-tour__text-button,
.owner-tour__step-button {
  border: none;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.owner-tour__text-button {
  padding: 10px 12px;
  border-radius: 999px;
  background: transparent;
  color: #5f6675;
}

.owner-tour__text-button:hover,
.owner-tour__text-button:focus-visible {
  background: rgba(15, 23, 42, 0.045);
  color: rgba(15, 23, 42, 0.92);
  outline: none;
}

.owner-tour__step-button {
  min-width: 112px;
  min-height: 44px;
  padding: 0 20px;
  border-radius: 14px;
}

.owner-tour__step-button:hover,
.owner-tour__step-button:focus-visible {
  transform: translateY(-1px);
  outline: none;
}

.owner-tour__step-button--secondary {
  border: 1px solid rgba(31, 41, 55, 0.14);
  background: rgba(255, 255, 255, 0.82);
  color: #4b5565;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.06);
}

.owner-tour__step-button--secondary:hover,
.owner-tour__step-button--secondary:focus-visible {
  border-color: rgba(20, 115, 230, 0.2);
  background: #ffffff;
  color: #0d66c2;
}

.owner-tour__step-button--primary {
  background: linear-gradient(135deg, #0d66c2, #3394f5);
  color: #ffffff;
  box-shadow: 0 14px 26px rgba(13, 102, 194, 0.24);
}

.owner-tour__step-button--primary:hover,
.owner-tour__step-button--primary:focus-visible {
  background: linear-gradient(135deg, #0b5cad, #2789ec);
  box-shadow: 0 18px 30px rgba(13, 102, 194, 0.3);
}

.owner-tour__step-button:disabled,
.owner-tour__step-button:disabled:hover,
.owner-tour__step-button:disabled:focus-visible {
  cursor: not-allowed;
  transform: none;
  opacity: 0.58;
  box-shadow: none;
}

@media (max-width: 900px) {
  .owner-tour__actions {
    align-items: flex-end;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .owner-tour__card,
  .owner-tour__highlight,
  .owner-tour__text-button,
  .owner-tour__step-button {
    transition: none;
  }
}
</style>
