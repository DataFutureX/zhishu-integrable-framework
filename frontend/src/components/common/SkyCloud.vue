<template>
  <div
    class="sky-cloud"
    :class="[`sky-cloud--${variant}`, { 'sky-cloud--flip': flip }]"
    :style="rootStyle"
    aria-hidden="true"
  >
    <svg
      class="sky-cloud__svg"
      viewBox="0 0 240 100"
      xmlns="http://www.w3.org/2000/svg"
      preserveAspectRatio="xMidYMid meet"
    >
      <defs>
        <linearGradient :id="gradId" x1="20%" y1="0%" x2="50%" y2="100%">
          <stop offset="0%" stop-color="#ffffff" stop-opacity="1" />
          <stop offset="45%" stop-color="#f7fbff" stop-opacity="0.96" />
          <stop offset="100%" stop-color="#dcecff" stop-opacity="0.78" />
        </linearGradient>
        <filter :id="blurId" x="-15%" y="-35%" width="130%" height="170%">
          <feGaussianBlur in="SourceGraphic" :stdDeviation="blur" />
        </filter>
      </defs>
      <g :filter="`url(#${blurId})`">
        <!-- 积云轮廓：底部平缓、顶部鼓包 -->
        <path
          :fill="`url(#${gradId})`"
          d="M28 74
             C18 74 10 67 10 58
             C10 48 18 41 28 40
             C30 27 41 18 55 18
             C62 8 76 2 92 2
             C108 2 122 8 130 18
             C142 14 156 16 166 24
             C178 20 194 26 200 38
             C214 40 228 50 228 62
             C228 72 218 78 206 78
             L40 78
             C34 78 28 76 28 74 Z"
        />
        <!-- 高光让体积感更强 -->
        <ellipse cx="96" cy="34" rx="34" ry="16" fill="#ffffff" opacity="0.45" />
        <ellipse cx="150" cy="40" rx="28" ry="12" fill="#ffffff" opacity="0.28" />
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    variant?: 'near' | 'mid' | 'far'
    flip?: boolean
    top?: string
    left?: string
    right?: string
    width?: string
    duration?: string
    delay?: string
  }>(),
  {
    variant: 'mid',
    flip: false,
    top: '20%',
    left: undefined,
    right: undefined,
    width: '320px',
    duration: '40s',
    delay: '0s',
  },
)

const uid = useId().replace(/:/g, '')
const gradId = `sky-cloud-grad-${uid}`
const blurId = `sky-cloud-blur-${uid}`

const blur = computed(() => {
  if (props.variant === 'near') return 0.6
  if (props.variant === 'far') return 2.2
  return 1.2
})

const rootStyle = computed(() => ({
  top: props.top,
  left: props.left,
  right: props.right,
  width: props.width,
  animationDuration: props.duration,
  animationDelay: props.delay,
}))
</script>

<style scoped lang="scss">
.sky-cloud {
  position: absolute;
  height: auto;
  aspect-ratio: 240 / 100;
  pointer-events: none;
  will-change: transform;
  animation-name: sky-cloud-drift;
  animation-timing-function: linear;
  animation-iteration-count: infinite;

  &--near {
    opacity: 0.92;
    z-index: 2;
  }

  &--mid {
    opacity: 0.68;
    z-index: 1;
  }

  &--far {
    opacity: 0.42;
    z-index: 0;
  }

  &--flip {
    .sky-cloud__svg {
      transform: scaleX(-1);
    }
  }

  &__svg {
    display: block;
    width: 100%;
    height: 100%;
    overflow: visible;
  }
}

@keyframes sky-cloud-drift {
  0% {
    transform: translate3d(0, 0, 0);
  }
  25% {
    transform: translate3d(7vw, -6px, 0);
  }
  50% {
    transform: translate3d(14vw, 4px, 0);
  }
  75% {
    transform: translate3d(21vw, -4px, 0);
  }
  100% {
    transform: translate3d(28vw, 0, 0);
  }
}
</style>
