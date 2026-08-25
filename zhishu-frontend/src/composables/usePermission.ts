import { computed } from 'vue'
import { useUserStore } from '@/stores/useUserStore'

/**
 * 按钮权限校验 composable
 */
export function usePermission() {
  const userStore = useUserStore()

  const permissions = computed(() => userStore.permissions)

  const hasPermission = (code: string | string[]) => userStore.hasPermission(code)

  const hasAllPermissions = (codes: string[]) => userStore.hasAllPermissions(codes)

  return {
    permissions,
    hasPermission,
    hasAllPermissions,
  }
}
