<template>
  <div class="briefing-center" v-loading="pageLoading">
    <header class="briefing-masthead" v-loading="overviewLoading">
      <div class="briefing-masthead__decor" aria-hidden="true">
        <span class="briefing-masthead__glow briefing-masthead__glow--a" />
        <span class="briefing-masthead__glow briefing-masthead__glow--b" />
        <span class="briefing-masthead__ring briefing-masthead__ring--lg" />
        <span class="briefing-masthead__ring briefing-masthead__ring--sm" />
        <span class="briefing-masthead__grid" />
        <span class="briefing-masthead__bar" />
      </div>
      <div class="briefing-masthead__text">
        <p class="briefing-masthead__eyebrow">工作台</p>
        <h2 class="briefing-masthead__title">AI 简报</h2>
        <p class="briefing-masthead__desc">
          汇总监测态势、告警与巡检要点 · 支持站内通知与邮件投递
        </p>
      </div>
      <div class="briefing-masthead__aside">
        <div class="briefing-masthead__actions">
          <el-button :icon="Refresh" :loading="refreshing" @click="handleRefresh">刷新</el-button>
        </div>
        <div class="briefing-kpi">
          <div class="briefing-kpi__item">
            <span class="briefing-kpi__value">{{ overviewStats?.total ?? 0 }}</span>
            <span class="briefing-kpi__label">累计</span>
          </div>
          <div class="briefing-kpi__item briefing-kpi__item--success">
            <span class="briefing-kpi__value">{{ overviewStats?.success ?? 0 }}</span>
            <span class="briefing-kpi__label">成功</span>
          </div>
          <div class="briefing-kpi__item briefing-kpi__item--warning">
            <span class="briefing-kpi__value">{{ overviewStats?.unread ?? 0 }}</span>
            <span class="briefing-kpi__label">未读</span>
          </div>
          <div class="briefing-kpi__item briefing-kpi__item--danger">
            <span class="briefing-kpi__value">{{ overviewStats?.failed ?? 0 }}</span>
            <span class="briefing-kpi__label">失败</span>
          </div>
          <div class="briefing-kpi__item briefing-kpi__item--info">
            <span class="briefing-kpi__value">{{ overviewStats?.pendingOrRunning ?? 0 }}</span>
            <span class="briefing-kpi__label">进行中</span>
          </div>
        </div>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="briefing-center__tabs" @tab-change="handleTabChange">
      <el-tab-pane label="最新简报" name="latest" />
      <el-tab-pane label="历史简报" name="history" />
      <el-tab-pane label="发送情况" name="stats" />
      <el-tab-pane v-if="userStore.isAdmin" label="计划配置" name="schedule" />
    </el-tabs>

    <!-- 最新简报 -->
    <div v-show="activeTab === 'latest'" class="briefing-layout">
      <section class="briefing-main">
        <el-empty v-if="!latestBriefing" description="暂无成功简报" :image-size="110" />
        <article v-else class="report-sheet">
          <div class="report-sheet__top">
            <div class="report-sheet__badge-row">
              <span class="report-sheet__series">本期简报</span>
              <el-tag :type="statusTagType(latestBriefing.status)" size="small" effect="plain">
                {{ statusLabel(latestBriefing.status) }}
              </el-tag>
              <el-tag v-if="!latestBriefing.readAt" type="warning" size="small">未读</el-tag>
              <el-tag v-else type="success" size="small" effect="plain">已读</el-tag>
              <el-tag v-if="latestBriefing.triggerType" size="small" type="info" effect="plain">
                {{ triggerLabel(latestBriefing.triggerType) }}
              </el-tag>
            </div>
            <el-button
              v-if="!latestBriefing.readAt"
              type="primary"
              size="small"
              :loading="markingRead"
              @click="handleMarkLatestRead"
            >
              标记已读
            </el-button>
          </div>

          <h2 class="report-sheet__title">{{ latestBriefing.title || '监测简报' }}</h2>
          <div class="report-sheet__byline">
            <span>{{ formatDateTime(latestBriefing.finishedAt || latestBriefing.createTime) || '—' }}</span>
            <span class="report-sheet__dot">·</span>
            <span>邮件：{{ emailStatusLabel(latestBriefing.emailStatus) }}</span>
          </div>
          <div class="report-sheet__rule" />

          <div
            class="markdown-body report-sheet__body"
            v-html="renderMarkdown(latestBriefing.contentMd || '_暂无正文_')"
          />
        </article>
      </section>

      <aside class="briefing-side">
        <div class="side-card">
          <div class="side-card__head">
            <h3 class="side-card__title">近期简报</h3>
            <el-button type="primary" link size="small" @click="activeTab = 'history'; loadHistory()">
              全部
            </el-button>
          </div>
          <el-empty
            v-if="!sideRecent.length"
            description="暂无记录"
            :image-size="64"
          />
          <ul v-else class="side-list">
            <li
              v-for="item in sideRecent"
              :key="String(item.id)"
              class="side-list__item"
              :class="{ 'side-list__item--active': item.id === latestBriefing?.id }"
              @click="openDetailDrawer(item)"
            >
              <div class="side-list__row">
                <span
                  class="side-list__title"
                  :class="{ 'is-unread': !item.readAt && item.status === 'SUCCESS' }"
                >
                  {{ item.title || '监测简报' }}
                </span>
                <el-tag
                  :type="statusTagType(item.status)"
                  size="small"
                  effect="plain"
                >
                  {{ statusLabel(item.status) }}
                </el-tag>
              </div>
              <p class="side-list__preview">{{ contentPreview(item.contentMd) }}</p>
              <div class="side-list__meta">
                {{ formatDateTime(item.finishedAt || item.createTime) || '—' }}
              </div>
            </li>
          </ul>
        </div>

        <div class="side-card side-card--hint">
          <h3 class="side-card__title">阅读提示</h3>
          <ul class="hint-list">
            <li>顶部铃铛可即时查看新简报弹窗</li>
            <li>历史页支持翻页检索往期内容</li>
            <li>管理员可在「计划配置」调整生成时间与通知方式</li>
          </ul>
        </div>
      </aside>
    </div>

    <!-- 历史简报 -->
    <div v-show="activeTab === 'history'" class="briefing-panel">
      <div class="panel-toolbar">
        <div>
          <h3 class="panel-toolbar__title">历史简报</h3>
          <p class="panel-toolbar__desc">点击条目查看完整内容</p>
        </div>
      </div>

      <div v-loading="historyLoading" class="history-grid">
        <el-empty
          v-if="!historyLoading && historyList.length === 0"
          description="暂无历史简报"
          :image-size="96"
        />
        <button
          v-for="row in historyList"
          :key="String(row.id)"
          type="button"
          class="history-card"
          :class="{ 'history-card--unread': !row.readAt && row.status === 'SUCCESS' }"
          @click="openDetailDrawer(row)"
        >
          <div class="history-card__top">
            <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
            <el-tag v-if="!row.readAt && row.status === 'SUCCESS'" type="warning" size="small">
              未读
            </el-tag>
            <span class="history-card__time">
              {{ formatDateTime(row.finishedAt || row.createTime) || '—' }}
            </span>
          </div>
          <h4 class="history-card__title">{{ row.title || '监测简报' }}</h4>
          <p class="history-card__preview">{{ contentPreview(row.contentMd, 120) }}</p>
          <div class="history-card__foot">
            <span>{{ triggerLabel(row.triggerType) }}</span>
            <span>邮件 {{ emailStatusLabel(row.emailStatus) }}</span>
          </div>
        </button>
      </div>

      <div class="briefing-pagination">
        <el-pagination
          v-model:current-page="historyPage.pageNum"
          v-model:page-size="historyPage.pageSize"
          :total="historyPage.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>
    </div>

    <!-- 发送情况 -->
    <div v-show="activeTab === 'stats'" class="briefing-panel">
      <div class="panel-toolbar">
        <div>
          <h3 class="panel-toolbar__title">发送情况</h3>
          <p class="panel-toolbar__desc">投递成功率与最近记录一览</p>
        </div>
      </div>

      <div class="stats-cards">
        <div class="stats-card">
          <div class="stats-card__value">{{ stats?.total ?? 0 }}</div>
          <div class="stats-card__label">全部</div>
        </div>
        <div class="stats-card stats-card--success">
          <div class="stats-card__value">{{ stats?.success ?? 0 }}</div>
          <div class="stats-card__label">成功</div>
        </div>
        <div class="stats-card stats-card--danger">
          <div class="stats-card__value">{{ stats?.failed ?? 0 }}</div>
          <div class="stats-card__label">失败</div>
        </div>
        <div class="stats-card stats-card--warning">
          <div class="stats-card__value">{{ stats?.unread ?? 0 }}</div>
          <div class="stats-card__label">未读</div>
        </div>
        <div class="stats-card stats-card--info">
          <div class="stats-card__value">{{ stats?.pendingOrRunning ?? 0 }}</div>
          <div class="stats-card__label">进行中</div>
        </div>
      </div>

      <div class="section-block">
        <h4 class="section-block__title">最近投递</h4>
        <el-table
          v-loading="recentLoading"
          :data="recentList"
          class="modern-table"
          empty-text="暂无投递记录"
          @row-click="openDetailDrawer"
        >
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="邮件" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="emailStatusTagType(row.emailStatus)" size="small" effect="plain">
                {{ emailStatusLabel(row.emailStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="触发" width="100" align="center">
            <template #default="{ row }">
              {{ triggerLabel(row.triggerType) }}
            </template>
          </el-table-column>
          <el-table-column label="时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.createTime) || '—' }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 计划配置（管理员） -->
    <div v-if="userStore.isAdmin" v-show="activeTab === 'schedule'" class="briefing-panel">
      <div class="panel-toolbar">
        <div>
          <h3 class="panel-toolbar__title">计划配置</h3>
          <p class="panel-toolbar__desc">配置定时生成、站内通知与邮件投递</p>
        </div>
        <div class="panel-toolbar__actions">
          <el-button type="primary" :icon="Plus" @click="openScheduleDialog()">新建计划</el-button>
          <el-button :icon="Refresh" :loading="scheduleLoading" @click="loadSchedules">刷新</el-button>
        </div>
      </div>
      <el-table
        v-loading="scheduleLoading"
        :data="schedules"
        class="modern-table"
        empty-text="暂无调度计划"
      >
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            {{ scheduleTypeLabel(row.scheduleType) }}
          </template>
        </el-table-column>
        <el-table-column label="时间" width="100" align="center">
          <template #default="{ row }">
            {{ row.scheduleTime || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="周几" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.scheduleDays || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="铃铛" width="80" align="center">
          <template #default="{ row }">
            {{ row.notifyBell ? '开' : '关' }}
          </template>
        </el-table-column>
        <el-table-column label="邮件" width="80" align="center">
          <template #default="{ row }">
            {{ row.notifyEmail ? '开' : '关' }}
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small" effect="plain">
              {{ row.enabled ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下次执行" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.nextRunAt) || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openScheduleDialog(row)">
              编辑
            </el-button>
            <el-button
              type="warning"
              link
              size="small"
              :loading="runningId === row.id"
              @click="handleRunNow(row)"
            >
              立即生成
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 详情抽屉：报版样式 -->
    <el-drawer
      v-model="drawerVisible"
      size="820px"
      destroy-on-close
      class="briefing-drawer"
      @closed="handleDrawerClosed"
    >
      <template #header>
        <div class="drawer-report__eyebrow">AI 监测简报</div>
      </template>
      <template v-if="drawerBriefing">
        <div class="drawer-report">
          <div class="drawer-report__meta">
            <el-tag :type="statusTagType(drawerBriefing.status)" size="small" effect="plain">
              {{ statusLabel(drawerBriefing.status) }}
            </el-tag>
            <el-tag
              :type="emailStatusTagType(drawerBriefing.emailStatus)"
              size="small"
              effect="plain"
            >
              邮件：{{ emailStatusLabel(drawerBriefing.emailStatus) }}
            </el-tag>
            <el-tag v-if="drawerBriefing.triggerType" size="small" type="info" effect="plain">
              {{ triggerLabel(drawerBriefing.triggerType) }}
            </el-tag>
            <span class="drawer-report__time">
              {{ formatDateTime(drawerBriefing.finishedAt || drawerBriefing.createTime) }}
            </span>
            <el-button
              v-if="!drawerBriefing.readAt && drawerBriefing.status === 'SUCCESS'"
              type="primary"
              size="small"
              :loading="markingRead"
              @click="handleMarkDrawerRead"
            >
              标记已读
            </el-button>
          </div>
          <h2 class="drawer-report__title">{{ drawerBriefing.title || '简报详情' }}</h2>
          <div class="drawer-report__rule" />
          <el-alert
            v-if="drawerBriefing.errorMessage"
            type="error"
            :title="drawerBriefing.errorMessage"
            :closable="false"
            show-icon
            class="drawer-error"
          />
          <div
            class="markdown-body drawer-report__body"
            v-html="renderMarkdown(drawerBriefing.contentMd || '_暂无正文_')"
          />
        </div>
      </template>
    </el-drawer>

    <!-- 计划编辑对话框 -->
    <el-dialog
      v-model="scheduleDialogVisible"
      :title="editingScheduleId ? '编辑计划' : '新建计划'"
      width="560px"
      destroy-on-close
      @closed="resetScheduleForm"
    >
      <el-form ref="scheduleFormRef" :model="scheduleForm" :rules="scheduleRules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="scheduleForm.name" maxlength="128" show-word-limit placeholder="计划名称" />
        </el-form-item>
        <el-form-item label="智能体">
          <el-select
            v-model="scheduleForm.agentId"
            clearable
            filterable
            placeholder="可选，默认智能体"
            style="width: 100%"
          >
            <el-option
              v-for="agent in agentOptions"
              :key="agent.id"
              :label="agent.name"
              :value="agent.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调度类型" prop="scheduleType">
          <el-select v-model="scheduleForm.scheduleType" style="width: 100%">
            <el-option label="每日" value="DAILY" />
            <el-option label="每周" value="WEEKLY" />
            <el-option label="Cron" value="CRON" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="scheduleForm.scheduleType !== 'CRON'"
          label="执行时间"
          prop="scheduleTime"
        >
          <el-time-select
            v-model="scheduleForm.scheduleTime"
            start="00:00"
            step="00:30"
            end="23:30"
            placeholder="选择时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="scheduleForm.scheduleType === 'WEEKLY'" label="周几" prop="scheduleDays">
          <el-select
            v-model="scheduleDaysSelected"
            multiple
            placeholder="选择星期"
            style="width: 100%"
          >
            <el-option v-for="d in weekDayOptions" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="scheduleForm.scheduleType === 'CRON'" label="Cron" prop="cronExpr">
          <el-input v-model="scheduleForm.cronExpr" placeholder="Spring 6 段 cron，如 0 0 8 * * ?" />
        </el-form-item>
        <el-form-item label="提示词模板">
          <el-input
            v-model="scheduleForm.promptTemplate"
            type="textarea"
            :rows="3"
            placeholder="可选，覆盖默认提示词"
          />
        </el-form-item>
        <el-form-item label="站内通知">
          <el-switch v-model="scheduleForm.notifyBell" />
        </el-form-item>
        <el-form-item label="邮件通知">
          <el-switch v-model="scheduleForm.notifyEmail" />
        </el-form-item>
        <el-form-item v-if="scheduleForm.notifyEmail" label="收件模式">
          <el-select v-model="scheduleForm.emailToMode" style="width: 100%">
            <el-option label="用户资料邮箱" value="USER_PROFILE" />
            <el-option label="额外邮箱" value="EXTRA" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="scheduleForm.notifyEmail && scheduleForm.emailToMode === 'EXTRA'"
          label="额外邮箱"
        >
          <el-input v-model="scheduleForm.emailExtraTo" placeholder="多个邮箱用逗号分隔" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="scheduleForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="scheduleSaving" @click="submitSchedule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createBriefingSchedule,
  getBriefingDetail,
  getBriefingLatest,
  getBriefingRecent,
  getBriefingStats,
  getBriefingsPage,
  listAgents,
  listBriefingSchedules,
  runBriefingScheduleNow,
  updateBriefingSchedule,
} from '@/api/ai'
import { useBriefingStore } from '@/stores/useBriefingStore'
import { useUserStore } from '@/stores/useUserStore'
import type { AgentVO } from '@/types/aiAgent'
import type {
  BriefingDeliveryVO,
  BriefingScheduleUpsertDTO,
  BriefingScheduleVO,
  BriefingStatsVO,
} from '@/types/briefing'
import {
  BRIEFING_EMAIL_STATUS_LABEL,
  BRIEFING_SCHEDULE_TYPE_LABEL,
  BRIEFING_STATUS_LABEL,
} from '@/types/briefing'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const briefingStore = useBriefingStore()

const md = new MarkdownIt({ html: false, linkify: true, breaks: true, typographer: true })
const renderMarkdown = (content: string) => {
  try {
    return md.render(content || '')
  } catch {
    return content || ''
  }
}

const activeTab = ref('latest')
const pageLoading = ref(false)
const refreshing = ref(false)
const markingRead = ref(false)
const overviewLoading = ref(false)

const latestBriefing = ref<BriefingDeliveryVO | null>(null)
const overviewStats = ref<BriefingStatsVO | null>(null)
const sideRecent = ref<BriefingDeliveryVO[]>([])

const historyLoading = ref(false)
const historyList = ref<BriefingDeliveryVO[]>([])
const historyPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const stats = ref<BriefingStatsVO | null>(null)
const recentLoading = ref(false)
const recentList = ref<BriefingDeliveryVO[]>([])

const scheduleLoading = ref(false)
const schedules = ref<BriefingScheduleVO[]>([])
const runningId = ref<number | null>(null)
const agentOptions = ref<AgentVO[]>([])

const drawerVisible = ref(false)
const drawerBriefing = ref<BriefingDeliveryVO | null>(null)

const scheduleDialogVisible = ref(false)
const scheduleSaving = ref(false)
const editingScheduleId = ref<number | null>(null)
const scheduleFormRef = ref<FormInstance>()
const scheduleForm = reactive<BriefingScheduleUpsertDTO>({
  name: '',
  agentId: null,
  promptTemplate: '',
  scopeType: 'USER_PROJECTS',
  scheduleType: 'DAILY',
  scheduleTime: '08:00',
  scheduleDays: '',
  cronExpr: '',
  timezone: 'Asia/Shanghai',
  notifyBell: true,
  notifyEmail: false,
  emailToMode: 'USER_PROFILE',
  emailExtraTo: '',
  emailSubjectTemplate: '',
  enabled: true,
})

const weekDayOptions = [
  { value: '1', label: '周一' },
  { value: '2', label: '周二' },
  { value: '3', label: '周三' },
  { value: '4', label: '周四' },
  { value: '5', label: '周五' },
  { value: '6', label: '周六' },
  { value: '7', label: '周日' },
]

const scheduleDaysSelected = computed({
  get: () =>
    (scheduleForm.scheduleDays || '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
  set: (vals: string[]) => {
    scheduleForm.scheduleDays = vals.join(',')
  },
})

const scheduleRules: FormRules = {
  name: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  scheduleType: [{ required: true, message: '请选择调度类型', trigger: 'change' }],
  scheduleTime: [
    {
      validator: (_rule, value, callback) => {
        if (scheduleForm.scheduleType !== 'CRON' && !value) {
          callback(new Error('请选择执行时间'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  scheduleDays: [
    {
      validator: (_rule, _value, callback) => {
        if (scheduleForm.scheduleType === 'WEEKLY' && !scheduleForm.scheduleDays) {
          callback(new Error('请选择周几'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  cronExpr: [
    {
      validator: (_rule, value, callback) => {
        if (scheduleForm.scheduleType === 'CRON' && !value) {
          callback(new Error('请输入 Cron 表达式'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

function statusLabel(status?: string | null) {
  return BRIEFING_STATUS_LABEL[status || ''] || status || '—'
}

function statusTagType(status?: string | null): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING' || status === 'PENDING') return 'warning'
  return 'info'
}

function emailStatusLabel(status?: string | null) {
  return BRIEFING_EMAIL_STATUS_LABEL[status || ''] || status || '—'
}

function emailStatusTagType(status?: string | null): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

function scheduleTypeLabel(type?: string | null) {
  return BRIEFING_SCHEDULE_TYPE_LABEL[type || ''] || type || '—'
}

function triggerLabel(type?: string | null) {
  if (type === 'SCHEDULE') return '定时'
  if (type === 'RUN_NOW') return '立即'
  if (type === 'EVENT') return '事件'
  return type || '—'
}

function contentPreview(content?: string | null, max = 72) {
  if (!content) return '暂无正文摘要'
  return content
    .replace(/[#>*`_\[\]()!-]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, max)
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    const [statsRes, recent] = await Promise.all([getBriefingStats(), getBriefingRecent(8)])
    overviewStats.value = statsRes
    sideRecent.value = recent ?? []
  } catch {
    overviewStats.value = null
    sideRecent.value = []
  } finally {
    overviewLoading.value = false
  }
}

async function loadLatest() {
  try {
    latestBriefing.value = await getBriefingLatest()
  } catch {
    latestBriefing.value = null
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const page = await getBriefingsPage({
      pageNum: historyPage.pageNum,
      pageSize: historyPage.pageSize,
    })
    historyList.value = page?.records ?? []
    historyPage.total = Number(page?.total ?? 0)
  } catch {
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

async function loadStats() {
  recentLoading.value = true
  try {
    const [statsRes, recent] = await Promise.all([getBriefingStats(), getBriefingRecent(20)])
    stats.value = statsRes
    overviewStats.value = statsRes
    recentList.value = recent ?? []
  } catch {
    stats.value = null
    recentList.value = []
  } finally {
    recentLoading.value = false
  }
}

async function loadSchedules() {
  if (!userStore.isAdmin) return
  scheduleLoading.value = true
  try {
    schedules.value = (await listBriefingSchedules()) ?? []
  } catch {
    schedules.value = []
  } finally {
    scheduleLoading.value = false
  }
}

async function loadAgents() {
  if (!userStore.isAdmin) return
  try {
    agentOptions.value = (await listAgents('ENABLED')) ?? []
  } catch {
    agentOptions.value = []
  }
}

function handleTabChange(name: string | number) {
  const tab = String(name)
  if (tab === 'history' && historyList.value.length === 0) void loadHistory()
  if (tab === 'stats') void loadStats()
  if (tab === 'schedule') void loadSchedules()
}

async function handleRefresh() {
  refreshing.value = true
  try {
    await loadOverview()
    if (activeTab.value === 'latest') await loadLatest()
    else if (activeTab.value === 'history') await loadHistory()
    else if (activeTab.value === 'stats') await loadStats()
    else if (activeTab.value === 'schedule') await loadSchedules()
    await briefingStore.fetchUnread()
  } finally {
    refreshing.value = false
  }
}

async function handleMarkLatestRead() {
  if (!latestBriefing.value?.id) return
  markingRead.value = true
  try {
    await briefingStore.markRead(latestBriefing.value.id)
    latestBriefing.value = { ...latestBriefing.value, readAt: new Date().toISOString() }
    ElMessage.success('已标记为已读')
    void loadOverview()
  } catch {
    // 拦截器已提示
  } finally {
    markingRead.value = false
  }
}

async function openDetailDrawer(row: BriefingDeliveryVO) {
  if (!row?.id) return
  try {
    drawerBriefing.value = await getBriefingDetail(row.id)
    drawerVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

async function handleMarkDrawerRead() {
  if (!drawerBriefing.value?.id) return
  markingRead.value = true
  try {
    await briefingStore.markRead(drawerBriefing.value.id)
    drawerBriefing.value = { ...drawerBriefing.value, readAt: new Date().toISOString() }
    if (latestBriefing.value?.id === drawerBriefing.value.id) {
      latestBriefing.value = { ...latestBriefing.value, readAt: drawerBriefing.value.readAt }
    }
    const hist = historyList.value.find((i) => i.id === drawerBriefing.value?.id)
    if (hist) hist.readAt = drawerBriefing.value.readAt
    ElMessage.success('已标记为已读')
    void loadOverview()
  } catch {
    // ignore
  } finally {
    markingRead.value = false
  }
}

function handleDrawerClosed() {
  drawerBriefing.value = null
  if (route.query.id) {
    const nextQuery = { ...route.query }
    delete nextQuery.id
    router.replace({ path: route.path, query: nextQuery })
  }
}

function openScheduleDialog(row?: BriefingScheduleVO | Record<string, unknown>) {
  if (row && typeof row === 'object' && 'id' in row) {
    const schedule = row as BriefingScheduleVO
    editingScheduleId.value = schedule.id
    Object.assign(scheduleForm, {
      name: schedule.name,
      agentId: schedule.agentId ?? null,
      promptTemplate: schedule.promptTemplate ?? '',
      scopeType: schedule.scopeType ?? 'USER_PROJECTS',
      scheduleType: schedule.scheduleType,
      scheduleTime: schedule.scheduleTime ?? '08:00',
      scheduleDays: schedule.scheduleDays ?? '',
      cronExpr: schedule.cronExpr ?? '',
      timezone: schedule.timezone ?? 'Asia/Shanghai',
      notifyBell: schedule.notifyBell ?? true,
      notifyEmail: schedule.notifyEmail ?? false,
      emailToMode: schedule.emailToMode ?? 'USER_PROFILE',
      emailExtraTo: schedule.emailExtraTo ?? '',
      emailSubjectTemplate: schedule.emailSubjectTemplate ?? '',
      enabled: schedule.enabled ?? true,
    })
  } else {
    editingScheduleId.value = null
    resetScheduleForm()
  }
  scheduleDialogVisible.value = true
}

function resetScheduleForm() {
  Object.assign(scheduleForm, {
    name: '',
    agentId: null,
    promptTemplate: '',
    scopeType: 'USER_PROJECTS',
    scheduleType: 'DAILY',
    scheduleTime: '08:00',
    scheduleDays: '',
    cronExpr: '',
    timezone: 'Asia/Shanghai',
    notifyBell: true,
    notifyEmail: false,
    emailToMode: 'USER_PROFILE',
    emailExtraTo: '',
    emailSubjectTemplate: '',
    enabled: true,
  })
}

async function submitSchedule() {
  const valid = await scheduleFormRef.value?.validate().catch(() => false)
  if (!valid) return
  scheduleSaving.value = true
  try {
    const payload: BriefingScheduleUpsertDTO = {
      ...scheduleForm,
      agentId: scheduleForm.agentId || null,
    }
    if (editingScheduleId.value) {
      await updateBriefingSchedule(editingScheduleId.value, payload)
      ElMessage.success('计划已更新')
    } else {
      await createBriefingSchedule(payload)
      ElMessage.success('计划已创建')
    }
    scheduleDialogVisible.value = false
    await loadSchedules()
  } catch {
    // ignore
  } finally {
    scheduleSaving.value = false
  }
}

async function handleRunNow(row: BriefingScheduleVO | Record<string, unknown>) {
  const schedule = row as BriefingScheduleVO
  try {
    await ElMessageBox.confirm(`确定立即生成「${schedule.name}」简报吗？`, '立即生成', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  runningId.value = schedule.id
  try {
    const res = await runBriefingScheduleNow(schedule.id)
    ElMessage.success(res?.message || '已提交后台生成，完成后可在简报列表中查看')
    await loadSchedules()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '生成失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    runningId.value = null
  }
}

async function openByQueryId(id: string) {
  try {
    const detail = await getBriefingDetail(id)
    drawerBriefing.value = detail
    drawerVisible.value = true
    activeTab.value = 'history'
    if (!detail.readAt && detail.status === 'SUCCESS') {
      await briefingStore.markRead(id)
      drawerBriefing.value = { ...detail, readAt: new Date().toISOString() }
    }
  } catch {
    // ignore
  }
}

watch(
  () => route.query.id,
  (id) => {
    if (id) void openByQueryId(String(id))
  },
)

onMounted(async () => {
  pageLoading.value = true
  try {
    await Promise.all([loadLatest(), loadOverview()])
    if (userStore.isAdmin) await loadAgents()
    if (route.query.id) await openByQueryId(String(route.query.id))
  } finally {
    pageLoading.value = false
  }
})
</script>

<style scoped lang="scss">
.briefing-center {
  --brief-ink: var(--app-text-primary);
  --brief-body: var(--app-text-regular);
  --brief-muted: var(--app-text-secondary);
  --brief-line: var(--app-border-color);
  --brief-accent: var(--app-primary);
  --brief-accent-soft: color-mix(in srgb, var(--app-primary) 12%, transparent);
  --brief-paper: var(--app-surface-muted);
  --brief-paper-2: var(--app-surface-muted);
  --brief-surface: var(--app-surface-bg);

  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
}

.briefing-masthead {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 0;
  padding: 14px 18px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);
  border: 1px solid var(--app-border-color);
  box-shadow: var(--app-shadow-sm);
  color: var(--app-text-primary);

  &__decor {
    position: absolute;
    inset: 0;
    pointer-events: none;
    overflow: hidden;
  }

  &__glow {
    position: absolute;
    border-radius: 50%;
    filter: blur(2px);

    &--a {
      top: -48%;
      right: -6%;
      width: 280px;
      height: 280px;
      background: radial-gradient(
        circle,
        color-mix(in srgb, var(--app-primary) 22%, transparent) 0%,
        transparent 70%
      );
    }

    &--b {
      bottom: -55%;
      left: -4%;
      width: 220px;
      height: 220px;
      background: radial-gradient(
        circle,
        color-mix(in srgb, var(--app-primary) 14%, transparent) 0%,
        transparent 68%
      );
    }
  }

  &__ring {
    position: absolute;
    border-radius: 50%;
    border: 1px solid color-mix(in srgb, var(--app-primary) 28%, transparent);

    &--lg {
      top: -36px;
      right: 72px;
      width: 160px;
      height: 160px;
      opacity: 0.55;
    }

    &--sm {
      top: 18px;
      right: 118px;
      width: 72px;
      height: 72px;
      opacity: 0.4;
      border-style: dashed;
    }
  }

  &__grid {
    position: absolute;
    inset: 0;
    opacity: 0.35;
    background-image:
      linear-gradient(
        color-mix(in srgb, var(--app-primary) 10%, transparent) 1px,
        transparent 1px
      ),
      linear-gradient(
        90deg,
        color-mix(in srgb, var(--app-primary) 10%, transparent) 1px,
        transparent 1px
      );
    background-size: 22px 22px;
    mask-image: linear-gradient(105deg, transparent 0%, rgba(0, 0, 0, 0.35) 42%, rgba(0, 0, 0, 0.7) 100%);
  }

  &__bar {
    position: absolute;
    left: 0;
    top: 14px;
    bottom: 14px;
    width: 3px;
    border-radius: 0 2px 2px 0;
    background: linear-gradient(
      180deg,
      color-mix(in srgb, var(--app-primary) 75%, transparent),
      color-mix(in srgb, var(--app-primary) 25%, transparent)
    );
  }

  &__text,
  &__aside,
  &__actions {
    position: relative;
    z-index: 1;
  }

  &__text {
    flex: 1;
    min-width: 200px;
    padding-left: 10px;
  }

  &__eyebrow {
    margin: 0 0 4px;
    font-size: 12px;
    font-weight: 500;
    letter-spacing: 0.06em;
    color: var(--app-text-secondary);
  }

  &__title {
    margin: 0 0 4px;
    font-size: 16px;
    font-weight: 600;
    line-height: 1.35;
    color: var(--app-text-primary);
  }

  &__desc {
    margin: 0;
    font-size: 13px;
    color: var(--app-text-secondary);
    line-height: 1.5;
  }

  &__aside {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 10px;
    flex-shrink: 0;
    max-width: 100%;
  }

  &__actions {
    flex-shrink: 0;
  }

  @media (max-width: 960px) {
    flex-direction: column;
    align-items: stretch;

    &__aside {
      align-items: stretch;
    }

    &__actions {
      align-self: flex-end;
    }
  }
}

.briefing-kpi {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;

  &__item {
    min-width: 68px;
    padding: 8px 12px;
    border-radius: var(--app-radius-md);
    background: color-mix(in srgb, var(--app-surface-muted) 88%, var(--app-primary) 6%);
    border: 1px solid var(--app-border-color);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;

    &--success .briefing-kpi__value {
      color: #16a34a;
    }

    &--warning .briefing-kpi__value {
      color: #d97706;
    }

    &--danger .briefing-kpi__value {
      color: #dc2626;
    }

    &--info .briefing-kpi__value {
      color: var(--app-primary);
    }
  }

  &__value {
    font-size: 18px;
    font-weight: 700;
    color: var(--app-text-primary);
    line-height: 1.15;
  }

  &__label {
    font-size: 11px;
    color: var(--app-text-secondary);
  }

  @media (max-width: 960px) {
    justify-content: flex-start;

    &__item {
      flex: 1 1 0;
      min-width: 56px;
    }
  }
}

.briefing-center__tabs {
  margin-bottom: 0;

  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }

  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background: var(--brief-line);
  }

  :deep(.el-tabs__item) {
    font-weight: 500;
  }
}

.briefing-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;

  @media (max-width: 1080px) {
    grid-template-columns: 1fr;
  }
}

.briefing-main {
  min-width: 0;
}

.report-sheet {
  padding: 20px 22px 24px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);
  border: 1px solid var(--app-border-color);
  box-shadow: var(--app-shadow-sm);
  min-height: 420px;

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
  }

  &__badge-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }

  &__series {
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.1em;
    color: var(--brief-accent);
  }

  &__title {
    margin: 0 0 10px;
    font-size: 18px;
    font-weight: 600;
    line-height: 1.35;
    color: var(--brief-ink);
  }

  &__byline {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-bottom: 14px;
    font-size: 13px;
    color: var(--brief-muted);
  }

  &__dot {
    opacity: 0.6;
  }

  &__rule {
    height: 2px;
    margin-bottom: 20px;
    border-radius: 1px;
    background: linear-gradient(90deg, var(--brief-accent) 0%, color-mix(in srgb, var(--app-primary) 8%, transparent) 100%);
  }

  &__body {
    font-size: 15px;
    line-height: 1.85;
    color: var(--brief-body);
  }
}

.briefing-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-card {
  padding: 16px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);
  border: 1px solid var(--app-border-color);
  box-shadow: var(--app-shadow-sm);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  &__title {
    margin: 0 0 10px;
    font-size: 14px;
    font-weight: 600;
    color: var(--brief-ink);
  }

  &--hint .side-card__title {
    margin-bottom: 8px;
  }
}

.side-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 420px;
  overflow-y: auto;

  &__item {
    padding: 10px 8px;
    border-radius: var(--app-radius-md);
    cursor: pointer;
    transition: background 0.15s ease;

    & + & {
      border-top: 1px solid var(--brief-line);
    }

    &:hover,
    &--active {
      background: color-mix(in srgb, var(--app-primary) 6%, var(--app-surface-bg));
    }
  }

  &__row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 4px;
  }

  &__title {
    font-size: 13px;
    font-weight: 500;
    color: var(--brief-ink);
    line-height: 1.4;
  }

  &__preview {
    margin: 0 0 4px;
    font-size: 12px;
    color: var(--brief-muted);
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__meta {
    font-size: 11px;
    color: #94a3b8;
  }
}

.hint-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--brief-muted);
  line-height: 1.7;
}

.briefing-panel {
  padding: 18px 20px 22px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-bg);
  border: 1px solid var(--app-border-color);
  box-shadow: var(--app-shadow-sm);
}

.panel-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;

  &__title {
    margin: 0 0 4px;
    font-size: 16px;
    font-weight: 600;
    color: var(--brief-ink);
  }

  &__desc {
    margin: 0;
    font-size: 12px;
    color: var(--brief-muted);
  }

  &__actions {
    display: flex;
    gap: 8px;
  }
}

.history-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  min-height: 160px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.history-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  text-align: left;
  padding: 16px 18px;
  border-radius: var(--app-radius-md);
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-bg);
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;

  &:hover {
    border-color: color-mix(in srgb, var(--app-primary) 35%, var(--app-border-color));
    box-shadow: var(--app-shadow-sm);
    transform: translateY(-1px);
  }

  &--unread {
    border-color: color-mix(in srgb, var(--app-primary) 30%, var(--app-border-color));
    background: color-mix(in srgb, var(--app-primary) 4%, var(--app-surface-bg));
  }

  &__top {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  &__time {
    margin-left: auto;
    font-size: 12px;
    color: var(--brief-muted);
  }

  &__title {
    margin: 0 0 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--brief-ink);
    line-height: 1.4;
  }

  &__preview {
    margin: 0 0 12px;
    flex: 1;
    font-size: 13px;
    line-height: 1.65;
    color: var(--brief-body);
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__foot {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    padding-top: 10px;
    border-top: 1px dashed var(--brief-line);
    font-size: 12px;
    color: var(--brief-muted);
  }
}

.is-unread {
  font-weight: 700;
  color: var(--brief-ink);
}

.briefing-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 8px;

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.stats-card {
  padding: 18px 16px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-muted);
  text-align: center;
  border: 1px solid var(--app-border-color);

  &__value {
    font-size: 26px;
    font-weight: 700;
    color: var(--app-text-primary);
    line-height: 1.2;
  }

  &__label {
    margin-top: 6px;
    font-size: 13px;
    color: var(--app-text-secondary);
  }

  &--success {
    background: color-mix(in srgb, #67c23a 10%, var(--app-surface-bg));
    .stats-card__value {
      color: #67c23a;
    }
  }

  &--danger {
    background: color-mix(in srgb, #f56c6c 10%, var(--app-surface-bg));
    .stats-card__value {
      color: #f56c6c;
    }
  }

  &--warning {
    background: color-mix(in srgb, #e6a23c 10%, var(--app-surface-bg));
    .stats-card__value {
      color: #e6a23c;
    }
  }

  &--info {
    background: color-mix(in srgb, var(--app-primary) 8%, var(--app-surface-bg));
    .stats-card__value {
      color: var(--app-primary);
    }
  }
}

.section-block {
  margin-top: 20px;

  &__title {
    margin: 0 0 12px;
    font-size: 15px;
    font-weight: 600;
    color: var(--brief-ink);
  }
}

.drawer-report {
  &__eyebrow {
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.12em;
    color: var(--brief-accent);
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 10px;
    margin-bottom: 12px;
  }

  &__time {
    font-size: 13px;
    color: var(--brief-muted);
  }

  &__title {
    margin: 0 0 12px;
    font-size: 18px;
    font-weight: 600;
    line-height: 1.35;
    color: var(--brief-ink);
  }

  &__rule {
    height: 2px;
    margin-bottom: 16px;
    background: linear-gradient(90deg, var(--brief-accent) 0%, color-mix(in srgb, var(--app-primary) 8%, transparent) 100%);
  }

  &__body {
    font-size: 14.5px;
    line-height: 1.85;
    color: var(--brief-body);
  }
}

.drawer-error {
  margin-bottom: 12px;
}

:deep(.markdown-body) {
  h1,
  h2,
  h3 {
    margin: 1.15em 0 0.55em;
    font-weight: 600;
    color: var(--brief-ink);
    line-height: 1.4;
  }

  h1 {
    font-size: 1.35em;
  }

  h2 {
    font-size: 1.2em;
    padding-bottom: 6px;
    border-bottom: 1px solid color-mix(in srgb, var(--app-primary) 20%, transparent);
  }

  h3 {
    font-size: 1.08em;
  }

  p {
    margin: 0.65em 0;
  }

  ul,
  ol {
    margin: 0.55em 0;
    padding-left: 1.4em;
  }

  li + li {
    margin-top: 0.25em;
  }

  table {
    width: 100%;
    margin: 12px 0;
    border-collapse: collapse;
    font-size: 13px;
    background: var(--app-surface-bg);
  }

  th,
  td {
    padding: 8px 10px;
    border: 1px solid var(--brief-line);
    text-align: left;
  }

  th {
    background: color-mix(in srgb, var(--app-primary) 6%, var(--app-surface-bg));
    font-weight: 600;
    color: var(--app-text-primary);
  }

  code {
    padding: 1px 5px;
    border-radius: 3px;
    background: rgba(15, 23, 42, 0.05);
    font-size: 0.92em;
  }

  pre {
    padding: 12px;
    border-radius: var(--app-radius-md);
    background: var(--brief-paper-2);
    overflow-x: auto;
  }

  blockquote {
    margin: 12px 0;
    padding: 8px 14px;
    border-left: 3px solid var(--brief-accent);
    background: var(--brief-accent-soft);
    color: var(--brief-body);
  }
}
</style>
