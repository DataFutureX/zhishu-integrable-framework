<template>
  <div
    class="logo-container"
    :class="{
      'logo-container--collapse': collapse,
      'logo-container--compact': compact,
      'logo-container--hydro': variant === 'hydro',
      'logo-container--light': variant === 'light',
    }"
  >
    <div class="logo-mark">
      <span class="logo-glow" aria-hidden="true" />
      <img
        v-if="systemConfigStore.iconUrl"
        :src="systemConfigStore.iconUrl"
        alt="系统图标"
        class="logo-image"
      />
      <el-icon v-else :size="22" class="logo-icon"><Odometer /></el-icon>
    </div>
    <div v-if="!collapse" class="logo-text">
      <span class="logo-eyebrow">{{ systemConfigStore.displayEnglishTitle }}</span>
      <span class="logo-title">{{ systemConfigStore.systemName }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Odometer } from '@element-plus/icons-vue'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'

defineProps<{
  collapse?: boolean
  compact?: boolean
  variant?: 'default' | 'hydro' | 'light'
}>()

const systemConfigStore = useSystemConfigStore()
</script>

<style scoped lang="scss">
.logo-container {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56px;
  padding: 8px 16px;
  background: var(--app-header-bg, linear-gradient(135deg, #0969da 0%, #0550ae 100%));
  box-shadow: var(--app-header-shadow, 0 2px 8px rgba(9, 105, 218, 0.28));

  &--collapse {
    .logo-mark {
      margin-right: 0;
    }
  }

  &--compact {
    width: auto;
    min-width: 220px;
    max-width: none;
    height: 56px;
    justify-content: flex-start;
    padding: 8px 20px 8px 18px;
    box-shadow: none;
    border-radius: 0;

    .logo-mark {
      width: 32px;
      height: 32px;
      margin-right: 10px;
    }

    .logo-image,
    .logo-icon {
      width: 24px;
      height: 24px;
    }

    .logo-text {
      overflow: visible;
    }

    .logo-title {
      font-size: 15px;
      letter-spacing: 0.04em;
      overflow: visible;
      text-overflow: clip;
      max-width: none;
    }

    .logo-eyebrow {
      font-size: 9px;
      letter-spacing: 0.12em;
    }
  }

  .logo-mark {
    position: relative;
    display: grid;
    place-items: center;
    width: 36px;
    height: 36px;
    margin-right: 12px;
    flex-shrink: 0;
  }

  .logo-glow {
    position: absolute;
    inset: -2px;
    border-radius: 50%;
    background:
      radial-gradient(circle at 50% 45%, rgba(255, 255, 255, 0.45) 0%, transparent 62%),
      radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.18) 0%, transparent 72%);
    animation: logo-glow-pulse 3.6s ease-in-out infinite;
    pointer-events: none;
  }

  .logo-text {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 2px;
    min-width: 0;
  }

  &:not(.logo-container--light):not(.logo-container--hydro) {
    .logo-icon {
      position: relative;
      z-index: 1;
      color: #fff;
      filter: drop-shadow(0 0 6px rgba(255, 255, 255, 0.45));
      animation: logo-icon-float 4.8s ease-in-out infinite;
    }

    .logo-image {
      position: relative;
      z-index: 1;
      width: 28px;
      height: 28px;
      object-fit: contain;
      background: transparent;
      /* 深蓝/主色顶栏上转为白色，保证对比度 */
      filter: brightness(0) invert(1) drop-shadow(0 0 6px rgba(255, 255, 255, 0.35));
      animation: logo-icon-float 4.8s ease-in-out infinite;
      flex-shrink: 0;
    }

    .logo-eyebrow {
      font-size: 10px;
      font-weight: 600;
      letter-spacing: 0.12em;
      line-height: 1;
      color: rgba(255, 255, 255, 0.72);
      white-space: nowrap;
    }

    .logo-title {
      font-size: 15px;
      font-weight: 700;
      letter-spacing: 0.04em;
      line-height: 1.15;
      white-space: nowrap;
      overflow: visible;
      text-overflow: clip;
      background: linear-gradient(
        105deg,
        #ffffff 0%,
        #e8f7ff 42%,
        #ffffff 68%,
        #d6eeff 100%
      );
      background-size: 180% 100%;
      -webkit-background-clip: text;
      background-clip: text;
      -webkit-text-fill-color: transparent;
      animation: logo-title-shimmer 7s ease-in-out infinite;
    }
  }

  &--light {
    background: #ffffff;
    box-shadow: none;
    border-bottom: 1px solid #d0d7de;

    &.logo-container--compact {
      background: transparent;
      border-bottom: none;
    }

    .logo-glow {
      background:
        radial-gradient(circle at 50% 45%, rgba(59, 130, 246, 0.22) 0%, transparent 62%),
        radial-gradient(circle at 50% 50%, rgba(59, 130, 246, 0.1) 0%, transparent 72%);
    }

    .logo-icon {
      position: relative;
      z-index: 1;
      color: var(--app-primary);
      filter: none;
      animation: logo-icon-float 4.8s ease-in-out infinite;
    }

    .logo-image {
      position: relative;
      z-index: 1;
      width: 28px;
      height: 28px;
      object-fit: contain;
      background: transparent;
      filter: drop-shadow(0 1px 2px rgba(15, 23, 42, 0.12));
      animation: logo-icon-float 4.8s ease-in-out infinite;
      flex-shrink: 0;
    }

    .logo-eyebrow {
      font-size: 10px;
      font-weight: 600;
      letter-spacing: 0.14em;
      line-height: 1;
      color: #86909c;
      white-space: nowrap;
    }

    .logo-title {
      font-size: 15px;
      font-weight: 700;
      letter-spacing: 0.04em;
      line-height: 1.15;
      white-space: nowrap;
      overflow: visible;
      text-overflow: clip;
      background: none;
      -webkit-background-clip: border-box;
      background-clip: border-box;
      -webkit-text-fill-color: #1d2129;
      color: #1d2129;
      animation: none;
    }
  }

  &--hydro {
    background: linear-gradient(180deg, rgba(6, 28, 48, 0.98) 0%, rgba(4, 18, 32, 0.96) 100%);
    box-shadow:
      0 4px 16px rgba(0, 8, 20, 0.35),
      0 1px 0 rgba(125, 211, 252, 0.1) inset;
    border-bottom: 1px solid rgba(56, 189, 248, 0.15);

    &.logo-container--compact {
      background: transparent;
      box-shadow: none;
      border-bottom: none;
    }

    .logo-glow {
      background:
        radial-gradient(circle at 50% 45%, rgba(125, 211, 252, 0.35) 0%, transparent 62%),
        radial-gradient(circle at 50% 50%, rgba(8, 145, 178, 0.22) 0%, transparent 72%);
    }

    .logo-image,
    .logo-icon {
      filter: brightness(0) invert(1) drop-shadow(0 0 8px rgba(125, 211, 252, 0.45));
    }

    .logo-eyebrow {
      color: rgba(125, 211, 252, 0.72);
    }

    .logo-title {
      font-size: 15px;
      font-weight: 700;
      letter-spacing: 0.04em;
      line-height: 1.15;
      white-space: nowrap;
      overflow: visible;
      text-overflow: clip;
      background: linear-gradient(
        105deg,
        #f0fdff 0%,
        #bae6fd 42%,
        #7dd3fc 68%,
        #e0f2fe 100%
      );
      background-size: 180% 100%;
      -webkit-background-clip: text;
      background-clip: text;
      -webkit-text-fill-color: transparent;
      text-shadow: none;
      animation: logo-title-shimmer 7s ease-in-out infinite;
    }

    .logo-image,
    .logo-icon {
      position: relative;
      z-index: 1;
      animation: logo-icon-float 4.8s ease-in-out infinite;
    }

    .logo-icon {
      color: #7dd3fc;
    }

    .logo-image {
      width: 28px;
      height: 28px;
      object-fit: contain;
      background: transparent;
      flex-shrink: 0;
    }

    .logo-eyebrow {
      font-size: 10px;
      font-weight: 600;
      letter-spacing: 0.12em;
      line-height: 1;
      color: rgba(125, 211, 252, 0.72);
      white-space: nowrap;
    }
  }
}

@keyframes logo-glow-pulse {
  0%,
  100% {
    opacity: 0.72;
    transform: scale(0.96);
  }

  50% {
    opacity: 1;
    transform: scale(1.08);
  }
}

@keyframes logo-icon-float {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-1px);
  }
}

@keyframes logo-title-shimmer {
  0%,
  100% {
    background-position: 0% 50%;
  }

  50% {
    background-position: 100% 50%;
  }
}
</style>
