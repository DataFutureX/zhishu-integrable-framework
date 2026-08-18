<template>
  <div v-if="isDemoMode && visible" class="demo-banner" role="status">
    <el-icon class="demo-banner__icon"><InfoFilled /></el-icon>
    <span class="demo-banner__text">
      当前为<strong>演示模式</strong>，接口行为对齐正式环境，数据均为模拟。账号：<code>demo</code> / <code>demo123</code>
    </span>
    <span class="demo-banner__countdown">{{ remaining }}s 后隐藏</span>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { isDemoMode } from '@/config/demo'

const AUTO_HIDE_SECONDS = 6

/** 仅当前页面会话有效，刷新浏览器后重置 */
let bannerStartAt = 0
let bannerHidden = false
let sharedTimer: ReturnType<typeof setInterval> | null = null
const remainingRefs = new Set<ReturnType<typeof ref<number>>>()
const visibleRefs = new Set<ReturnType<typeof ref<boolean>>>()

const syncAll = (remaining: number, visible: boolean) => {
  remainingRefs.forEach((r) => {
    r.value = remaining
  })
  visibleRefs.forEach((r) => {
    r.value = visible
  })
}

const getRemaining = () => {
  if (!bannerStartAt) {
    bannerStartAt = Date.now()
    return AUTO_HIDE_SECONDS
  }
  const elapsed = Math.floor((Date.now() - bannerStartAt) / 1000)
  return Math.max(AUTO_HIDE_SECONDS - elapsed, 0)
}

const hideBanner = () => {
  bannerHidden = true
  syncAll(0, false)
  if (sharedTimer) {
    clearInterval(sharedTimer)
    sharedTimer = null
  }
}

const ensureTimer = () => {
  if (sharedTimer || bannerHidden || !isDemoMode) return
  sharedTimer = setInterval(() => {
    const left = getRemaining()
    if (left <= 0) {
      hideBanner()
      return
    }
    syncAll(left, true)
  }, 1000)
}

const visible = ref(isDemoMode && !bannerHidden && getRemaining() > 0)
const remaining = ref(getRemaining())

remainingRefs.add(remaining)
visibleRefs.add(visible)

onMounted(() => {
  if (!isDemoMode || bannerHidden) {
    visible.value = false
    return
  }
  const left = getRemaining()
  if (left <= 0) {
    hideBanner()
    return
  }
  syncAll(left, true)
  ensureTimer()
})

onUnmounted(() => {
  remainingRefs.delete(remaining)
  visibleRefs.delete(visible)
})
</script>

<style lang="scss" scoped>
.demo-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 6px 16px;
  background: linear-gradient(90deg, #e6a23c 0%, #f0c78a 100%);
  color: #5c3d00;
  font-size: 13px;
  line-height: 1.5;
  z-index: 9999;

  &__icon {
    flex-shrink: 0;
    font-size: 16px;
  }

  &__text {
    code {
      padding: 1px 6px;
      border-radius: 4px;
      background: rgba(255, 255, 255, 0.55);
      font-family: inherit;
    }
  }

  &__countdown {
    flex-shrink: 0;
    margin-left: 4px;
    padding: 0 8px;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.45);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    opacity: 0.9;
  }
}
</style>
