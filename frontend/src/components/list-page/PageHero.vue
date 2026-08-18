<template>
  <section class="page-hero">
    <div class="page-hero__wave" aria-hidden="true" />
    <div class="page-hero__inner">
      <div class="page-hero__main">
        <div v-if="eyebrow" class="page-hero__eyebrow">
          <el-icon v-if="eyebrowIcon"><component :is="eyebrowIcon" /></el-icon>
          {{ eyebrow }}
        </div>
        <h2 class="page-hero__title">{{ title }}</h2>
        <p v-if="description || $slots.description" class="page-hero__desc">
          <slot name="description">{{ description }}</slot>
        </p>
        <div v-if="$slots.actions" class="page-hero__actions">
          <slot name="actions" />
        </div>
      </div>

      <div v-if="metrics?.length" class="page-hero__metrics">
        <div v-for="item in metrics" :key="item.key" class="hero-metric">
          <div
            class="hero-metric__icon"
            :class="item.accent ? `hero-metric__icon--${item.accent}` : ''"
          >
            <el-icon :size="16">
              <component :is="item.icon" />
            </el-icon>
          </div>
          <div>
            <div class="hero-metric__value">{{ item.value }}</div>
            <div class="hero-metric__label">{{ item.label }}</div>
          </div>
        </div>
      </div>

      <EnableRateRing
        v-if="enableRate != null"
        :rate="enableRate"
        :label="ringLabel"
        class="page-hero__ring"
      />

      <slot name="trailing" />
    </div>
  </section>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import EnableRateRing from '@/components/list-page/EnableRateRing.vue'

export interface PageHeroMetric {
  key: string
  label: string
  value: string | number
  icon: Component | string
  accent?: 'primary' | 'success' | 'danger'
}

withDefaults(
  defineProps<{
    title: string
    eyebrow?: string
    eyebrowIcon?: Component | string
    description?: string
    metrics?: PageHeroMetric[]
    enableRate?: number
    ringLabel?: string
  }>(),
  {
    ringLabel: '启用率',
  },
)
</script>
