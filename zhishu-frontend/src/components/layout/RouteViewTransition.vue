<template>
  <div
    class="route-view-transition"
    :class="{
      'route-view-transition--loading': loading && showLoadingMask,
      'route-view-transition--full': fullBleed,
    }"
  >
    <router-view v-slot="{ Component, route: currentRoute }">
      <template v-if="Component">
        <keep-alive :max="max">
          <component
            :is="Component"
            :key="resolveRouteKey(currentRoute)"
            class="route-view-transition__page"
          />
        </keep-alive>
      </template>
      <div v-else class="route-view-transition__placeholder">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        <span>页面加载中...</span>
      </div>
    </router-view>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { Loading } from '@element-plus/icons-vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { usePageTransitionStore } from '@/stores/usePageTransitionStore'

const props = withDefaults(
  defineProps<{
    max?: number
    fullBleed?: boolean
    showLoadingMask?: boolean
    resolveRouteKey?: (route: RouteLocationNormalizedLoaded) => string
  }>(),
  {
    max: 20,
    fullBleed: false,
    showLoadingMask: true,
  },
)

const pageTransitionStore = usePageTransitionStore()
const { loading } = storeToRefs(pageTransitionStore)

const resolveRouteKey = (route: RouteLocationNormalizedLoaded) => {
  if (props.resolveRouteKey) {
    return props.resolveRouteKey(route)
  }
  return route.fullPath
}
</script>

<style scoped lang="scss">
.route-view-transition {
  position: relative;
  width: 100%;
  min-height: 100%;

  &__page {
    width: 100%;
  }

  &__placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    width: 100%;
    min-height: 240px;
    color: #909399;
    font-size: 14px;
  }

  &--full {
    flex: 1;
    min-height: 0;
    height: 100%;
    overflow: hidden;
    display: flex;
    flex-direction: column;

    .route-view-transition__page {
      flex: 1;
      min-height: 0;
      height: 100%;
    }

    .route-view-transition__placeholder {
      flex: 1;
      min-height: 0;
    }
  }

  &--loading::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 20;
    pointer-events: none;
    background: linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.45) 0%,
      rgba(255, 255, 255, 0.12) 28%,
      transparent 56%
    );
    animation: route-view-shimmer 0.65s ease-in-out infinite alternate;
  }

  &--full.route-view-transition--loading::before {
    background: linear-gradient(
      180deg,
      rgba(4, 13, 24, 0.35) 0%,
      rgba(4, 13, 24, 0.08) 36%,
      transparent 62%
    );
  }
}

@keyframes route-view-shimmer {
  from {
    opacity: 0.35;
  }

  to {
    opacity: 0.85;
  }
}
</style>
