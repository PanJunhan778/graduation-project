import { onBeforeUnmount, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'

export function useDelayedLoading(source: MaybeRefOrGetter<boolean>, delay = 300) {
  const visible = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  watch(
    () => toValue(source),
    (loading) => {
      clearTimer()

      if (loading) {
        timer = setTimeout(() => {
          visible.value = true
        }, delay)
        return
      }

      visible.value = false
    },
    { immediate: true },
  )

  onBeforeUnmount(clearTimer)

  return visible
}
