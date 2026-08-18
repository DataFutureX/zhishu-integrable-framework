import { computed, type Ref } from 'vue'
import { useRoute } from 'vue-router'
import { useLayoutStore } from '@/stores/useLayoutStore'
import type {
  EffectiveRouteLayout,
  PageLayoutType,
  RouteLayoutMeta,
} from '@/types/route'

const LAYOUT_PRESETS: Record<
  PageLayoutType,
  Required<Pick<RouteLayoutMeta, 'fullBleed' | 'hideTabBar' | 'hideBreadcrumb' | 'hideSecondaryAside'>>
> = {
  default: {
    fullBleed: false,
    hideTabBar: false,
    hideBreadcrumb: false,
    hideSecondaryAside: false,
  },
  dashboard: {
    fullBleed: false,
    hideTabBar: false,
    hideBreadcrumb: false,
    hideSecondaryAside: true,
  },
  fullscreen: {
    fullBleed: true,
    hideTabBar: true,
    hideBreadcrumb: true,
    hideSecondaryAside: true,
  },
}

const isPageLayoutType = (value: unknown): value is PageLayoutType =>
  value === 'default' || value === 'dashboard' || value === 'fullscreen'

const pickBoolean = (value: unknown): boolean | undefined =>
  typeof value === 'boolean' ? value : undefined

const pickRouteLayoutMeta = (routeMeta: Record<string, unknown>): RouteLayoutMeta => ({
  layout: isPageLayoutType(routeMeta.layout) ? routeMeta.layout : undefined,
  fullBleed: pickBoolean(routeMeta.fullBleed),
  hideTabBar: pickBoolean(routeMeta.hideTabBar),
  hideBreadcrumb: pickBoolean(routeMeta.hideBreadcrumb),
  hideSecondaryAside: pickBoolean(routeMeta.hideSecondaryAside),
})

/** 从 matched 链中解析页面布局（子路由优先） */
export function resolvePageLayoutType(
  matched: { meta: Record<string, unknown> }[],
): { layoutType: PageLayoutType; explicitMeta: RouteLayoutMeta } {
  for (let i = matched.length - 1; i >= 0; i--) {
    const meta = pickRouteLayoutMeta(matched[i].meta)
    if (meta.layout) {
      return { layoutType: meta.layout, explicitMeta: meta }
    }
  }

  for (let i = matched.length - 1; i >= 0; i--) {
    const meta = pickRouteLayoutMeta(matched[i].meta)
    if (meta.fullBleed) {
      return { layoutType: 'fullscreen', explicitMeta: meta }
    }
  }

  return { layoutType: 'default', explicitMeta: {} }
}

export function useRouteLayout(showSecondaryAside: Ref<boolean>) {
  const route = useRoute()
  const layoutStore = useLayoutStore()

  const routeLayout = computed<EffectiveRouteLayout>(() => {
    const { layoutType, explicitMeta } = resolvePageLayoutType(route.matched)
    const preset = LAYOUT_PRESETS[layoutType]

    const fullBleed = explicitMeta.fullBleed ?? preset.fullBleed
    const hideTabBar = explicitMeta.hideTabBar ?? preset.hideTabBar
    const hideBreadcrumb = explicitMeta.hideBreadcrumb ?? preset.hideBreadcrumb
    const hideSecondaryAside = explicitMeta.hideSecondaryAside ?? preset.hideSecondaryAside

    const showTabBar = layoutStore.showTabBar && !hideTabBar
    const showBreadcrumb = layoutStore.showBreadcrumb && !hideBreadcrumb && !showTabBar
    const effectiveShowSecondaryAside = showSecondaryAside.value && !hideSecondaryAside
    const tabBarInNavBar = layoutStore.isHybridLayout && showTabBar
    const showTabBarInContent = showTabBar && !tabBarInNavBar

    const showHybridSubHeader =
      layoutStore.isHybridLayout &&
      (showBreadcrumb || effectiveShowSecondaryAside || tabBarInNavBar)

    return {
      layoutType,
      fullBleed,
      showTabBar,
      showBreadcrumb,
      tabBarInNavBar,
      showTabBarInContent,
      effectiveShowSecondaryAside,
      showHybridSubHeader,
    }
  })

  return { routeLayout }
}
