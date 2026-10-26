import { computed } from 'vue'
import { useUserStore } from '@/stores/useUserStore'

/**
 * 会话页头像展示：首字母 + 基于用户名哈希的稳定色相。
 * AIChat / DocumentQA 原先各写一份（仅饱和度与亮度不同），统一到这里。
 */
export function useUserAvatar(
  options: { fallbackName?: string; saturation?: number; lightness?: number } = {},
) {
  const userStore = useUserStore()
  const fallbackName = options.fallbackName ?? '管理员'
  const saturation = options.saturation ?? 70
  const lightness = options.lightness ?? 50

  const initial = computed(() => (userStore.userName || fallbackName).charAt(0).toUpperCase())

  const color = computed(() => {
    const name = userStore.userName || 'admin'
    let hash = 0
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash)
    }
    const hue = Math.abs(hash % 360)
    return `hsl(${hue}, ${saturation}%, ${lightness}%)`
  })

  return { initial, color }
}
