import { defineStore } from 'pinia'

export const usePageTransitionStore = defineStore('pageTransition', {
  state: () => ({
    loading: false,
  }),

  actions: {
    start() {
      this.loading = true
    },

    finish() {
      this.loading = false
    },
  },
})
