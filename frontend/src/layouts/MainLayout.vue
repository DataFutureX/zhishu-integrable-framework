<template>
  <DemoBanner />
  <el-container
    class="layout-container"
    :class="{
      'layout-container--hybrid': layoutStore.isHybridLayout,
      'layout-container--hydro': isHydroTheme,
      'layout-container--light': isLightTheme,
      'layout-container--default': isDefaultTheme,
    }"
  >
    <el-header v-if="layoutStore.isHybridLayout" class="hybrid-top-header">
      <div v-if="isHydroTheme" class="hybrid-top-header__grid" aria-hidden="true" />
      <LayoutLogo
        class="hybrid-top-header__logo"
        :collapse="false"
        compact
        :variant="logoVariant"
      />
      <TopNavMenu
        class="hybrid-top-header__nav"
        :theme="hybridNavTheme"
      />
      <div class="hybrid-top-header__aside">
        <LayoutHeaderActions
          class="hybrid-top-header__actions"
          :on-primary="headerActionsOnPrimary"
          :user-initial="getUserInitial"
          :avatar-color="getUserAvatarColor"
          :user-name="userStore.userName || '管理员'"
          @command="handleCommand"
        />
      </div>
    </el-header>

    <el-container :class="layoutStore.isHybridLayout ? 'hybrid-body' : 'layout-body'">
      <LayoutAside
        v-if="layoutStore.isSidebarLayout"
        variant="primary"
        :collapse="isCollapse"
        :menus="menuStore.sidebarNavigationMenus"
        :active-menu="activeMenu"
        :copyright="sidebarCopyright"
      />

      <LayoutAside
        v-if="routeLayout.effectiveShowSecondaryAside"
        variant="secondary"
        :collapse="isCollapse"
        :menus="secondaryMenus"
        :active-menu="activeMenu"
        :menu-key="activeTopMenuPath"
        :copyright="sidebarCopyright"
      />

      <div
        v-if="showHybridCopyrightBar"
        class="hybrid-copyright-bar"
      >
        <span class="hybrid-copyright-bar__rule" aria-hidden="true" />
        <p class="copyright">{{ sidebarCopyright }}</p>
      </div>

      <el-container class="main-container">
        <el-header
          v-if="layoutStore.isSidebarLayout"
          class="layout-header"
        >
          <LayoutNavBar
            :collapse="isCollapse"
            :show-collapse="true"
            :show-tab-bar="false"
            :show-breadcrumb="routeLayout.showBreadcrumb"
            :breadcrumbs="breadcrumbs"
            @toggle-collapse="toggleCollapse"
          />
          <LayoutHeaderActions
            :user-initial="getUserInitial"
            :avatar-color="getUserAvatarColor"
            :user-name="userStore.userName || '管理员'"
            @command="handleCommand"
          />
        </el-header>

        <el-header
          v-else-if="routeLayout.showHybridSubHeader"
          class="layout-header layout-header--sub"
        >
          <LayoutNavBar
            :collapse="isCollapse"
            :show-collapse="routeLayout.effectiveShowSecondaryAside"
            :show-tab-bar="routeLayout.tabBarInNavBar"
            :show-breadcrumb="routeLayout.showBreadcrumb"
            :breadcrumbs="breadcrumbs"
            @toggle-collapse="toggleCollapse"
          />
        </el-header>

        <el-main
          class="layout-main"
          :class="{ 'layout-main--full-bleed': routeLayout.fullBleed }"
        >
          <LayoutContent
            :full-bleed="routeLayout.fullBleed"
            :show-tab-bar="routeLayout.showTabBarInContent"
          />
        </el-main>
      </el-container>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ThemeStyle } from '@/types'
import { useUserStore } from '@/stores/useUserStore'
import { useMenuStore } from '@/stores/useMenuStore'
import { useThemeStore } from '@/stores/useThemeStore'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'
import { useLayoutStore } from '@/stores/useLayoutStore'
import { useLayoutMenu } from '@/composables/useLayoutMenu'
import { useRouteLayout } from '@/composables/useRouteLayout'
import LayoutLogo from '@/components/layout/LayoutLogo.vue'
import DemoBanner from '@/components/common/DemoBanner.vue'
import LayoutAside from '@/components/layout/LayoutAside.vue'
import TopNavMenu from '@/components/layout/TopNavMenu.vue'
import LayoutHeaderActions from '@/components/layout/LayoutHeaderActions.vue'
import LayoutNavBar from '@/components/layout/LayoutNavBar.vue'
import LayoutContent from '@/components/layout/LayoutContent.vue'
import { logoutAndRedirect } from '@/utils/logout'
import { useAnnouncementStore } from '@/stores/useAnnouncementStore'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const menuStore = useMenuStore()
const themeStore = useThemeStore()
const systemConfigStore = useSystemConfigStore()
const layoutStore = useLayoutStore()
const { secondaryMenus, showSecondaryAside, activeTopMenuPath } = useLayoutMenu()
const { routeLayout } = useRouteLayout(showSecondaryAside)

const isCollapse = ref(false)
const activeMenu = computed(() => route.path)
const breadcrumbs = computed(() => route.matched.filter((item) => item.meta?.title))
const sidebarCopyright = computed(() => systemConfigStore.copyright || '')

const showHybridCopyrightBar = computed(
  () =>
    layoutStore.isHybridLayout &&
    !!sidebarCopyright.value &&
    !routeLayout.value.effectiveShowSecondaryAside,
)

const isHydroTheme = computed(() => themeStore.currentTheme === ThemeStyle.BLUE)
const isLightTheme = computed(() => themeStore.currentTheme === ThemeStyle.LIGHT)
const isDefaultTheme = computed(() => themeStore.currentTheme === ThemeStyle.DEFAULT)

const logoVariant = computed(() => {
  if (isHydroTheme.value) return 'hydro'
  if (isLightTheme.value || isDefaultTheme.value) return 'light'
  return 'default'
})

const headerActionsOnPrimary = computed(() => {
  const theme = themeStore.currentTheme
  return theme === ThemeStyle.BLUE || theme === ThemeStyle.DARK
})

const hybridNavTheme = computed(() => {
  if (themeStore.currentTheme === ThemeStyle.BLUE) return 'hydro'
  if (themeStore.currentTheme === ThemeStyle.DARK) return 'default'
  return 'light'
})

const getUserInitial = computed(() => {
  const name = userStore.userName || '管理员'
  return name.charAt(0).toUpperCase()
})

const getUserAvatarColor = computed(() => {
  const name = userStore.userName || 'admin'
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  const hue = Math.abs(hash % 360)
  return `hsl(${hue}, 70%, 50%)`
})

const announcementStore = useAnnouncementStore()

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认要退出登录吗?', '提示', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      })
      logoutAndRedirect(router)
    } catch {
      // 用户取消
    }
    return
  }

  if (command === 'profile') {
    router.push('/profile/info')
  }
}

onMounted(() => {
  userStore.initUserInfo()
  themeStore.initTheme()
  layoutStore.initLayoutMode()
  systemConfigStore.fetchConfig()
  announcementStore.init()
})

onUnmounted(() => {
  announcementStore.destroy()
})
</script>

<style lang="scss" scoped>
@use '@/styles/theme-blue-glass.scss' as glass;

.layout-container {
  height: 100vh;
  background: var(--app-content-bg);

  &--hybrid {
    flex-direction: column;
  }
}

.hybrid-top-header {
  --el-header-height: var(--app-header-height);
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0;
  height: var(--app-header-height);
  min-height: var(--app-header-height);
  padding: 0 18px 0 0;
  background: var(--app-header-bg);
  box-shadow: var(--app-header-shadow);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  color: var(--app-header-text);
  overflow: hidden;

  &__grid {
    @include glass.blue-layout-header-grid;
  }

  &__logo {
    position: relative;
    z-index: 1;
    flex-shrink: 0;
    min-width: max-content;
  }

  &__nav {
    position: relative;
    z-index: 1;
    min-width: 0;
    padding: 0 12px;
  }

  &__aside {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;
    padding-left: 14px;
  }

  &__actions {
    flex-shrink: 0;
  }

  :deep(.logo-container--compact) {
    background: transparent;
    box-shadow: none;
    width: auto;
    min-width: max-content;
  }
}

.layout-container--light {
  .hybrid-top-header {
    border-bottom-color: #d0d7de;
    background: #ffffff;

    &__nav {
      border-left: none;
      border-right: none;
      padding: 0 8px 0 4px;
    }

    &__aside {
      padding-left: 8px;
    }
  }

  .layout-aside {
    box-shadow: none;
    border-right: 1px solid #d0d7de;

    .sidebar-footer {
      background: linear-gradient(180deg, rgba(255, 255, 255, 0) 0%, #ffffff 42%);
      border-top: none;

      &__rule {
        background: #d0d7de;
        opacity: 1;
      }

      .copyright {
        color: #656d76;
      }
    }
  }

  .hybrid-copyright-bar {
    &__rule {
      background: #d0d7de;
    }

    .copyright {
      color: #656d76;
    }
  }

  .layout-header {
    box-shadow: 0 1px 0 #d0d7de;
  }
}

.layout-container--default {
  .hybrid-top-header {
    border-bottom: 1px solid #d0d7de;
    background: #ffffff;
    color: #1f2328;

    &__nav {
      border-left: none;
      border-right: none;
    }
  }

  .layout-aside {
    box-shadow: none;
    border-right: 1px solid #30363d;
  }

  .layout-header {
    box-shadow: 0 1px 0 #d0d7de;
    background: #ffffff;
    color: #1f2328;
  }
}

.layout-container--hydro {
  .hybrid-top-header {
    @include glass.blue-layout-header;
    border-bottom-color: rgba(56, 189, 248, 0.18);

    &__nav {
      border-left: none;
      border-right: none;
    }

    :deep(.layout-settings-trigger),
    :deep(.notification-trigger) {
      color: rgba(186, 230, 253, 0.88);

      &:hover {
        color: glass.$hydro-aqua;
        background: rgba(56, 189, 248, 0.12);
      }
    }

    :deep(.user-info) {
      &:hover {
        background-color: rgba(56, 189, 248, 0.1);

        .username {
          color: glass.$hydro-aqua;
        }
      }

      .username {
        color: rgba(224, 252, 255, 0.92);
      }
    }
  }

  .layout-aside {
    box-shadow: 2px 0 16px rgba(0, 8, 20, 0.35);
    border-right: 1px solid rgba(56, 189, 248, 0.12);
  }

  .layout-header {
    background: rgba(6, 28, 48, 0.92);
    border-bottom-color: rgba(56, 189, 248, 0.15);
    box-shadow: 0 2px 12px rgba(0, 8, 20, 0.25);
  }
}

.layout-body,
.hybrid-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.hybrid-body {
  position: relative;
}

.hybrid-copyright-bar {
  position: absolute;
  left: 14px;
  bottom: 10px;
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  pointer-events: none;

  &__rule {
    display: block;
    width: 18px;
    height: 1px;
    border-radius: 1px;
    background: color-mix(in srgb, var(--app-primary) 50%, transparent);
    opacity: 0.75;
  }

  .copyright {
    margin: 0;
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: 11px;
    font-weight: 500;
    letter-spacing: 0.02em;
    line-height: 1.35;
    color: var(--app-text-secondary);
  }
}

.main-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.layout-header {
  --el-header-height: var(--app-header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--app-header-height);
  min-height: var(--app-header-height);
  padding: 0 24px;
  background: var(--app-surface-bg);
  box-shadow: var(--app-shadow-sm);
  border-bottom: 1px solid var(--app-border-color);
  flex-shrink: 0;

  &--sub {
    --el-header-height: 40px;
    height: 40px;
    min-height: 40px;
    box-shadow: none;
    padding: 0 16px;
  }
}

.layout-main {
  flex: 1;
  min-height: 0;
  padding: 0;
  background: var(--app-content-bg-gradient);
  overflow: hidden;
  display: flex;
  flex-direction: column;

  &--full-bleed {
    background: #000;
  }
}
</style>
