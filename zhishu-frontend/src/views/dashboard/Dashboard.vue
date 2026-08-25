<template>
  <div v-loading="loading" class="dashboard">
    <!-- 问候区 -->
    <section class="dash-hero">
      <div class="dash-hero__text">
        <p class="dash-hero__eyebrow">{{ todayLabel }}</p>
        <h1 class="dash-hero__title">
          {{ greetingText }}，{{ displayUserName }}
        </h1>
        <p class="dash-hero__desc">
          {{ welcomeSubtitle }}
        </p>
      </div>
      <div class="dash-hero__aside">
        <span class="dash-hero__version">{{ APP_VERSION }}</span>
        <el-button :icon="Refresh" :loading="loading" @click="loadDashboardData">
          刷新
        </el-button>
        <el-button
          v-if="canOpenMonitor"
          type="primary"
          :icon="Monitor"
          @click="goTo('/monitor/ops')"
        >
          运维监控
        </el-button>
      </div>
    </section>

    <!-- 组织概览 -->
    <section v-if="overviewCards.length" class="dash-stats">
      <button
        v-for="card in overviewCards"
        :key="card.key"
        type="button"
        class="dash-stat"
        @click="goTo(card.path)"
      >
        <span class="dash-stat__label">{{ card.label }}</span>
        <span class="dash-stat__value">{{ card.value }}</span>
        <span class="dash-stat__hint">{{ card.hint }}</span>
        <span class="dash-stat__accent" :class="`dash-stat__accent--${card.accent}`" />
      </button>
    </section>

    <!-- 运行健康 + 快捷入口 -->
    <el-row :gutter="12" class="dash-row">
      <el-col :xs="24" :lg="10">
        <section class="dash-panel dash-health">
          <header class="dash-panel__head">
            <div class="dash-panel__title-group">
              <h2 class="dash-panel__title">运行健康</h2>
              <el-tag
                v-if="systemStatus"
                :type="healthTagType(systemStatus.status)"
                size="small"
                effect="plain"
              >
                {{ healthLabel(systemStatus.status) }}
              </el-tag>
            </div>
            <el-button
              v-if="canOpenMonitor"
              link
              type="primary"
              @click="goTo('/monitor/ops')"
            >
              详情
            </el-button>
          </header>

          <div v-if="systemStatus" class="dash-health__body">
            <div class="dash-health__grid">
              <div class="dash-health__item">
                <span class="dash-health__label">HTTP 服务</span>
                <span class="dash-health__value">
                  <i
                    class="dash-dot"
                    :class="`dash-dot--${healthTone(systemStatus.webServer?.status)}`"
                  />
                  端口 {{ systemStatus.webServer?.port ?? '-' }}
                </span>
              </div>
              <div class="dash-health__item">
                <span class="dash-health__label">数据库</span>
                <span class="dash-health__value">
                  <i
                    class="dash-dot"
                    :class="`dash-dot--${healthTone(systemStatus.database?.status)}`"
                  />
                  {{ healthLabel(systemStatus.database?.status) }}
                </span>
              </div>
              <div class="dash-health__item">
                <span class="dash-health__label">运行时长</span>
                <span class="dash-health__value dash-health__value--strong">
                  {{ formatUptime(systemStatus.application?.uptimeMillis) }}
                </span>
              </div>
              <div class="dash-health__item">
                <span class="dash-health__label">最近检测</span>
                <span class="dash-health__value">
                  {{
                    systemStatus.timestamp
                      ? formatDateTime(systemStatus.timestamp)
                      : '-'
                  }}
                </span>
              </div>
            </div>

            <div class="dash-meters">
              <div class="dash-meter">
                <div class="dash-meter__head">
                  <span>JVM 堆内存</span>
                  <span>{{ formatHeap(systemStatus.jvm) }}</span>
                </div>
                <div class="dash-meter__track">
                  <div
                    class="dash-meter__fill"
                    :class="meterToneClass(heapUsagePercent)"
                    :style="{ width: `${heapUsagePercent ?? 0}%` }"
                  />
                </div>
              </div>
              <div
                v-if="dbConnectionPercent != null"
                class="dash-meter"
              >
                <div class="dash-meter__head">
                  <span>数据库连接</span>
                  <span>
                    {{ systemStatus.database?.activeConnections ?? 0 }}/{{
                      systemStatus.database?.maxConnections ?? '-'
                    }}
                  </span>
                </div>
                <div class="dash-meter__track">
                  <div
                    class="dash-meter__fill"
                    :class="meterToneClass(dbConnectionPercent)"
                    :style="{ width: `${dbConnectionPercent}%` }"
                  />
                </div>
              </div>
            </div>
          </div>

          <el-empty
            v-else
            description="暂无运行状态数据"
            :image-size="56"
          />
        </section>
      </el-col>

      <el-col :xs="24" :lg="14">
        <DashboardQuickActions class="dash-panel dash-panel--flush" />
      </el-col>
    </el-row>

    <!-- 公告 + 最近操作 -->
    <el-row :gutter="12" class="dash-row">
      <el-col :xs="24" :lg="12">
        <section class="dash-panel">
          <header class="dash-panel__head">
            <div class="dash-panel__title-group">
              <h2 class="dash-panel__title">系统公告</h2>
              <el-badge
                v-if="unreadAnnouncementCount > 0"
                :value="unreadAnnouncementCount"
                :max="99"
                class="dash-badge"
              />
            </div>
            <el-button link type="primary" @click="goTo('/system/announcement')">
              全部
            </el-button>
          </header>

          <div v-if="recentAnnouncements.length" class="dash-feed">
            <button
              v-for="item in recentAnnouncements"
              :key="item.id"
              type="button"
              class="dash-feed__item"
              :class="{ 'dash-feed__item--unread': item.read === false }"
              @click="goTo('/system/announcement')"
            >
              <div class="dash-feed__top">
                <el-tag
                  v-if="item.priority != null && item.priority > 0"
                  :type="item.priority === 2 ? 'danger' : 'warning'"
                  size="small"
                  effect="plain"
                >
                  {{ ANNOUNCEMENT_PRIORITY_LABEL[item.priority] || '重要' }}
                </el-tag>
                <span class="dash-feed__title">{{ item.title || '无标题' }}</span>
              </div>
              <p class="dash-feed__summary">{{ item.content || '-' }}</p>
              <div class="dash-feed__meta">
                <span>{{ item.publisherName || '系统' }}</span>
                <span>{{ formatRelativeTime(item.publishTime) }}</span>
              </div>
            </button>
          </div>
          <el-empty v-else description="暂无公告" :image-size="56" />
        </section>
      </el-col>

      <el-col :xs="24" :lg="12">
        <section class="dash-panel">
          <header class="dash-panel__head">
            <h2 class="dash-panel__title">最近操作</h2>
            <el-button
              v-if="canQueryLogs"
              link
              type="primary"
              @click="goTo('/system/operation-log')"
            >
              日志
            </el-button>
          </header>

          <div v-if="recentLogs.length" class="dash-feed">
            <div
              v-for="item in recentLogs"
              :key="item.id"
              class="dash-feed__item dash-feed__item--static"
            >
              <div class="dash-feed__top">
                <span class="dash-feed__module">{{ item.module || '系统' }}</span>
                <el-tag
                  :type="item.status === 0 ? 'danger' : 'success'"
                  size="small"
                  effect="plain"
                >
                  {{ item.status === 0 ? '失败' : '成功' }}
                </el-tag>
              </div>
              <p class="dash-feed__summary dash-feed__summary--single">
                {{ item.operation || item.method || '-' }}
              </p>
              <div class="dash-feed__meta">
                <span>{{ item.realName || item.username || '系统' }}</span>
                <span>{{ formatRelativeTime(item.createTime) }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无操作记录" :image-size="56" />
        </section>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Monitor, Refresh } from '@element-plus/icons-vue'
import DashboardQuickActions from '@/components/dashboard/DashboardQuickActions.vue'
import { getRecentAnnouncementsApi } from '@/api/announcement'
import { getMenuTreeApi } from '@/api/menu'
import { getOperationLogPageApi } from '@/api/operationLog'
import { getRolePageApi } from '@/api/role'
import { getSystemStatusApi } from '@/api/systemMonitor'
import { getUnitListApi } from '@/api/unit'
import { getUserPageApi } from '@/api/user'
import { APP_VERSION } from '@/constants/app'
import { PERMISSIONS } from '@/constants/permissions'
import type { AnnouncementVO } from '@/types/announcement'
import { ANNOUNCEMENT_PRIORITY_LABEL } from '@/types/announcement'
import type { MenuVO } from '@/types/menu'
import type { OperationLogVO } from '@/types/operationLog'
import type { HealthStatus, JvmMetricsDTO, SystemStatusDTO } from '@/types/systemMonitor'
import type { UnitVO } from '@/types/unit'
import { formatDateTime } from '@/utils/format'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'
import { useUserStore } from '@/stores/useUserStore'
import { useMenuStore } from '@/stores/useMenuStore'
import { normalizeMenuPath } from '@/utils/menuNavigation'

type OverviewAccent = 'primary' | 'success' | 'warning' | 'info'

interface OverviewCard {
  key: string
  label: string
  value: string | number
  hint: string
  path: string
  accent: OverviewAccent
}

const router = useRouter()
const systemConfigStore = useSystemConfigStore()
const userStore = useUserStore()
const menuStore = useMenuStore()

const displayUserName = computed(() => userStore.userName || '管理员')

const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayLabel = computed(() => {
  const now = new Date()
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d} · ${weekdays[now.getDay()]}`
})

const welcomeSubtitle = computed(() => {
  const name = systemConfigStore.systemName || '知枢可集成框架'
  return `${name} 工作台 · 智能协作、知识检索与平台治理一站总览`
})

const loading = ref(false)
const systemStatus = ref<SystemStatusDTO | null>(null)
const recentAnnouncements = ref<AnnouncementVO[]>([])
const recentLogs = ref<OperationLogVO[]>([])
const userTotal = ref<number | null>(null)
const roleTotal = ref<number | null>(null)
const unitTotal = ref<number | null>(null)
const menuTotal = ref<number | null>(null)

const canQueryLogs = computed(() => userStore.hasPermission(PERMISSIONS.SYSTEM_OPERLOG_QUERY))

const canOpenMonitor = computed(() => {
  if (userStore.hasPermission(PERMISSIONS.SYSTEM_MONITOR_QUERY)) return true
  const paths = new Set<string>()
  const walk = (items: typeof menuStore.sidebarMenus) => {
    for (const item of items) {
      if (item.path) paths.add(normalizeMenuPath(item.path))
      if (item.children?.length) walk(item.children)
    }
  }
  walk(menuStore.sidebarMenus)
  return paths.has(normalizeMenuPath('/monitor/ops'))
})

const overviewCards = computed<OverviewCard[]>(() => {
  const cards: OverviewCard[] = []
  if (userTotal.value != null) {
    cards.push({
      key: 'users',
      label: '用户',
      value: userTotal.value,
      hint: '账号与登录主体',
      path: '/permission/user',
      accent: 'primary',
    })
  }
  if (roleTotal.value != null) {
    cards.push({
      key: 'roles',
      label: '角色',
      value: roleTotal.value,
      hint: '权限边界划分',
      path: '/permission/role',
      accent: 'success',
    })
  }
  if (unitTotal.value != null) {
    cards.push({
      key: 'units',
      label: '单位',
      value: unitTotal.value,
      hint: '组织机构节点',
      path: '/permission/unit',
      accent: 'warning',
    })
  }
  if (menuTotal.value != null) {
    cards.push({
      key: 'menus',
      label: '菜单',
      value: menuTotal.value,
      hint: '导航与按钮权限',
      path: '/permission/menu',
      accent: 'info',
    })
  }
  return cards
})

const unreadAnnouncementCount = computed(
  () => recentAnnouncements.value.filter((item) => item.read === false).length,
)

const heapUsagePercent = computed(() => {
  const jvm = systemStatus.value?.jvm
  if (!jvm) return null
  if (typeof jvm.heapUsagePercent === 'number') {
    return Math.min(100, Math.max(0, Math.round(jvm.heapUsagePercent)))
  }
  if (jvm.heapUsedMb != null && jvm.heapMaxMb != null && jvm.heapMaxMb > 0) {
    return Math.min(100, Math.max(0, Math.round((jvm.heapUsedMb / jvm.heapMaxMb) * 100)))
  }
  return null
})

const dbConnectionPercent = computed(() => {
  const db = systemStatus.value?.database
  if (db?.activeConnections == null || db?.maxConnections == null || db.maxConnections <= 0) {
    return null
  }
  return Math.min(100, Math.max(0, Math.round((db.activeConnections / db.maxConnections) * 100)))
})

const healthTagType = (status?: HealthStatus) => {
  const normalized = status?.toUpperCase()
  if (normalized === 'UP') return 'success'
  if (normalized === 'DEGRADED') return 'warning'
  if (normalized === 'DOWN') return 'danger'
  return 'info'
}

const healthLabel = (status?: HealthStatus) => {
  const normalized = status?.toUpperCase()
  if (normalized === 'UP') return '正常'
  if (normalized === 'DEGRADED') return '降级'
  if (normalized === 'DOWN') return '异常'
  return '未知'
}

const healthTone = (status?: HealthStatus) => {
  const normalized = status?.toUpperCase()
  if (normalized === 'UP') return 'ok'
  if (normalized === 'DEGRADED') return 'warn'
  if (normalized === 'DOWN') return 'bad'
  return 'muted'
}

const meterToneClass = (percent: number | null) => {
  if (percent == null) return 'dash-meter__fill--muted'
  if (percent >= 85) return 'dash-meter__fill--danger'
  if (percent >= 70) return 'dash-meter__fill--warn'
  return 'dash-meter__fill--ok'
}

const formatHeap = (jvm?: JvmMetricsDTO) => {
  if (!jvm) return '-'
  if (jvm.heapUsedMb != null && jvm.heapMaxMb != null) {
    return `${jvm.heapUsedMb} / ${jvm.heapMaxMb} MB`
  }
  if (jvm.heapUsagePercent != null) {
    return `${jvm.heapUsagePercent}%`
  }
  return '-'
}

const formatUptime = (uptimeMillis?: number) => {
  if (uptimeMillis == null || uptimeMillis < 0) return '-'
  const totalSeconds = Math.floor(uptimeMillis / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 小时`
  if (hours > 0) return `${hours} 小时 ${minutes} 分`
  return `${minutes} 分钟`
}

const formatRelativeTime = (value?: string | null) => {
  if (!value) return '-'
  const ts = new Date(value).getTime()
  if (Number.isNaN(ts)) return formatDateTime(value)
  const diff = Date.now() - ts
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (diff < 86_400_000 * 7) return `${Math.floor(diff / 86_400_000)} 天前`
  return formatDateTime(value)
}

const countMenuNodes = (menus: MenuVO[]): number => {
  let count = 0
  const walk = (items: MenuVO[]) => {
    for (const item of items) {
      count += 1
      if (item.children?.length) walk(item.children)
    }
  }
  walk(menus)
  return count
}

const countUnitNodes = (units: UnitVO[]): number => {
  let count = 0
  const walk = (items: UnitVO[]) => {
    for (const item of items) {
      count += 1
      if (item.children?.length) walk(item.children)
    }
  }
  walk(units)
  return count
}

const safeFetch = async <T>(task: () => Promise<T>): Promise<T | null> => {
  try {
    return await task()
  } catch {
    return null
  }
}

const goTo = (path: string) => {
  router.push(path)
}

const loadDashboardData = async () => {
  loading.value = true
  try {
    const canQueryUsers = userStore.hasPermission(PERMISSIONS.SYSTEM_USER_QUERY)
    const canQueryRoles = userStore.hasPermission(PERMISSIONS.SYSTEM_ROLE_QUERY)
    const canQueryUnits = userStore.hasPermission(PERMISSIONS.SYSTEM_UNIT_QUERY)
    const canQueryMenus = userStore.hasPermission(PERMISSIONS.SYSTEM_MENU_QUERY)
    const canQueryOperLogs = userStore.hasPermission(PERMISSIONS.SYSTEM_OPERLOG_QUERY)
    const canQueryMonitor = userStore.hasPermission(PERMISSIONS.SYSTEM_MONITOR_QUERY)

    const [
      statusRes,
      announcementRes,
      userPageRes,
      rolePageRes,
      unitListRes,
      menuTreeRes,
      logPageRes,
    ] = await Promise.all([
      canQueryMonitor ? safeFetch(() => getSystemStatusApi()) : Promise.resolve(null),
      safeFetch(() => getRecentAnnouncementsApi(5)),
      canQueryUsers
        ? safeFetch(() => getUserPageApi({ pageNum: 1, pageSize: 1 }))
        : Promise.resolve(null),
      canQueryRoles
        ? safeFetch(() => getRolePageApi({ pageNum: 1, pageSize: 1 }))
        : Promise.resolve(null),
      canQueryUnits ? safeFetch(() => getUnitListApi()) : Promise.resolve(null),
      canQueryMenus ? safeFetch(() => getMenuTreeApi()) : Promise.resolve(null),
      canQueryOperLogs
        ? safeFetch(() => getOperationLogPageApi({ pageNum: 1, pageSize: 6 }))
        : Promise.resolve(null),
    ])

    systemStatus.value = statusRes
    recentAnnouncements.value = announcementRes ?? []
    userTotal.value = userPageRes?.total ?? (canQueryUsers ? 0 : null)
    roleTotal.value = rolePageRes?.total ?? (canQueryRoles ? 0 : null)
    unitTotal.value = unitListRes ? countUnitNodes(unitListRes) : canQueryUnits ? 0 : null
    menuTotal.value = menuTreeRes ? countMenuNodes(menuTreeRes) : canQueryMenus ? 0 : null
    recentLogs.value = logPageRes?.records ?? []

    if (!systemStatus.value && !canQueryMonitor) {
      systemStatus.value = await safeFetch(() => getSystemStatusApi())
    }
  } finally {
    loading.value = false
  }
}

loadDashboardData()
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.dashboard {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;

  :deep(.el-row) {
    margin-bottom: 0 !important;
  }
}

.dash-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 20px;
  border-radius: var(--app-radius-lg, 12px);
  background:
    radial-gradient(120% 140% at 0% 0%, color-mix(in srgb, var(--app-primary) 14%, transparent), transparent 55%),
    linear-gradient(180deg, #ffffff 0%, #f6f8fa 100%);
  border: 1px solid color-mix(in srgb, var(--app-primary) 10%, var(--app-border-color));

  &__eyebrow {
    margin: 0 0 6px;
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: 12px;
    font-weight: 500;
    letter-spacing: 0.04em;
    color: $text-secondary;
  }

  &__title {
    margin: 0 0 6px;
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: 22px;
    font-weight: 650;
    line-height: 1.25;
    color: $text-primary;
  }

  &__desc {
    margin: 0;
    max-width: 560px;
    font-size: 13px;
    line-height: 1.55;
    color: $text-secondary;
  }

  &__aside {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }

  &__version {
    margin-right: 4px;
    padding: 4px 8px;
    border-radius: 6px;
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.02em;
    color: var(--app-primary);
    background: color-mix(in srgb, var(--app-primary) 10%, transparent);
  }
}

.dash-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.dash-stat {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 14px 16px 16px;
  border: 1px solid var(--app-border-color);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;

  &:hover {
    border-color: color-mix(in srgb, var(--app-primary) 30%, var(--app-border-color));
    box-shadow: var(--app-shadow-sm);
    transform: translateY(-1px);
  }

  &__label {
    font-size: 12px;
    color: $text-secondary;
  }

  &__value {
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: 26px;
    font-weight: 700;
    line-height: 1.15;
    color: $text-primary;
  }

  &__hint {
    font-size: 12px;
    color: $text-secondary;
  }

  &__accent {
    position: absolute;
    left: 0;
    top: 14px;
    bottom: 14px;
    width: 3px;
    border-radius: 0 2px 2px 0;

    &--primary {
      background: var(--app-primary);
    }

    &--success {
      background: #67c23a;
    }

    &--warning {
      background: #e6a23c;
    }

    &--info {
      background: #909399;
    }
  }
}

.dash-row {
  width: 100%;
}

.dash-panel {
  height: 100%;
  margin-bottom: 12px;
  padding: 14px 16px 16px;
  border: 1px solid var(--app-border-color);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);

  &--flush {
    padding: 0;
    border: none;
    background: transparent;
    margin-bottom: 12px;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
    min-height: 24px;
  }

  &__title-group {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__title {
    margin: 0;
    font-size: 14px;
    font-weight: 650;
    color: $text-primary;
  }
}

.dash-badge {
  :deep(.el-badge__content) {
    transform: translateY(0);
  }
}

.dash-health__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
  margin-bottom: 14px;
}

.dash-health__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.dash-health__label {
  font-size: 12px;
  color: $text-secondary;
}

.dash-health__value {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: $text-regular;
  line-height: 1.4;

  &--strong {
    font-weight: 600;
    color: $text-primary;
  }
}

.dash-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;

  &--ok {
    background: #67c23a;
  }

  &--warn {
    background: #e6a23c;
  }

  &--bad {
    background: #f56c6c;
  }

  &--muted {
    background: #c0c4cc;
  }
}

.dash-meters {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dash-meter {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 6px;
    font-size: 12px;
    color: $text-secondary;
  }

  &__track {
    height: 6px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--app-border-color) 80%, #eef2f6);
    overflow: hidden;
  }

  &__fill {
    height: 100%;
    border-radius: inherit;
    transition: width 0.35s ease;

    &--ok {
      background: linear-gradient(90deg, var(--app-primary), color-mix(in srgb, var(--app-primary) 65%, #67c23a));
    }

    &--warn {
      background: #e6a23c;
    }

    &--danger {
      background: #f56c6c;
    }

    &--muted {
      background: #c0c4cc;
      width: 0 !important;
    }
  }
}

.dash-feed {
  display: flex;
  flex-direction: column;
}

.dash-feed__item {
  display: block;
  width: 100%;
  padding: 12px 0;
  border: none;
  border-bottom: 1px solid color-mix(in srgb, var(--app-border-color) 85%, transparent);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:first-child {
    padding-top: 2px;
  }

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }

  &:hover:not(.dash-feed__item--static) {
    .dash-feed__title {
      color: var(--app-primary);
    }
  }

  &--unread {
    .dash-feed__title {
      font-weight: 700;
    }
  }

  &--static {
    cursor: default;
  }
}

.dash-feed__top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  min-width: 0;
}

.dash-feed__title {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s ease;
}

.dash-feed__module {
  font-size: 12px;
  font-weight: 650;
  color: var(--app-primary);
}

.dash-feed__summary {
  margin: 0 0 6px;
  font-size: 12px;
  line-height: 1.5;
  color: $text-regular;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;

  &--single {
    -webkit-line-clamp: 1;
    white-space: nowrap;
    display: block;
    text-overflow: ellipsis;
  }
}

.dash-feed__meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: $text-secondary;
}

@media (max-width: 1100px) {
  .dash-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .dash-hero {
    padding: 14px 14px;

    &__title {
      font-size: 19px;
    }

    &__aside {
      width: 100%;
      justify-content: flex-end;
      flex-wrap: wrap;
    }
  }

  .dash-stats {
    grid-template-columns: 1fr 1fr;
  }

  .dash-health__grid {
    grid-template-columns: 1fr;
  }

  .dash-panel {
    margin-bottom: 10px;
  }
}
</style>
