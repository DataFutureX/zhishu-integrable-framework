<template>
  <div class="list-toolbar">
    <span class="list-toolbar__title">{{ title }}</span>
    <span v-if="hint || $slots.hint" class="list-toolbar__hint">
      <slot name="hint">{{ hint }}</slot>
    </span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  hint?: string
}>()
</script>
