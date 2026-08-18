import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElNotification } from 'element-plus'
import { isDemoMode } from '@/config/demo'
import {
  getRecentAnnouncementsApi,
  getAnnouncementUnreadCountApi,
  markAnnouncementReadApi,
  markAllAnnouncementsReadApi,
  buildAnnouncementStreamUrl,
} from '@/api/announcement'
import type { AnnouncementVO } from '@/types/announcement'
import {
  ANNOUNCEMENT_PRIORITY_LABEL,
  AnnouncementStatus,
} from '@/types/announcement'
import { parseJsonWithBigInt } from '@/utils/parseJson'

const RECENT_LIMIT = 20
const RECONNECT_DELAY_MS = 5000
const POPUP_AUTO_CLOSE_MS = 8000

export const useAnnouncementStore = defineStore('announcement', () => {
  const recentAnnouncements = ref<AnnouncementVO[]>([])
  const unreadCount = ref(0)
  const streamConnected = ref(false)
  const loadingRecent = ref(false)

  let eventSource: EventSource | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let initialized = false

  const hasUnread = computed(() => unreadCount.value > 0)

  function clearReconnectTimer() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer || !localStorage.getItem('token')) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connectStream()
    }, RECONNECT_DELAY_MS)
  }

  function normalizePayload(raw: unknown): AnnouncementVO | null {
    if (!raw || typeof raw !== 'object') return null
    const payload = raw as Record<string, unknown>
    if (payload.data && typeof payload.data === 'object') {
      return payload.data as AnnouncementVO
    }
    return raw as AnnouncementVO
  }

  function upsertRecent(announcement: AnnouncementVO) {
    if (!announcement.id) return
    const id = String(announcement.id)
    const index = recentAnnouncements.value.findIndex((item) => String(item.id) === id)
    if (index >= 0) {
      recentAnnouncements.value[index] = { ...recentAnnouncements.value[index], ...announcement }
      return
    }
    recentAnnouncements.value.unshift(announcement)
    if (recentAnnouncements.value.length > RECENT_LIMIT) {
      recentAnnouncements.value.length = RECENT_LIMIT
    }
  }

  async function refreshUnreadCount() {
    try {
      unreadCount.value = await getAnnouncementUnreadCountApi()
    } catch (error) {
      console.error('加载未读公告数量失败:', error)
    }
  }

  function showAnnouncementPopup(announcement: AnnouncementVO) {
    const priorityLabel = ANNOUNCEMENT_PRIORITY_LABEL[announcement.priority ?? 0] ?? '普通'
    ElNotification({
      title: `新公告 · ${priorityLabel}`,
      message: announcement.title || '系统公告',
      type: announcement.priority === 2 ? 'warning' : 'info',
      position: 'top-right',
      duration: POPUP_AUTO_CLOSE_MS,
      showClose: true,
      offset: 60,
    })
  }

  function handleIncomingAnnouncement(announcement: AnnouncementVO, notify = true) {
    if (announcement.status !== undefined && announcement.status !== AnnouncementStatus.PUBLISHED) {
      return
    }
    upsertRecent({ ...announcement, read: false })
    unreadCount.value += 1

    if (notify) {
      showAnnouncementPopup(announcement)
    }
  }

  function handleSseMessage(event: MessageEvent<string>) {
    const parsed = parseJsonWithBigInt<unknown>(event.data)
    const announcement = normalizePayload(parsed)
    if (announcement) {
      handleIncomingAnnouncement(announcement)
    }
  }

  function connectStream() {
    if (isDemoMode) return
    const token = localStorage.getItem('token')
    if (!token) return

    disconnectStream(false)

    try {
      eventSource = new EventSource(buildAnnouncementStreamUrl(token))
      eventSource.addEventListener('announcement', handleSseMessage as EventListener)
      eventSource.onmessage = handleSseMessage as EventListener
      eventSource.onopen = () => {
        streamConnected.value = true
        clearReconnectTimer()
      }
      eventSource.onerror = () => {
        streamConnected.value = false
        disconnectStream(false)
        scheduleReconnect()
      }
    } catch (error) {
      console.error('连接公告 SSE 失败:', error)
      scheduleReconnect()
    }
  }

  function disconnectStream(clearData = true) {
    clearReconnectTimer()
    if (eventSource) {
      eventSource.removeEventListener('announcement', handleSseMessage as EventListener)
      eventSource.onmessage = null
      eventSource.onerror = null
      eventSource.onopen = null
      eventSource.close()
      eventSource = null
    }
    streamConnected.value = false
    if (clearData) {
      recentAnnouncements.value = []
      unreadCount.value = 0
      initialized = false
    }
  }

  async function fetchRecentAnnouncements() {
    loadingRecent.value = true
    try {
      const [list, count] = await Promise.all([
        getRecentAnnouncementsApi(RECENT_LIMIT),
        getAnnouncementUnreadCountApi(),
      ])
      recentAnnouncements.value = list ?? []
      unreadCount.value = count ?? 0
    } catch (error) {
      console.error('加载最近公告失败:', error)
    } finally {
      loadingRecent.value = false
    }
  }

  async function init() {
    if (initialized || !localStorage.getItem('token')) return
    initialized = true
    await fetchRecentAnnouncements()
    connectStream()
  }

  function destroy() {
    disconnectStream(true)
  }

  async function markAsRead(id: number | string) {
    await markAnnouncementReadApi(id)
    const target = recentAnnouncements.value.find((item) => String(item.id) === String(id))
    if (target && !target.read) {
      target.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } else {
      await refreshUnreadCount()
    }
  }

  async function markAllAsRead() {
    await markAllAnnouncementsReadApi()
    recentAnnouncements.value.forEach((item) => {
      item.read = true
    })
    unreadCount.value = 0
  }

  return {
    recentAnnouncements,
    unreadCount,
    hasUnread,
    streamConnected,
    loadingRecent,
    init,
    destroy,
    connectStream,
    disconnectStream,
    fetchRecentAnnouncements,
    refreshUnreadCount,
    markAsRead,
    markAllAsRead,
    handleIncomingAnnouncement,
  }
})
