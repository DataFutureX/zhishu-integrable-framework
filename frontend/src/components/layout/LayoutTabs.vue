<template>
  <div
    v-if="tabsStore.tabs.length"
    class="layout-tabs"
    :class="{ 'layout-tabs--embedded': embedded }"
  >
    <div ref="scrollRef" class="layout-tabs__scroll">
      <div
        v-for="tab in tabsStore.tabs"
        :key="tab.fullPath"
        :ref="(el) => setTabRef(tab.fullPath, el as HTMLElement | null)"
        class="layout-tabs__item"
        :class="{ 'layout-tabs__item--active': tab.fullPath === route.fullPath }"
        @click="handleSwitch(tab.fullPath)"
        @contextmenu.prevent="openContextMenu($event, tab)"
      >
        <span class="layout-tabs__title">{{ tab.title }}</span>
        <el-icon
          v-if="!tab.affix"
          class="layout-tabs__close"
          @click.stop="handleClose(tab.fullPath)"
        >
          <Close />
        </el-icon>
      </div>
    </div>

    <el-dropdown trigger="click" @command="handleBatchCommand">
      <el-button class="layout-tabs__more" circle size="small">
        <el-icon><ArrowDown /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="current">关闭当前</el-dropdown-item>
          <el-dropdown-item command="others">关闭其他</el-dropdown-item>
          <el-dropdown-item command="all">关闭全部</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <ul
      v-show="contextMenu.visible"
      class="layout-tabs__context"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
    >
      <li @click="handleContextAction('refresh')">刷新</li>
      <li v-if="!contextMenu.affix" @click="handleContextAction('close')">关闭</li>
      <li @click="handleContextAction('others')">关闭其他</li>
      <li @click="handleContextAction('all')">关闭全部</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Close } from '@element-plus/icons-vue'
import { useTabsStore } from '@/stores/useTabsStore'
import { useMenuStore } from '@/stores/useMenuStore'
import type { LayoutTab } from '@/types/tabs'

defineProps<{
  embedded?: boolean
}>()

const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()
const menuStore = useMenuStore()

const scrollRef = ref<HTMLElement>()
const tabRefs = new Map<string, HTMLElement>()

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  fullPath: '',
  affix: false,
})

const setTabRef = (fullPath: string, el: HTMLElement | null) => {
  if (el) {
    tabRefs.set(fullPath, el)
  } else {
    tabRefs.delete(fullPath)
  }
}

const scrollActiveIntoView = () => {
  nextTick(() => {
    const activeEl = tabRefs.get(route.fullPath)
    activeEl?.scrollIntoView({ behavior: 'smooth', inline: 'nearest', block: 'nearest' })
  })
}

const handleSwitch = (fullPath: string) => {
  if (fullPath !== route.fullPath) {
    router.push(fullPath)
  }
}

const navigateAfterClose = (closedFullPath: string, removedIndex: number) => {
  if (route.fullPath !== closedFullPath) return

  if (!tabsStore.tabs.length) {
    router.push(menuStore.defaultPath)
    return
  }

  const nextTab =
    tabsStore.tabs[removedIndex] ?? tabsStore.tabs[removedIndex - 1] ?? tabsStore.tabs[0]
  router.push(nextTab.fullPath)
}

const navigateToHomeDashboard = () => {
  router.push(menuStore.defaultPath)
}

const handleClose = (fullPath: string) => {
  const removedIndex = tabsStore.removeTab(fullPath)
  if (removedIndex !== null) {
    navigateAfterClose(fullPath, removedIndex)
  }
}

const handleBatchCommand = (command: string | number | object) => {
  if (command === 'current') {
    handleClose(route.fullPath)
    return
  }

  if (command === 'others') {
    tabsStore.closeOtherTabs(route.fullPath)
    return
  }

  if (command === 'all') {
    tabsStore.closeAllTabs()
    navigateToHomeDashboard()
    return
  }
}

const openContextMenu = (event: MouseEvent, tab: LayoutTab) => {
  contextMenu.visible = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.fullPath = tab.fullPath
  contextMenu.affix = !!tab.affix
}

const hideContextMenu = () => {
  contextMenu.visible = false
}

const handleContextAction = (action: string) => {
  const { fullPath, affix } = contextMenu
  hideContextMenu()

  if (action === 'refresh') {
    tabsStore.refreshTab(fullPath)
    if (fullPath !== route.fullPath) {
      router.push(fullPath)
    }
    return
  }

  if (action === 'close' && !affix) {
    handleClose(fullPath)
    return
  }

  if (action === 'others') {
    tabsStore.closeOtherTabs(fullPath)
    if (fullPath !== route.fullPath) {
      router.push(fullPath)
    }
    return
  }

  if (action === 'all') {
    tabsStore.closeAllTabs()
    navigateToHomeDashboard()
  }
}

watch(
  () => route.fullPath,
  () => {
    scrollActiveIntoView()
  },
)

onMounted(() => {
  document.addEventListener('click', hideContextMenu)
})

onUnmounted(() => {
  document.removeEventListener('click', hideContextMenu)
})
</script>

<style scoped lang="scss">
.layout-tabs {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  height: var(--app-tabs-bar-height);
  padding: 0 10px;
  background: var(--app-surface-bg);
  border-bottom: 1px solid var(--app-border-color);
  flex-shrink: 0;

  &--embedded {
    height: auto;
    padding: 0;
    background: transparent;
    border-bottom: none;
    gap: 4px;
  }

  &__scroll {
    display: flex;
    align-items: center;
    gap: 4px;
    flex: 1;
    min-width: 0;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  &__item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 24px;
    padding: 0 8px;
    border-radius: 3px;
    background: var(--app-surface-muted);
    color: var(--app-text-regular);
    font-size: 12px;
    cursor: pointer;
    white-space: nowrap;
    transition: all 0.2s ease;
    flex-shrink: 0;

    &:hover {
      color: var(--app-primary);
      background: color-mix(in srgb, var(--app-primary) 8%, transparent);
    }

    &--active {
      color: var(--app-primary);
      background: color-mix(in srgb, var(--app-primary) 12%, transparent);
      font-weight: 600;
    }
  }

  &__title {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1;
  }

  &__close {
    font-size: 11px;
    border-radius: 50%;
    transition: all 0.2s ease;

    &:hover {
      color: #fff;
      background: #c0c4cc;
    }
  }

  &__more {
    flex-shrink: 0;
    width: 22px;
    height: 22px;
    padding: 0;
    border: none;
    background: transparent;
    color: var(--app-text-secondary);

    &:hover {
      color: var(--app-primary);
      background: color-mix(in srgb, var(--app-primary) 8%, transparent);
    }
  }

  &__context {
    position: fixed;
    z-index: 3000;
    min-width: 120px;
    margin: 0;
    padding: 6px 0;
    list-style: none;
    background: var(--app-surface-bg);
    border: 1px solid var(--app-border-color);
    border-radius: 6px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);

    li {
      padding: 8px 16px;
      font-size: 13px;
      color: var(--app-text-regular);
      cursor: pointer;

      &:hover {
        color: var(--app-primary);
        background: color-mix(in srgb, var(--app-primary) 8%, transparent);
      }
    }
  }
}
</style>
