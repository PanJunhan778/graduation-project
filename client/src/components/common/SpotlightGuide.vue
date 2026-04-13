<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type PropType } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    required: true,
  },
  target: {
    type: Object as PropType<HTMLElement | null>,
    default: null,
  },
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    required: true,
  },
  primaryText: {
    type: String,
    default: '立即前往',
  },
})

const emit = defineEmits<{
  close: []
  primary: []
}>()

const targetRect = ref<DOMRect | null>(null)

const cardStyle = computed(() => {
  if (!targetRect.value) {
    return {}
  }

  const viewportWidth = window.innerWidth
  const cardWidth = Math.min(320, viewportWidth - 32)
  const preferredLeft = targetRect.value.left + targetRect.value.width - cardWidth
  const left = Math.max(16, Math.min(preferredLeft, viewportWidth - cardWidth - 16))
  const belowTop = targetRect.value.bottom + 18
  const aboveTop = targetRect.value.top - 188
  const top = belowTop + 160 <= window.innerHeight ? belowTop : Math.max(16, aboveTop)

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

  return {
    left: `${targetRect.value.left - 10}px`,
    top: `${targetRect.value.top - 10}px`,
    width: `${targetRect.value.width + 20}px`,
    height: `${targetRect.value.height + 20}px`,
  }
})

function syncTargetRect() {
  if (!props.visible || !props.target) {
    targetRect.value = null
    return
  }
  targetRect.value = props.target.getBoundingClientRect()
}

function handlePrimary() {
  emit('primary')
}

function handleClose() {
  emit('close')
}

watch(
  () => [props.visible, props.target] as const,
  () => {
    syncTargetRect()
  },
  { immediate: true },
)

watch(
  () => props.visible,
  (visible) => {
    if (!visible) {
      targetRect.value = null
    }
  },
)

onMounted(() => {
  window.addEventListener('resize', syncTargetRect)
  window.addEventListener('scroll', syncTargetRect, true)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncTargetRect)
  window.removeEventListener('scroll', syncTargetRect, true)
})
</script>

<template>
  <teleport to="body">
    <div v-if="visible && targetRect" class="spotlight-guide">
      <div class="spotlight-guide__backdrop" @click="handleClose" />
      <div class="spotlight-guide__highlight" :style="highlightStyle" />
      <div class="spotlight-guide__card ds-card" :style="cardStyle">
        <span class="spotlight-guide__eyebrow">工作引导</span>
        <h3>{{ title }}</h3>
        <p>{{ description }}</p>
        <div class="spotlight-guide__actions">
          <el-button text @click="handleClose">稍后再说</el-button>
          <el-button type="primary" @click="handlePrimary">{{ primaryText }}</el-button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.spotlight-guide {
  position: fixed;
  inset: 0;
  z-index: 3000;
}

.spotlight-guide__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.48);
}

.spotlight-guide__highlight {
  position: fixed;
  border-radius: 18px;
  box-shadow:
    0 0 0 9999px rgba(15, 23, 42, 0.48),
    0 0 0 1px rgba(255, 255, 255, 0.82),
    0 18px 48px rgba(15, 23, 42, 0.18);
  pointer-events: none;
}

.spotlight-guide__card {
  position: fixed;
  padding: 18px 18px 16px;
  border-radius: 20px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.18);
}

.spotlight-guide__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #9a938c;
}

.spotlight-guide__card h3 {
  margin-top: 10px;
  font-size: 20px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.96);
}

.spotlight-guide__card p {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.75;
  color: #615d59;
}

.spotlight-guide__actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (prefers-reduced-motion: reduce) {
  .spotlight-guide__highlight,
  .spotlight-guide__card {
    transition: none;
  }
}
</style>
