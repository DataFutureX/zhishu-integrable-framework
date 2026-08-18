import type { Router } from 'vue-router'
import NProgress from 'nprogress'
import { usePageTransitionStore } from '@/stores/usePageTransitionStore'

const MIN_LOADING_MS = 120

/** 菜单页面路由切换时的加载过渡（顶栏进度条 + 内容区状态） */
export function setupPageTransition(router: Router) {
  NProgress.configure({ showSpinner: false, trickleSpeed: 120 })

  let startTime = 0
  let finishTimer: ReturnType<typeof setTimeout> | null = null

  const finishLoading = () => {
    const store = usePageTransitionStore()
    const elapsed = Date.now() - startTime
    const delay = Math.max(0, MIN_LOADING_MS - elapsed)

    if (finishTimer) {
      clearTimeout(finishTimer)
    }

    finishTimer = setTimeout(() => {
      store.finish()
      NProgress.done()
      finishTimer = null
    }, delay)
  }

  router.beforeEach((to, from) => {
    if (to.fullPath === from.fullPath) return

    startTime = Date.now()
    usePageTransitionStore().start()
    NProgress.start()
  })

  router.afterEach(async () => {
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
    })
    finishLoading()
  })

  router.onError(() => {
    if (finishTimer) {
      clearTimeout(finishTimer)
      finishTimer = null
    }
    usePageTransitionStore().finish()
    NProgress.done()
  })
}
