<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type GuidePlacement = 'auto' | 'top' | 'bottom' | 'right' | 'center'

type FinanceGuideStep = {
  title: string
  description: string
  target?: HTMLElement | null
  placement?: GuidePlacement
  primaryText?: string
  width?: number
  highlightPadding?: number
  highlightRadius?: number
}

const props = defineProps<{
  visible: boolean
  steps: FinanceGuideStep[]
}>()

const emit = defineEmits<{
  skip: []
  finish: []
}>()

const guideLabel = String.fromCharCode(21151, 33021, 24341, 23548)
const skipText = String.fromCharCode(36339, 36807)
const backText = String.fromCharCode(19978, 19968, 27493)
const nextText = String.fromCharCode(19979, 19968, 27493)
const finishText = String.fromCharCode(23436, 25104)

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
    left = clamp(targetRect.value.right + gap, 16, maxLeft)
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
  if (!targetRect.value || !currentStep.value?.target) {
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
  if (!props.visible || !currentStep.value?.target) {
    targetRect.value = null
    return
  }

  currentStep.value.target.scrollIntoView({
    behavior: 'smooth',
    block: 'center',
    inline: 'nearest',
  })

  await nextTick()
  targetRect.value = currentStep.value.target.getBoundingClientRect()
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
  () => [props.visible, currentIndex.value, currentStep.value?.target] as const,
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
      <div class="finance-onboarding-guide__backdrop" />
      <div
        v-if="currentStep.target && targetRect"
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
          <el-button text @click="handleSkip">{{ skipText }}</el-button>
          <div class="finance-onboarding-guide__nav">
            <el-button v-if="currentIndex > 0" @click="handleBack">{{ backText }}</el-button>
            <el-button type="primary" @click="handlePrimary">{{ primaryText }}</el-button>
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
  z-index: 3000;
}

.finance-onboarding-guide__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.56);
  backdrop-filter: blur(2px);
}

.finance-onboarding-guide__highlight {
  position: fixed;
  box-shadow:
    0 0 0 9999px rgba(15, 23, 42, 0.56),
    0 0 0 1px rgba(255, 255, 255, 0.88),
    0 20px 50px rgba(15, 23, 42, 0.2);
  pointer-events: none;
}

.finance-onboarding-guide__card {
  position: fixed;
  padding: 20px 20px 18px;
  border-radius: 24px;
  box-shadow: 0 28px 72px rgba(15, 23, 42, 0.24);
}

.finance-onboarding-guide__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.finance-onboarding-guide__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #9a938c;
}

.finance-onboarding-guide__progress {
  font-size: 12px;
  font-weight: 600;
  color: #8a847e;
}

.finance-onboarding-guide__card h3 {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
  color: rgba(15, 23, 42, 0.96);
}

.finance-onboarding-guide__card p {
  margin-top: 12px;
  font-size: 14px;
  line-height: 1.8;
  color: #615d59;
  white-space: pre-line;
}

.finance-onboarding-guide__actions {
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.finance-onboarding-guide__nav {
  display: flex;
  align-items: center;
  gap: 10px;
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
  .finance-onboarding-guide__card {
    transition: none;
  }
}
</style>
