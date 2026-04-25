<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type GuidePlacement = 'auto' | 'top' | 'bottom' | 'right' | 'center'

type FinanceGuideStep = {
  title: string
  description: string
  target?: HTMLElement | null
  targetSelector?: string
  placement?: GuidePlacement
  primaryText?: string
  width?: number
  highlightPadding?: number
  highlightRadius?: number
  minCardLeft?: number
}

const props = defineProps<{
  visible: boolean
  steps: FinanceGuideStep[]
}>()

const emit = defineEmits<{
  skip: []
  finish: []
}>()

const guideLabel = '员工功能引导'
const skipText = '跳过'
const backText = '上一步'
const nextText = '下一步'
const finishText = '完成'

const currentIndex = ref(0)
const targetRect = ref<DOMRect | null>(null)

const currentStep = computed(() => props.steps[currentIndex.value] || null)
const isLastStep = computed(() => currentIndex.value === props.steps.length - 1)
const primaryText = computed(() => {
  if (!currentStep.value) return nextText
  if (currentStep.value.primaryText) return currentStep.value.primaryText
  return isLastStep.value ? finishText : nextText
})

const progressText = computed(() => {
  if (!props.steps.length) return ''
  return `${currentIndex.value + 1} / ${props.steps.length}`
})

const cardStyle = computed(() => {
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const step = currentStep.value
  const cardWidth = Math.min(step?.width || 380, viewportWidth - 32)

  if (!step || !targetRect.value || step.placement === 'center') {
    return {
      width: `${cardWidth}px`,
      left: '50%',
      top: '50%',
      transform: 'translate(-50%, -50%)',
    }
  }

  const cardHeight = 248
  const gap = 20
  const maxLeft = Math.max(16, viewportWidth - cardWidth - 16)
  const centeredLeft = clamp(
    targetRect.value.left + (targetRect.value.width / 2) - (cardWidth / 2),
    16,
    maxLeft,
  )

  let left = centeredLeft
  let top = 16
  const placement = step.placement || 'auto'
  const bottomSpace = viewportHeight - targetRect.value.bottom - 16
  const topSpace = targetRect.value.top - 16

  if (placement === 'right') {
    left = clamp(Math.max(targetRect.value.right + gap, step.minCardLeft || 16), 16, maxLeft)
    top = clamp(targetRect.value.top, 16, Math.max(16, viewportHeight - cardHeight - 16))
  } else {
    const shouldPlaceBelow = placement === 'bottom'
      || (placement === 'auto' && (bottomSpace >= cardHeight || bottomSpace >= topSpace))

    top = shouldPlaceBelow
      ? clamp(targetRect.value.bottom + gap, 16, Math.max(16, viewportHeight - cardHeight - 16))
      : clamp(targetRect.value.top - cardHeight - gap, 16, Math.max(16, viewportHeight - cardHeight - 16))
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

  const padding = currentStep.value.highlightPadding || 12
  const radius = currentStep.value.highlightRadius || 20

  return {
    left: `${targetRect.value.left - padding}px`,
    top: `${targetRect.value.top - padding}px`,
    width: `${targetRect.value.width + (padding * 2)}px`,
    height: `${targetRect.value.height + (padding * 2)}px`,
    borderRadius: `${radius}px`,
  }
})

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

async function syncTargetRect() {
  const target = await resolveCurrentTarget()

  if (!props.visible || !target) {
    targetRect.value = null
    return
  }

  target.scrollIntoView({
    behavior: 'smooth',
    block: 'center',
    inline: 'nearest',
  })

  await nextTick()
  targetRect.value = target.getBoundingClientRect()
}

async function resolveCurrentTarget() {
  const step = currentStep.value
  if (!step) return null
  if (step.target) return step.target
  if (!step.targetSelector || typeof document === 'undefined') return null

  await nextTick()
  for (let index = 0; index < 10; index += 1) {
    const target = document.querySelector(step.targetSelector) as HTMLElement | null
    if (target) return target
    await new Promise((resolve) => window.setTimeout(resolve, 50))
  }

  return null
}

function resetGuide() {
  currentIndex.value = 0
  targetRect.value = null
}

function handleSkip() {
  emit('skip')
  resetGuide()
}

function handleBack() {
  if (currentIndex.value === 0) return
  currentIndex.value -= 1
}

function handlePrimary() {
  if (isLastStep.value) {
    emit('finish')
    resetGuide()
    return
  }
  currentIndex.value += 1
}

async function handleViewportChange() {
  await syncTargetRect()
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) {
      resetGuide()
      return
    }
    currentIndex.value = 0
    await syncTargetRect()
  },
  { immediate: true },
)

watch(
  () => [props.visible, currentIndex.value, currentStep.value?.target, currentStep.value?.targetSelector] as const,
  async () => {
    if (!props.visible) return
    await syncTargetRect()
  },
)

onMounted(() => {
  window.addEventListener('resize', handleViewportChange)
  window.addEventListener('scroll', handleViewportChange, true)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleViewportChange)
  window.removeEventListener('scroll', handleViewportChange, true)
})
</script>

<template>
  <teleport to="body">
    <div v-if="visible && currentStep" class="finance-onboarding-guide">
      <div
        class="finance-onboarding-guide__backdrop"
        :class="{ 'finance-onboarding-guide__backdrop--clear-target': targetRect }"
        @click="handleSkip"
      />
      <div
        v-if="targetRect"
        class="finance-onboarding-guide__highlight"
        :style="highlightStyle"
      />
      <div class="finance-onboarding-guide__card ds-card" :style="cardStyle">
        <div class="finance-onboarding-guide__meta">
          <span class="finance-onboarding-guide__eyebrow">{{ guideLabel }}</span>
          <span class="finance-onboarding-guide__progress">{{ progressText }}</span>
        </div>
        <h3>{{ currentStep.title }}</h3>
        <p>{{ currentStep.description }}</p>
        <div class="finance-onboarding-guide__actions">
          <button type="button" class="finance-onboarding-guide__text-button" @click="handleSkip">
            {{ skipText }}
          </button>
          <div class="finance-onboarding-guide__nav">
            <button
              v-if="currentIndex > 0"
              type="button"
              class="finance-onboarding-guide__step-button finance-onboarding-guide__step-button--secondary"
              @click="handleBack"
            >
              {{ backText }}
            </button>
            <button
              type="button"
              class="finance-onboarding-guide__step-button finance-onboarding-guide__step-button--primary"
              @click="handlePrimary"
            >
              {{ primaryText }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.finance-onboarding-guide {
  position: fixed;
  inset: 0;
  z-index: 3200;
  pointer-events: none;
}

.finance-onboarding-guide__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.56);
  backdrop-filter: blur(2px);
  pointer-events: auto;
}

.finance-onboarding-guide__backdrop--clear-target {
  background: transparent;
  backdrop-filter: none;
  pointer-events: none;
}

.finance-onboarding-guide__highlight {
  position: fixed;
  border-radius: 20px;
  box-shadow:
    0 0 0 9999px rgba(15, 23, 42, 0.56),
    0 0 0 1px rgba(255, 255, 255, 0.9),
    0 24px 60px rgba(15, 23, 42, 0.24);
  pointer-events: none;
}

.finance-onboarding-guide__card {
  position: fixed;
  padding: 20px 20px 18px;
  border-radius: 24px;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.98), rgba(246, 250, 255, 0.94));
  border: 1px solid rgba(52, 78, 121, 0.12);
  box-shadow: 0 28px 72px rgba(15, 23, 42, 0.22);
  pointer-events: auto;
}

.finance-onboarding-guide__meta,
.finance-onboarding-guide__actions,
.finance-onboarding-guide__nav {
  display: flex;
  align-items: center;
}

.finance-onboarding-guide__meta {
  justify-content: space-between;
  gap: 16px;
}

.finance-onboarding-guide__eyebrow {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #7f8795;
}

.finance-onboarding-guide__progress {
  font-size: 12px;
  font-weight: 700;
  color: #7b8493;
}

.finance-onboarding-guide__card h3 {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.35;
  color: rgba(15, 23, 42, 0.96);
  letter-spacing: 0;
}

.finance-onboarding-guide__card p {
  margin-top: 12px;
  font-size: 14px;
  line-height: 1.8;
  color: #5f6675;
  white-space: pre-line;
}

.finance-onboarding-guide__actions {
  margin-top: 20px;
  justify-content: space-between;
  gap: 12px;
}

.finance-onboarding-guide__nav {
  gap: 10px;
}

.finance-onboarding-guide__text-button,
.finance-onboarding-guide__step-button {
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

.finance-onboarding-guide__text-button {
  padding: 10px 12px;
  border-radius: 999px;
  background: transparent;
  color: #5f6675;
}

.finance-onboarding-guide__text-button:hover,
.finance-onboarding-guide__text-button:focus-visible {
  background: rgba(15, 23, 42, 0.045);
  color: rgba(15, 23, 42, 0.92);
  outline: none;
}

.finance-onboarding-guide__step-button {
  min-width: 112px;
  min-height: 44px;
  padding: 0 20px;
  border-radius: 14px;
}

.finance-onboarding-guide__step-button:hover,
.finance-onboarding-guide__step-button:focus-visible {
  transform: translateY(-1px);
  outline: none;
}

.finance-onboarding-guide__step-button--secondary {
  border: 1px solid rgba(31, 41, 55, 0.14);
  background: rgba(255, 255, 255, 0.82);
  color: #4b5565;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.06);
}

.finance-onboarding-guide__step-button--secondary:hover,
.finance-onboarding-guide__step-button--secondary:focus-visible {
  border-color: rgba(20, 115, 230, 0.2);
  background: #ffffff;
  color: #0d66c2;
}

.finance-onboarding-guide__step-button--primary {
  background: linear-gradient(135deg, #0d66c2, #3394f5);
  color: #ffffff;
  box-shadow: 0 14px 26px rgba(13, 102, 194, 0.24);
}

.finance-onboarding-guide__step-button--primary:hover,
.finance-onboarding-guide__step-button--primary:focus-visible {
  background: linear-gradient(135deg, #0b5cad, #2789ec);
  box-shadow: 0 18px 30px rgba(13, 102, 194, 0.3);
}

@media (max-width: 768px) {
  .finance-onboarding-guide__card {
    max-width: calc(100vw - 32px);
  }

  .finance-onboarding-guide__actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .finance-onboarding-guide__nav {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (prefers-reduced-motion: reduce) {
  .finance-onboarding-guide__backdrop,
  .finance-onboarding-guide__highlight,
  .finance-onboarding-guide__card,
  .finance-onboarding-guide__text-button,
  .finance-onboarding-guide__step-button {
    transition: none;
  }
}
</style>
