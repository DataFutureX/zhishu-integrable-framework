<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-end"
    :width="400"
    trigger="click"
    popper-class="notification-popover"
    @show="handlePopoverShow"
  >
    <template #reference>
      <el-badge :value="totalUnreadCount" :hidden="totalUnreadCount <= 0" :max="99">
        <el-button
          class="notification-trigger"
          :class="{ 'notification-trigger--on-primary': onPrimary }"
          circle
        >
          <el-icon :size="18"><Bell /></el-icon>
        </el-button>
      </el-badge>
    </template>

    <div class="notification-panel">
      <div v-loading="announcementStore.loadingRecent">
        <div class="notification-panel__header">
          <span class="notification-panel__title">系统公告</span>
          <div class="notification-panel__actions">
            <el-button
              v-if="announcementStore.hasUnread"
              type="primary"
              link
              size="small"
              @click="handleMarkAllAnnouncementsRead"
            >
              全部已读
            </el-button>
            <el-button
              v-if="userStore.isAdmin"
              type="primary"
              link
              size="small"
              @click="goAnnouncementPage"
            >
              公告管理
            </el-button>
          </div>
        </div>

        <div v-if="announcementStore.recentAnnouncements.length > 0" class="notification-list">
          <div
            v-for="item in announcementStore.recentAnnouncements"
            :key="String(item.id)"
            class="notification-item"
            :class="{ 'notification-item--unread': !item.read }"
            @click.stop="handleAnnouncementClick(item, $event)"
          >
            <div class="notification-item__top">
              <el-tag
                :type="announcementPriorityType(item.priority)"
                size="small"
                effect="plain"
              >
                {{ announcementPriorityLabel(item.priority) }}
              </el-tag>
              <span class="notification-item__time">
                {{ formatRelativeTime(item.publishTime || item.createTime) }}
              </span>
            </div>
            <div class="notification-item__title">{{ item.title }}</div>
            <div class="notification-item__message">{{ item.content }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无公告" :image-size="72" />
      </div>
    </div>
  </el-popover>

  <el-dialog
    v-model="detailVisible"
    title="公告详情"
    width="520px"
    append-to-body
    destroy-on-close
    @closed="handleDetailClosed"
  >
    <div v-if="selectedAnnouncement" class="announcement-detail">
      <h3 class="announcement-detail__title">{{ selectedAnnouncement.title }}</h3>
      <div class="announcement-detail__meta">
        <el-tag
          :type="announcementPriorityType(selectedAnnouncement.priority)"
          size="small"
          effect="plain"
        >
          {{ announcementPriorityLabel(selectedAnnouncement.priority) }}
        </el-tag>
        <span v-if="selectedAnnouncement.publisherName">
          发布人：{{ selectedAnnouncement.publisherName }}
        </span>
        <span v-if="selectedAnnouncement.publishTime">
          {{ formatDateTime(selectedAnnouncement.publishTime) }}
        </span>
      </div>
      <div class="announcement-detail__content">{{ selectedAnnouncement.content }}</div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAnnouncementStore } from '@/stores/useAnnouncementStore'
import { useUserStore } from '@/stores/useUserStore'
import type { AnnouncementVO } from '@/types/announcement'
import { ANNOUNCEMENT_PRIORITY_LABEL } from '@/types/announcement'
import { formatDateTime } from '@/utils/format'

defineProps<{
  onPrimary?: boolean
}>()

const router = useRouter()
const announcementStore = useAnnouncementStore()
const userStore = useUserStore()

const popoverVisible = ref(false)
const detailVisible = ref(false)
const selectedAnnouncement = ref<AnnouncementVO | null>(null)
const pendingReadId = ref<number | string | null>(null)

const totalUnreadCount = computed(() => announcementStore.unreadCount)

function announcementPriorityLabel(value?: number) {
  return ANNOUNCEMENT_PRIORITY_LABEL[value ?? 0] ?? '普通'
}

function announcementPriorityType(value?: number): 'info' | 'warning' | 'danger' {
  if (value === 2) return 'danger'
  if (value === 1) return 'warning'
  return 'info'
}

function formatRelativeTime(value?: string): string {
  if (!value) return ''
  const time = new Date(value).getTime()
  if (Number.isNaN(time)) return formatDateTime(value)

  const diff = Date.now() - time
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return formatDateTime(value)
}

function handlePopoverShow() {
  announcementStore.fetchRecentAnnouncements()
}

async function handleMarkAllAnnouncementsRead() {
  try {
    await announcementStore.markAllAsRead()
    ElMessage.success('公告已全部标记为已读')
  } catch {
    // 错误由拦截器处理
  }
}

function goAnnouncementPage() {
  router.push('/system/announcement')
}

function handleAnnouncementClick(item: AnnouncementVO, event: MouseEvent) {
  event.stopPropagation()
  event.preventDefault()

  selectedAnnouncement.value = { ...item }
  pendingReadId.value = item.id != null && !item.read ? item.id : null
  popoverVisible.value = false

  nextTick(() => {
    detailVisible.value = true
  })
}

async function handleDetailClosed() {
  const readId = pendingReadId.value
  pendingReadId.value = null
  selectedAnnouncement.value = null

  if (readId != null) {
    try {
      await announcementStore.markAsRead(readId)
    } catch {
      // 错误由拦截器处理
    }
  }
}
</script>

<style scoped lang="scss">
.notification-trigger {
  border: none;
  background: transparent;
  color: #606266;
  transition: color 0.2s ease, background 0.2s ease;

  &:hover {
    color: var(--app-primary);
    background: rgba(9, 105, 218, 0.1);
  }

  &--on-primary {
    color: rgba(255, 255, 255, 0.9);

    &:hover {
      color: #fff;
      background: rgba(255, 255, 255, 0.15);
    }
  }
}

.notification-panel {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.notification-list {
  max-height: 360px;
  overflow-y: auto;
}

.notification-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f6f8fa;
  cursor: pointer;
  transition: background 0.2s ease;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: #f6f8fa;
  }

  &--unread {
    background: #f0f9ff;

    &:hover {
      background: #e1f3ff;
    }
  }

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
  }

  &__time {
    font-size: 12px;
    color: #909399;
  }

  &__title {
    font-size: 14px;
    font-weight: 500;
    color: #303133;
    margin-bottom: 4px;
  }

  &__message {
    font-size: 13px;
    color: #606266;
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.announcement-detail {
  &__title {
    margin: 0 0 12px;
    font-size: 18px;
    color: #303133;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 16px;
    margin-bottom: 16px;
    font-size: 13px;
    color: #909399;
  }

  &__content {
    white-space: pre-wrap;
    line-height: 1.7;
    color: #606266;
    font-size: 14px;
  }
}
</style>
