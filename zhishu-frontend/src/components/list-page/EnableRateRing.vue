<template>
  <div class="page-hero__ring enable-ring-wrap" :aria-label="label">
    <svg viewBox="0 0 36 36" class="enable-ring">
      <path
        class="enable-ring__track"
        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
      />
      <path
        class="enable-ring__fill"
        :stroke-dasharray="`${rate}, 100`"
        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
      />
    </svg>
    <div class="enable-ring__center">
      <span class="enable-ring__value">{{ rate }}%</span>
      <span class="enable-ring__label">{{ label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  rate: number
  label?: string
}>()
</script>

<style scoped>
.enable-ring-wrap {
  position: relative;
  width: 72px;
  height: 72px;
  flex-shrink: 0;
}
</style>
