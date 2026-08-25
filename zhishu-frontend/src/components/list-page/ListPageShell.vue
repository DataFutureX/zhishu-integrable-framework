<template>
  <div class="list-page">
    <PageHero
      v-if="showHero"
      :title="heroTitle!"
      :eyebrow="heroEyebrow"
      :eyebrow-icon="heroEyebrowIcon"
      :metrics="heroMetrics"
      :enable-rate="heroEnableRate"
      :ring-label="heroRingLabel"
    >
      <template v-if="$slots.heroDescription" #description>
        <slot name="heroDescription" />
      </template>
      <template v-if="$slots.heroActions" #actions>
        <slot name="heroActions" />
      </template>
    </PageHero>

    <section class="list-page__panel">
      <slot name="strip" />
      <slot name="filter" />
      <slot name="toolbar" />
      <div v-loading="loading" class="list-page__table">
        <slot />
      </div>
      <div v-if="$slots.pagination" class="list-page__pagination">
        <slot name="pagination" />
      </div>
    </section>

    <slot name="extra" />
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import PageHero, { type PageHeroMetric } from '@/components/list-page/PageHero.vue'

withDefaults(
  defineProps<{
    loading?: boolean
    showHero?: boolean
    heroTitle?: string
    heroEyebrow?: string
    heroEyebrowIcon?: Component | string
    heroMetrics?: PageHeroMetric[]
    heroEnableRate?: number
    heroRingLabel?: string
  }>(),
  {
    loading: false,
    showHero: true,
    heroRingLabel: '启用率',
  },
)
</script>
