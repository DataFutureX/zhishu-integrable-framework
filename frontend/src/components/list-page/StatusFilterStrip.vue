<template>
  <div class="status-strip">
    <span class="status-strip__label">{{ label }}</span>
    <div class="status-strip__chips">
      <button
        v-for="option in options"
        :key="String(option.value)"
        type="button"
        class="status-chip"
        :class="{ 'is-active': modelValue === option.value }"
        @click="$emit('update:modelValue', option.value)"
      >
        <span
          v-if="option.dot"
          class="status-chip__dot"
          :class="`status-chip__dot--${option.dot}`"
        />
        {{ option.label }}
        <span v-if="option.count != null" class="status-chip__count">{{ option.count }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts" generic="T = unknown">
export interface StatusFilterOption<T = unknown> {
  label: string
  value: T
  dot?: 'enabled' | 'disabled'
  count?: number
}

defineProps<{
  label?: string
  modelValue: T
  options: StatusFilterOption<T>[]
}>()

defineEmits<{
  'update:modelValue': [value: T]
}>()
</script>

<style scoped lang="scss">
.status-chip__count {
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 8px;
  font-size: 11px;
  background: color-mix(in srgb, var(--app-primary) 10%, transparent);
  color: var(--app-primary);
}
</style>
