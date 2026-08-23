import { onBeforeUnmount, onMounted, ref } from 'vue'

/** 会话 / 知识检索页全屏面板 */
export function usePanelMaximize() {
  const isMaximized = ref(false)

  const toggleMaximize = () => {
    isMaximized.value = !isMaximized.value
  }

  const onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && isMaximized.value) {
      isMaximized.value = false
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', onKeydown)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('keydown', onKeydown)
  })

  return { isMaximized, toggleMaximize }
}
