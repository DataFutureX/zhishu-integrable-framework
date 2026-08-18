<template>
  <template v-for="menu in menus" :key="menu.id">
    <el-sub-menu v-if="hasVisibleChildren(menu)" :index="normalizePath(menu.path)">
      <template #title>
        <el-icon><component :is="menu.icon || 'Folder'" /></el-icon>
        <span>{{ menu.title }}</span>
      </template>
      <SidebarMenuItems :menus="getVisibleChildren(menu)" />
    </el-sub-menu>

    <el-menu-item v-else :index="normalizePath(menu.path)">
      <el-icon><component :is="menu.icon || 'Document'" /></el-icon>
      <template #title>{{ menu.title }}</template>
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import type { MenuVO } from '@/types/menu'
import SidebarMenuItems from './SidebarMenuItems.vue'

defineOptions({ name: 'SidebarMenuItems' })

defineProps<{
  menus: MenuVO[]
}>()

const normalizePath = (path?: string) => {
  if (!path) return '/'
  return path.startsWith('/') ? path : `/${path}`
}

const getVisibleChildren = (menu: MenuVO) =>
  (menu.children || []).filter((child) => child.visible !== 0 && child.menuType !== 'PAGE')

const hasVisibleChildren = (menu: MenuVO) => getVisibleChildren(menu).length > 0
</script>
