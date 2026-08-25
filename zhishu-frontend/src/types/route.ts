/** 页面级布局类型，通过路由 meta.layout 或预设驱动 */
export type PageLayoutType = 'default' | 'dashboard' | 'fullscreen'

/** 路由 meta 中可配置的布局选项 */
export interface RouteLayoutMeta {
  /** 页面布局类型，优先级高于 fullBleed 等单项配置 */
  layout?: PageLayoutType
  /** 内容区是否铺满（无内边距） */
  fullBleed?: boolean
  /** 隐藏标签栏（覆盖用户全局设置） */
  hideTabBar?: boolean
  /** 隐藏面包屑（覆盖用户全局设置） */
  hideBreadcrumb?: boolean
  /** 隐藏二级侧栏（hybrid 模式下） */
  hideSecondaryAside?: boolean
}

/** 解析后的有效布局配置 */
export interface EffectiveRouteLayout {
  layoutType: PageLayoutType
  fullBleed: boolean
  /** 是否显示标签栏 */
  showTabBar: boolean
  /** 是否显示面包屑（与标签栏互斥，标签栏优先） */
  showBreadcrumb: boolean
  /** hybrid 模式下标签栏是否合并到顶栏下方导航行 */
  tabBarInNavBar: boolean
  /** 标签栏是否渲染在内容区（sidebar 模式） */
  showTabBarInContent: boolean
  effectiveShowSecondaryAside: boolean
  /** hybrid 模式下 Tab/面包屑/折叠按钮独立子顶栏 */
  showHybridSubHeader: boolean
}
