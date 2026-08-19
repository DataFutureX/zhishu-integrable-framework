/**
 * AI 简报演示 Mock
 */
import type {
  BriefingDeliveryPageResult,
  BriefingDeliveryVO,
  BriefingScheduleUpsertDTO,
  BriefingScheduleVO,
  BriefingStatsVO,
} from '@/types/briefing'
import { daysAgoStr, delay, nowStr, paginate } from './utils'

const SAMPLE_MD = `## 监测态势摘要

演示环境简报：遥测站在线率良好，雨情与水位总体平稳。

### 关键要点

- 青溪水文站水位运行正常
- 东区雨量站今日累计偏高，建议关注离线补报
- 西区液位站存在一级阈值告警，请值班复核

### 巡检与处置

- 青溪防洪周巡进行中，检查点完成 1/2
- 建议优先复核供电与通信链路
`

let deliverySeq = 9303
let scheduleSeq = 9202

const deliveries: BriefingDeliveryVO[] = [
  {
    id: 9301,
    scheduleId: 9201,
    triggerType: 'SCHEDULE',
    title: '晨间监测简报（演示）',
    contentMd: SAMPLE_MD,
    status: 'SUCCESS',
    startedAt: daysAgoStr(0, 7, 0),
    finishedAt: daysAgoStr(0, 7, 2),
    readAt: null,
    emailStatus: 'SENT',
    createTime: daysAgoStr(0, 7, 0),
  },
  {
    id: 9302,
    scheduleId: 9201,
    triggerType: 'SCHEDULE',
    title: '昨日运行简报（演示）',
    contentMd: SAMPLE_MD.replace('晨间', '昨日'),
    status: 'SUCCESS',
    startedAt: daysAgoStr(1, 18, 0),
    finishedAt: daysAgoStr(1, 18, 3),
    readAt: daysAgoStr(1, 19, 10),
    emailStatus: 'SENT',
    createTime: daysAgoStr(1, 18, 0),
  },
  {
    id: 9303,
    scheduleId: null,
    triggerType: 'MANUAL',
    title: '告警复核简报（演示）',
    contentMd: '## 告警复核\n\n演示模式：东区雨量、西区液位告警待复核。',
    status: 'SUCCESS',
    startedAt: daysAgoStr(2, 10, 0),
    finishedAt: daysAgoStr(2, 10, 1),
    readAt: null,
    emailStatus: 'SKIPPED',
    createTime: daysAgoStr(2, 10, 0),
  },
  {
    id: 9304,
    scheduleId: 9201,
    triggerType: 'SCHEDULE',
    title: '生成失败样例（演示）',
    contentMd: null,
    status: 'FAILED',
    errorMessage: '演示：Agent 调用超时',
    startedAt: daysAgoStr(3, 7, 0),
    finishedAt: daysAgoStr(3, 7, 1),
    readAt: null,
    emailStatus: 'NONE',
    createTime: daysAgoStr(3, 7, 0),
  },
]

const schedules: BriefingScheduleVO[] = [
  {
    id: 9201,
    name: '每日晨报',
    agentId: 1,
    promptTemplate: '汇总昨日监测态势、告警与巡检要点',
    scopeType: 'ALL',
    scheduleType: 'DAILY',
    scheduleTime: '07:00',
    timezone: 'Asia/Shanghai',
    nextRunAt: daysAgoStr(-1, 7, 0),
    lastRunAt: daysAgoStr(0, 7, 0),
    notifyBell: true,
    notifyEmail: true,
    emailToMode: 'SELF',
    enabled: true,
    createdBy: 'demo',
    createTime: daysAgoStr(7),
    updateTime: nowStr(),
  },
]

function sortedDeliveries() {
  return [...deliveries].sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || '')))
}

export async function mockGetBriefingLatest(): Promise<BriefingDeliveryVO | null> {
  await delay()
  return sortedDeliveries().find((item) => item.status === 'SUCCESS') ?? null
}

export async function mockGetBriefingsPage(params: {
  pageNum?: number
  pageSize?: number
  status?: string
}): Promise<BriefingDeliveryPageResult> {
  await delay()
  let list = sortedDeliveries()
  if (params.status) {
    list = list.filter((item) => item.status === params.status)
  }
  return paginate(list, params.pageNum ?? 1, params.pageSize ?? 10)
}

export async function mockGetBriefingUnreadCount(): Promise<{ count: number }> {
  await delay(60)
  const count = deliveries.filter((item) => item.status === 'SUCCESS' && !item.readAt).length
  return { count }
}

export async function mockGetBriefingRecent(limit = 10): Promise<BriefingDeliveryVO[]> {
  await delay()
  return sortedDeliveries().slice(0, Math.max(1, limit))
}

export async function mockGetBriefingStats(): Promise<BriefingStatsVO> {
  await delay()
  const success = deliveries.filter((item) => item.status === 'SUCCESS').length
  const failed = deliveries.filter((item) => item.status === 'FAILED').length
  const unread = deliveries.filter((item) => item.status === 'SUCCESS' && !item.readAt).length
  const pendingOrRunning = deliveries.filter(
    (item) => item.status === 'PENDING' || item.status === 'RUNNING',
  ).length
  return {
    total: deliveries.length,
    success,
    failed,
    unread,
    pendingOrRunning,
  }
}

export async function mockGetBriefingDetail(id: number | string): Promise<BriefingDeliveryVO> {
  await delay()
  const found = deliveries.find((item) => String(item.id) === String(id))
  if (!found) throw new Error('简报不存在')
  return { ...found }
}

export async function mockMarkBriefingRead(id: number | string): Promise<void> {
  await delay(80)
  const found = deliveries.find((item) => String(item.id) === String(id))
  if (found && !found.readAt) {
    found.readAt = nowStr()
  }
}

export async function mockListBriefingSchedules(): Promise<BriefingScheduleVO[]> {
  await delay()
  return schedules.map((item) => ({ ...item }))
}

export async function mockCreateBriefingSchedule(
  data: BriefingScheduleUpsertDTO,
): Promise<BriefingScheduleVO> {
  await delay()
  const created: BriefingScheduleVO = {
    id: ++scheduleSeq,
    name: data.name,
    agentId: data.agentId ?? null,
    promptTemplate: data.promptTemplate ?? null,
    scopeType: data.scopeType ?? null,
    scheduleType: data.scheduleType,
    scheduleTime: data.scheduleTime ?? null,
    scheduleDays: data.scheduleDays ?? null,
    cronExpr: data.cronExpr ?? null,
    timezone: data.timezone ?? 'Asia/Shanghai',
    nextRunAt: daysAgoStr(-1, 7, 0),
    lastRunAt: null,
    notifyBell: data.notifyBell ?? true,
    notifyEmail: data.notifyEmail ?? false,
    emailToMode: data.emailToMode ?? null,
    emailExtraTo: data.emailExtraTo ?? null,
    emailSubjectTemplate: data.emailSubjectTemplate ?? null,
    enabled: data.enabled ?? true,
    createdBy: 'demo',
    createTime: nowStr(),
    updateTime: nowStr(),
  }
  schedules.unshift(created)
  return { ...created }
}

export async function mockUpdateBriefingSchedule(
  id: number | string,
  data: BriefingScheduleUpsertDTO,
): Promise<BriefingScheduleVO> {
  await delay()
  const idx = schedules.findIndex((item) => String(item.id) === String(id))
  if (idx < 0) throw new Error('计划不存在')
  schedules[idx] = {
    ...schedules[idx],
    ...data,
    updateTime: nowStr(),
  }
  return { ...schedules[idx] }
}

export async function mockRunBriefingScheduleNow(
  id: number | string,
): Promise<{ scheduleId: number; generated: number }> {
  await delay(200)
  const schedule = schedules.find((item) => String(item.id) === String(id))
  if (!schedule) throw new Error('计划不存在')
  const created: BriefingDeliveryVO = {
    id: ++deliverySeq,
    scheduleId: schedule.id,
    triggerType: 'MANUAL',
    title: `${schedule.name}（立即生成）`,
    contentMd: SAMPLE_MD,
    status: 'SUCCESS',
    startedAt: nowStr(),
    finishedAt: nowStr(),
    readAt: null,
    emailStatus: schedule.notifyEmail ? 'PENDING' : 'SKIPPED',
    createTime: nowStr(),
  }
  deliveries.unshift(created)
  schedule.lastRunAt = nowStr()
  return { scheduleId: Number(schedule.id), generated: 1 }
}
