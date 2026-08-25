<template>

  <div class="layout-content" :class="{ 'layout-content--full-bleed': fullBleed }">

    <LayoutTabs v-if="showTabBar" />

    <div class="layout-content-view" :class="{ 'layout-content-view--full': fullBleed }">

      <RouteViewTransition

        :max="20"

        :full-bleed="fullBleed"

        :show-loading-mask="!fullBleed"

        :resolve-route-key="resolveRouteKey"

      />

    </div>

  </div>

</template>



<script setup lang="ts">

import { watch } from 'vue'

import { useRoute } from 'vue-router'

import type { RouteLocationNormalizedLoaded } from 'vue-router'

import LayoutTabs from '@/components/layout/LayoutTabs.vue'

import RouteViewTransition from '@/components/layout/RouteViewTransition.vue'

import { useTabsStore } from '@/stores/useTabsStore'



defineProps<{

  fullBleed?: boolean

  showTabBar?: boolean

}>()



const route = useRoute()

const tabsStore = useTabsStore()



const resolveRouteKey = (currentRoute: RouteLocationNormalizedLoaded) =>

  `${currentRoute.fullPath}:${tabsStore.getRefreshKey(currentRoute.fullPath)}`



watch(

  () => route.fullPath,

  () => {

    tabsStore.addTab(route)

  },

  { immediate: true },

)

</script>



<style scoped lang="scss">

.layout-content {

  display: flex;

  flex-direction: column;

  flex: 1;

  min-height: 0;

  overflow: hidden;



  &--full-bleed {

    .layout-content-view {

      background: var(--el-bg-color, #fff);

    }

  }

}



.layout-content-view {

  flex: 1;

  min-height: 0;

  padding: var(--app-content-padding);

  overflow-y: auto;

  overflow-x: hidden;

  background: var(--app-content-bg-gradient);



  &--full {
    padding: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }



  &::-webkit-scrollbar {

    width: 8px;

    height: 8px;

  }



  &::-webkit-scrollbar-thumb {

    background: color-mix(in srgb, var(--app-primary) 30%, transparent);

    border-radius: 4px;

    transition: background 0.3s;



    &:hover {

      background: color-mix(in srgb, var(--app-primary) 50%, transparent);

    }

  }



  &::-webkit-scrollbar-track {

    background: rgba(0, 0, 0, 0.05);

    border-radius: 4px;

  }

}

</style>


