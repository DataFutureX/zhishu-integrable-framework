<template>
  <div class="slide-captcha">
    <div
      ref="imageWrapRef"
      class="slide-captcha__image-wrap"
      :style="{ height: `${displayHeight}px` }"
    >
      <div v-if="loading" class="slide-captcha__loading">
        <el-icon class="is-loading" :size="28"><Loading /></el-icon>
        <span>加载验证码...</span>
      </div>

      <template v-else-if="captcha && imageReady">
        <div
          class="slide-captcha__canvas"
          :style="canvasStyle"
        >
          <img
            :src="backgroundSrc"
            alt="验证码背景"
            class="slide-captcha__background"
            draggable="false"
            :width="naturalWidth"
            :height="naturalHeight"
          />
          <img
            :src="sliderSrc"
            alt="滑块"
            class="slide-captcha__slider"
            draggable="false"
            :width="sliderNaturalWidth"
            :height="sliderNaturalHeight"
            :style="sliderStyle"
          />
        </div>
        <Transition name="fade">
          <div v-if="verifyStatus === 'success'" class="slide-captcha__status slide-captcha__status--success">
            <el-icon><CircleCheck /></el-icon>
            <span>验证成功</span>
          </div>
          <div v-else-if="verifyStatus === 'fail'" class="slide-captcha__status slide-captcha__status--fail">
            <el-icon><CircleClose /></el-icon>
            <span>验证失败，请重试</span>
          </div>
        </Transition>
      </template>

      <img
        v-show="false"
        v-if="captcha"
        :src="backgroundSrc"
        alt=""
        @load="handleBackgroundLoad"
      />
      <img
        v-show="false"
        v-if="captcha"
        :src="sliderSrc"
        alt=""
        @load="handleSliderLoad"
      />

      <div v-if="!loading && !captcha" class="slide-captcha__empty">
        <span>验证码加载失败</span>
        <el-button link type="primary" @click="loadCaptcha">重新加载</el-button>
      </div>

      <button
        type="button"
        class="slide-captcha__refresh"
        :disabled="loading || verifying"
        title="刷新验证码"
        @click="loadCaptcha"
      >
        <el-icon><Refresh /></el-icon>
      </button>
    </div>

    <div
      class="slide-captcha__track"
      :style="{ width: `${displayWidth}px` }"
      :class="{
        'is-dragging': dragging,
        'is-success': verifyStatus === 'success',
        'is-fail': verifyStatus === 'fail',
      }"
    >
      <div class="slide-captcha__track-fill" :style="{ width: `${slideX}px` }" />
      <div
        class="slide-captcha__thumb"
        :style="{ left: `${slideX}px` }"
        @mousedown.prevent="startDrag"
        @touchstart.prevent="startDrag"
      >
        <el-icon v-if="verifying" class="is-loading"><Loading /></el-icon>
        <el-icon v-else-if="verifyStatus === 'success'"><CircleCheck /></el-icon>
        <el-icon v-else><DArrowRight /></el-icon>
      </div>
      <span v-if="slideX === 0 && !verifying" class="slide-captcha__hint">向右拖动滑块完成验证</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { CircleCheck, CircleClose, DArrowRight, Loading, Refresh } from '@element-plus/icons-vue'
import { getCaptchaApi, verifyCaptchaApi, type CaptchaResponse } from '@/api/auth'

const emit = defineEmits<{
  success: [captchaToken: string]
}>()

const DEFAULT_WIDTH = 310
const DEFAULT_HEIGHT = 155
const THUMB_SIZE = 42

const imageWrapRef = ref<HTMLElement>()
const captcha = ref<CaptchaResponse | null>(null)
const loading = ref(false)
const verifying = ref(false)
const dragging = ref(false)
const slideX = ref(0)
const verifyStatus = ref<'idle' | 'success' | 'fail'>('idle')
const displayWidth = ref(DEFAULT_WIDTH)
const naturalWidth = ref(0)
const naturalHeight = ref(0)
const sliderNaturalWidth = ref(0)
const sliderNaturalHeight = ref(0)
const backgroundLoaded = ref(false)
const sliderLoaded = ref(false)

let dragStartX = 0
let dragStartSlideX = 0

const toImageSrc = (value: string) => {
  if (!value) return ''
  if (value.startsWith('data:')) return value
  return `data:image/png;base64,${value}`
}

const backgroundSrc = computed(() => toImageSrc(captcha.value?.backgroundImage ?? ''))
const sliderSrc = computed(() => toImageSrc(captcha.value?.sliderImage ?? ''))

const imageReady = computed(() => backgroundLoaded.value && sliderLoaded.value && naturalWidth.value > 0)

const scale = computed(() => {
  if (!naturalWidth.value) return 1
  return displayWidth.value / naturalWidth.value
})

const displayHeight = computed(() => {
  if (!naturalWidth.value || !naturalHeight.value) return DEFAULT_HEIGHT
  return Math.round(naturalHeight.value * scale.value)
})

const sliderDisplayWidth = computed(() => {
  if (!sliderNaturalWidth.value) return THUMB_SIZE
  return sliderNaturalWidth.value * scale.value
})

const maxSlideX = computed(() => Math.max(displayWidth.value - sliderDisplayWidth.value, 0))

const canvasStyle = computed(() => ({
  width: `${naturalWidth.value}px`,
  height: `${naturalHeight.value}px`,
  transform: `scale(${scale.value})`,
}))

const sliderStyle = computed(() => {
  const sliderY = captcha.value?.sliderY ?? 0
  const offsetY = captcha.value?.sliderImageOffsetY ?? 0
  return {
    top: `${sliderY - offsetY}px`,
    left: `${slideX.value / scale.value}px`,
  }
})

const resetSlider = () => {
  slideX.value = 0
  verifyStatus.value = 'idle'
  sliderNaturalWidth.value = 0
  sliderNaturalHeight.value = 0
  naturalWidth.value = 0
  naturalHeight.value = 0
  backgroundLoaded.value = false
  sliderLoaded.value = false
}

const updateLayout = () => {
  if (!imageWrapRef.value) return
  displayWidth.value = imageWrapRef.value.clientWidth || DEFAULT_WIDTH
}

const handleBackgroundLoad = (event: Event) => {
  const img = event.target as HTMLImageElement
  if (img.naturalWidth > 0) {
    naturalWidth.value = img.naturalWidth
    naturalHeight.value = img.naturalHeight
  }
  backgroundLoaded.value = true
  updateLayout()
}

const handleSliderLoad = (event: Event) => {
  const img = event.target as HTMLImageElement
  if (img.naturalWidth > 0) {
    sliderNaturalWidth.value = img.naturalWidth
    sliderNaturalHeight.value = img.naturalHeight
  }
  sliderLoaded.value = true
}

const loadCaptcha = async () => {
  loading.value = true
  resetSlider()
  captcha.value = null

  try {
    captcha.value = await getCaptchaApi()
  } catch {
    captcha.value = null
  } finally {
    loading.value = false
    await nextTick()
    updateLayout()
  }
}

const toOriginalSlideX = (displayX: number) => Math.round(displayX / scale.value)

const onDragMove = (clientX: number) => {
  if (!dragging.value || verifying.value) return
  const delta = clientX - dragStartX
  slideX.value = Math.min(Math.max(0, dragStartSlideX + delta), maxSlideX.value)
}

const onDragEnd = async () => {
  if (!dragging.value) return
  dragging.value = false

  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
  document.removeEventListener('touchmove', handleTouchMove)
  document.removeEventListener('touchend', handleTouchEnd)

  if (!captcha.value || slideX.value <= 0 || verifying.value) return

  verifying.value = true
  try {
    const result = await verifyCaptchaApi({
      captchaId: captcha.value.captchaId,
      slideX: toOriginalSlideX(slideX.value),
    })

    verifyStatus.value = 'success'
    emit('success', result.captchaToken)
  } catch {
    verifyStatus.value = 'fail'
    window.setTimeout(() => {
      void loadCaptcha()
    }, 600)
  } finally {
    verifying.value = false
  }
}

const handleMouseMove = (event: MouseEvent) => {
  onDragMove(event.clientX)
}

const handleMouseUp = () => {
  void onDragEnd()
}

const handleTouchMove = (event: TouchEvent) => {
  const touch = event.touches[0]
  if (touch) onDragMove(touch.clientX)
}

const handleTouchEnd = () => {
  void onDragEnd()
}

const startDrag = (event: MouseEvent | TouchEvent) => {
  if (loading.value || verifying.value || verifyStatus.value === 'success' || !captcha.value) return

  verifyStatus.value = 'idle'
  dragging.value = true
  dragStartSlideX = slideX.value
  dragStartX = 'touches' in event ? event.touches[0]?.clientX ?? 0 : event.clientX

  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
  document.addEventListener('touchmove', handleTouchMove, { passive: false })
  document.addEventListener('touchend', handleTouchEnd)
}

const handleResize = () => {
  updateLayout()
}

watch(displayWidth, () => {
  slideX.value = Math.min(slideX.value, maxSlideX.value)
})

onMounted(() => {
  void loadCaptcha()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
  document.removeEventListener('touchmove', handleTouchMove)
  document.removeEventListener('touchend', handleTouchEnd)
})

defineExpose({
  reload: loadCaptcha,
})
</script>

<style lang="scss" scoped>
.slide-captcha {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  user-select: none;
}

.slide-captcha__image-wrap {
  position: relative;
  width: 100%;
  max-width: 310px;
  overflow: hidden;
  background: #eef3f8;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px rgba(9, 105, 218, 0.12);
}

.slide-captcha__canvas {
  position: relative;
  transform-origin: top left;
}

.slide-captcha__background {
  display: block;
}

.slide-captcha__slider {
  position: absolute;
  z-index: 2;
  pointer-events: none;
}

.slide-captcha__loading,
.slide-captcha__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  height: 100%;
  min-height: 155px;
  color: #909399;
  font-size: 14px;
}

.slide-captcha__refresh {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: #fff;
  cursor: pointer;
  background: rgba(0, 0, 0, 0.35);
  border: none;
  border-radius: 50%;
  transition: background 0.2s;

  &:hover:not(:disabled) {
    background: rgba(0, 0, 0, 0.5);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.slide-captcha__status {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;

  &--success {
    color: #fff;
    background: rgba(103, 194, 58, 0.72);
  }

  &--fail {
    color: #fff;
    background: rgba(245, 108, 108, 0.72);
  }
}

.slide-captcha__track {
  position: relative;
  height: 42px;
  margin-top: 14px;
  overflow: hidden;
  background: #f6f8fa;
  border: 1px solid #dcdfe6;
  border-radius: 21px;

  &.is-dragging {
    border-color: var(--app-primary);
  }

  &.is-success {
    border-color: #67c23a;
  }

  &.is-fail {
    border-color: #f56c6c;
  }
}

.slide-captcha__track-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, rgba(9, 105, 218, 0.18), rgba(9, 105, 218, 0.28));
  border-radius: 21px 0 0 21px;
  pointer-events: none;
}

.slide-captcha__thumb {
  position: absolute;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: var(--app-primary);
  cursor: grab;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);

  &:active {
    cursor: grabbing;
  }
}

.slide-captcha__hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 42px;
  color: #909399;
  font-size: 13px;
  pointer-events: none;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
