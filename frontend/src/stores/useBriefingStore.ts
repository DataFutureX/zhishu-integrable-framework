import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  getBriefingRecent,
  getBriefingUnreadCount,
  markBriefingRead,
} from '@/api/ai'
import { isDemoMode } from '@/config/demo'
import type { BriefingDeliveryVO } from '@/types/briefing'

const RECENT_LIMIT = 20
const POLL_INTERVAL_MS = 60_000

export const useBriefingStore = defineStore('briefing', () => {
  const recentBriefings = ref<BriefingDeliveryVO[]>([])
  const unreadCount = ref(0)
  const loadingRecent = ref(false)

  let pollTimer: ReturnType<typeof setInterval> | null = null
  let eventSource: EventSource | null = null
  let initialized = false

  const hasUnread = computed(() => unreadCount.value > 0)

  function isUnread(item: BriefingDeliveryVO) {
    return item.status === 'SUCCESS' && !item.readAt
  }

  function briefingStreamUrl(): string {
    const token = localStorage.getItem('token') || ''
    const encoded = encodeURIComponent(token)
    if (import.meta.env.DEV) {
      return `/api/v1/briefings/stream?token=${encoded}`
    }
    const base = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
    return `${base}/v1/briefings/stream?token=${encoded}`
  }

  function startSse() {
    stopSse()
    if (isDemoMode) return
    if (!localStorage.getItem('token') || typeof EventSource === 'undefined') return
    try {
      eventSource = new EventSource(briefingStreamUrl())
      eventSource.addEventListener('briefing', () => {
        void fetchUnread()
        void fetchRecent()
      })
      eventSource.onerror = () => {
        // 断线后依赖轮询兜底
      }
    } catch (error) {
      console.warn('简报 SSE 订阅失败，将使用轮询:', error)
    }
  }

  function stopSse() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  async function fetchUnread() {
    try {
      const res = await getBriefingUnreadCount()
      unreadCount.value = Number(res?.count ?? 0)
    } catch (error) {
      console.error('加载未读简报数量失败:', error)
    }
  }

  async function fetchRecent(limit = RECENT_LIMIT) {
    loadingRecent.value = true
    try {
      const [list, countRes] = await Promise.all([
        getBriefingRecent(limit),
        getBriefingUnreadCount(),
      ])
      recentBriefings.value = list ?? []
      unreadCount.value = Number(countRes?.count ?? 0)
    } catch (error) {
      console.error('加载最近简报失败:', error)
    } finally {
      loadingRecent.value = false
    }
  }

  async function markRead(id: number | string) {
    await markBriefingRead(id)
    const target = recentBriefings.value.find((item) => String(item.id) === String(id))
    if (target && !target.readAt) {
      target.readAt = new Date().toISOString()
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } else {
      await fetchUnread()
    }
  }

  function startPoll() {
    stopPoll()
    if (!localStorage.getItem('token')) return
    pollTimer = setInterval(() => {
      if (!localStorage.getItem('token')) {
        stopPoll()
        return
      }
      void fetchUnread()
    }, POLL_INTERVAL_MS)
  }

  function stopPoll() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  async function init() {
    if (initialized || !localStorage.getItem('token')) return
    initialized = true
    await fetchRecent()
    startPoll()
    startSse()
  }

  function destroy() {
    stopPoll()
    stopSse()
    recentBriefings.value = []
    unreadCount.value = 0
    initialized = false
  }

  return {
    recentBriefings,
    unreadCount,
    hasUnread,
    loadingRecent,
    isUnread,
    init,
    destroy,
    fetchUnread,
    fetchRecent,
    markRead,
    startPoll,
    stopPoll,
  }
})
