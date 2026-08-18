import type { App, Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/useUserStore'
import { matchPermissions } from '@/utils/permission'

type PermissionValue = string | string[]

function resolveCodes(value: PermissionValue | undefined): string[] {
  if (!value) return []
  return Array.isArray(value) ? value.filter(Boolean) : [value]
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding<PermissionValue>) {
  const codes = resolveCodes(binding.value)
  if (!codes.length) return

  const userStore = useUserStore()
  const allowed = matchPermissions(userStore.permissions, codes, {
    requireAll: Boolean(binding.modifiers?.all),
    isAdmin: userStore.isAdmin,
  })

  if (allowed) {
    el.style.removeProperty('display')
    return
  }

  // 使用隐藏而非移除 DOM，避免 Vue patch 时因节点丢失导致页面崩溃
  el.style.display = 'none'
}

const permissionDirective: Directive<HTMLElement, PermissionValue> = {
  mounted(el, binding) {
    checkPermission(el, binding)
  },
  updated(el, binding) {
    checkPermission(el, binding)
  },
}

export function setupPermissionDirective(app: App) {
  app.directive('permission', permissionDirective)
}

export default permissionDirective
