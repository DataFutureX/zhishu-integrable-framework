<template>
  <div
    class="ai-chat-demo"
    :class="[`ai-chat-demo--${variant}`, { 'ai-chat-demo--fill': fill }]"
    ref="rootRef"
  >
    <div class="ai-chat-demo__chrome">
      <div class="ai-chat-demo__chrome-left">
        <template v-if="variant === 'portal'">
          <span class="ai-chat-demo__dot ai-chat-demo__dot--r" />
          <span class="ai-chat-demo__dot ai-chat-demo__dot--y" />
          <span class="ai-chat-demo__dot ai-chat-demo__dot--g" />
        </template>
        <span v-else-if="variant === 'hero'" class="ai-chat-demo__live" aria-hidden="true">
          <i class="ai-chat-demo__live-dot" />
        </span>
        <el-icon v-else class="ai-chat-demo__title-icon" :size="16"><ChatLineRound /></el-icon>
        <span class="ai-chat-demo__title">{{ title }}</span>
      </div>
      <div class="ai-chat-demo__chrome-right">
        <span class="ai-chat-demo__badge">{{ badge }}</span>
        <button type="button" class="ai-chat-demo__replay" :disabled="playing" @click="playDemo(true)">
          重播
        </button>
      </div>
    </div>

    <div class="ai-chat-demo__body" ref="bodyRef">
      <div
        v-for="(msg, index) in visibleMessages"
        :key="`${msg.role}-${index}`"
        class="ai-chat-demo__msg"
        :class="`ai-chat-demo__msg--${msg.role}`"
      >
        <div class="ai-chat-demo__avatar" aria-hidden="true">
          <el-icon v-if="msg.role === 'assistant'" :size="16"><ChatLineRound /></el-icon>
          <span v-else>值班</span>
        </div>
        <div
          class="ai-chat-demo__bubble"
          :class="{ 'is-thinking': msg.thinking && !msg.typing, 'is-streaming': !!msg.typing }"
        >
          <div v-if="msg.liveTraces?.length" class="ai-chat-demo__progress">
            <div class="ai-chat-demo__progress-head">
              <span class="ai-chat-demo__sse-tag">SSE · progress</span>
              <span class="ai-chat-demo__progress-status">{{ msg.status || '执行中…' }}</span>
            </div>
            <ul class="ai-chat-demo__progress-list">
              <li
                v-for="(t, ti) in msg.liveTraces"
                :key="`${t.type}-${ti}`"
                class="ai-chat-demo__progress-item"
                :class="`is-${progressTone(t.type)}`"
              >
                <span class="ai-chat-demo__progress-type">{{ progressTypeLabel(t.type) }}</span>
                <span class="ai-chat-demo__progress-name">{{ t.name }}</span>
                <span v-if="t.durationMs != null" class="ai-chat-demo__progress-ms">{{ t.durationMs }}ms</span>
              </li>
            </ul>
          </div>
          <div v-else-if="msg.thinking" class="ai-chat-demo__thinking">
            <span class="ai-chat-demo__thinking-dots" aria-hidden="true"><i /><i /><i /></span>
            <span class="ai-chat-demo__thinking-text">{{ msg.status || '正在思考…' }}</span>
          </div>

          <div v-if="msg.typing || msg.html || (msg.lines?.length && !msg.thinking)" class="ai-chat-demo__answer">
            <div v-if="msg.typing" class="ai-chat-demo__sse-row">
              <span class="ai-chat-demo__sse-tag is-message">SSE · message</span>
              <span class="ai-chat-demo__sse-hint">真流式输出中</span>
            </div>
            <div v-if="msg.html && !msg.typing" class="ai-chat-demo__md markdown-body" v-html="msg.html" />
            <template v-else>
              <p v-for="(line, li) in msg.lines" :key="li" class="ai-chat-demo__line">
                {{ line.text }}<span
                  v-if="msg.typing && li === msg.lines.length - 1"
                  class="ai-chat-demo__cursor"
                  aria-hidden="true"
                />
              </p>
            </template>
          </div>

          <AgentTracePanel
            v-if="msg.traces?.length && !msg.thinking && !msg.typing"
            :traces="msg.traces"
            :default-expand="!!msg.showTrace"
          />
        </div>
      </div>
      <div v-if="loading" class="ai-chat-demo__placeholder">正在加载智能问答历史演示…</div>
      <div v-else-if="!visibleMessages.length" class="ai-chat-demo__placeholder">
        {{ loadError || emptyHint }}
      </div>
    </div>

    <div class="ai-chat-demo__composer" aria-hidden="true">
      <div
        class="ai-chat-demo__composer-input"
        :class="{ 'is-typing': !!inputDraft, 'is-empty': !inputDraft }"
      >
        <span v-if="inputDraft" class="ai-chat-demo__composer-value">
          {{ inputDraft }}<i v-if="inputCaret" class="ai-chat-demo__composer-caret" />
        </span>
        <span v-else class="ai-chat-demo__composer-placeholder">请输入您的问题…</span>
      </div>
      <button
        type="button"
        class="ai-chat-demo__composer-send"
        :class="{ 'is-active': sendActive, 'is-pressed': sendPressed }"
        tabindex="-1"
      >
        发送
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ChatLineRound } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import { listPortalQaDemo } from '@/api/ai'
import type { QaHistoryVO } from '@/types/qaHistory'
import type { AgentTraceEvent } from '@/types/aiChat'
import AgentTracePanel from '@/components/ai/AgentTracePanel.vue'

type AiChatLine = { text: string }
type AiChatMsg = {
  role: 'user' | 'assistant'
  content: string
  lines: AiChatLine[]
  html?: string
  typing?: boolean
  thinking?: boolean
  status?: string
  liveTraces?: AgentTraceEvent[]
  traces?: AgentTraceEvent[]
  showTrace?: boolean
}

type ProgressStep = { event: AgentTraceEvent; status: string; delayMs: number }

const props = withDefaults(
  defineProps<{
    variant?: 'portal' | 'hero' | 'dashboard'
    title?: string
    badge?: string
    emptyHint?: string
    playOnVisible?: boolean
    autoPlay?: boolean
    fill?: boolean
  }>(),
  {
    variant: 'portal',
    title: '数智中枢 · 对话演示',
    badge: '流式过程',
    emptyHint: '即将演示最近两轮智能问答…',
    playOnVisible: false,
    autoPlay: false,
    fill: false,
  },
)

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const DEMO_SCRIPT: AiChatMsg[] = [
  {
    role: 'user',
    content: '青溪水文站现在水位多少？有没有超限？',
    lines: [{ text: '青溪水文站现在水位多少？有没有超限？' }],
  },
  {
    role: 'assistant',
    content: [
      '已查询到 **青溪水文站**（DEMO0001001）最新监测数据：',
      '',
      '- 水位：`12.35 m`，流量：`86.2 m³/s`',
      '- 站点在线，协议 SL 651-2014',
      '- 关联工程：青溪防洪工程',
      '',
      '当前水位正常，暂未触发阈值告警。西区液位站实测 `16.20 m` 已超一级阈值，建议一并关注。',
    ].join('\n'),
    lines: [],
  },
  {
    role: 'user',
    content: '本周巡检进度如何？有没有未关闭异常？',
    lines: [{ text: '本周巡检进度如何？有没有未关闭异常？' }],
  },
  {
    role: 'assistant',
    content: [
      '**巡检业务摘要**（getInspectionSummary）：',
      '',
      '| 类型 | 内容 |',
      '| --- | --- |',
      '| 进行中 | 青溪防洪周巡-本周（检查点 1/2） |',
      '| 待开始 | 碧湖入库月检-本月 |',
      '| 未关闭异常 | 东区雨量站离线待复核（二级） |',
      '',
      '建议优先完成东区雨量站打卡；写操作请在「巡检管理」执行。',
    ].join('\n'),
    lines: [],
  },
]

function ensureAssistantHtml(msg: AiChatMsg): AiChatMsg {
  if (msg.role !== 'assistant') return msg
  const content = msg.content || ''
  return {
    ...msg,
    lines: content.split(/\n/).filter(Boolean).map((text) => ({ text })),
    html: msg.html || md.render(content),
  }
}

function historyToScript(records: QaHistoryVO[]): AiChatMsg[] {
  const next: AiChatMsg[] = []
  for (const row of records) {
    const question = (row.question || '').trim()
    const answer = (row.answer || '').trim()
    if (!question && !answer) continue
    if (question) next.push({ role: 'user', content: question, lines: [{ text: question }] })
    if (answer) next.push(ensureAssistantHtml({ role: 'assistant', content: answer, lines: [] }))
  }
  return next
}

function progressTypeLabel(type: string): string {
  return ({ NODE_START: '节点', NODE_END: '完成', TOOL_CALL: '工具', TOOL_RESULT: '结果', ROUTE: '路由' } as Record<string, string>)[type] || type
}

function progressTone(type: string): string {
  if (type === 'TOOL_CALL' || type === 'TOOL_RESULT') return 'tool'
  if (type === 'NODE_END') return 'end'
  if (type === 'NODE_START') return 'start'
  return 'default'
}

function progressStepsFor(question: string): ProgressStep[] {
  const q = question.toLowerCase()
  const mk = (type: string, name: string, status: string, delayMs: number, durationMs?: number, detail?: string): ProgressStep => ({
    event: { type, name, detail, durationMs: durationMs ?? null, timestamp: Date.now() },
    status,
    delayMs,
  })
  if (/告警|超限|阈值|雨量|水位|流量|对比|趋势|站点|遥测/.test(q)) {
    return [
      mk('NODE_START', 'REACT · 理解意图', 'progress · 节点开始：理解意图', 480),
      mk('NODE_END', 'REACT · 理解意图', 'progress · 意图：查询水位与阈值', 360, 210),
      mk('TOOL_CALL', 'getLatestElement', 'progress · 调用 getLatestElement…', 520, undefined, '{"station":"DEMO0001001"}'),
      mk('TOOL_RESULT', 'getLatestElement', 'progress · 遥测回传完成', 480, 186),
      mk('TOOL_CALL', 'queryAlerts', 'progress · 调用 queryAlerts…', 420),
      mk('TOOL_RESULT', 'queryAlerts', 'progress · 告警结果回传', 420, 142),
      mk('NODE_START', 'LLM · 真流式生成', 'SSE · message 即将开始…', 360),
    ]
  }
  if (/巡检|计划|任务|异常|检查点|摘要/.test(q)) {
    return [
      mk('NODE_START', 'SEQUENTIAL · 澄清', 'progress · 顺序工作流启动', 440),
      mk('NODE_END', 'SEQUENTIAL · 澄清', 'progress · 澄清：巡检进度与异常', 340, 168),
      mk('TOOL_CALL', 'getInspectionSummary', 'progress · 调用 getInspectionSummary…', 540),
      mk('TOOL_RESULT', 'getInspectionSummary', 'progress · 巡检摘要回传', 520, 224),
      mk('NODE_START', 'LLM · 润色输出', 'SSE · message 即将开始…', 360),
    ]
  }
  return [
    mk('NODE_START', 'REACT · 理解意图', 'progress · 正在理解问题…', 440),
    mk('NODE_END', 'REACT · 理解意图', 'progress · 意图解析完成', 320, 160),
    mk('NODE_START', 'LLM · 真流式生成', 'SSE · message 即将开始…', 340),
  ]
}

function tokenizeForStream(content: string): string[] {
  const tokens: string[] = []
  for (const part of content.split(/(\n+)/)) {
    if (!part) continue
    if (/^\n+$/.test(part)) {
      tokens.push(part)
      continue
    }
    const matches = part.match(/[\u4e00-\u9fff]|[a-zA-Z0-9._%-]+|\s+|[^\s\u4e00-\u9fff]/g)
    if (matches) tokens.push(...matches)
  }
  return tokens.length ? tokens : [content]
}

function cloneDemoScript(): AiChatMsg[] {
  return DEMO_SCRIPT.map((m) =>
    m.role === 'assistant' ? ensureAssistantHtml({ ...m }) : { ...m, lines: [...m.lines] },
  )
}

function patchMessage(index: number, patch: Partial<AiChatMsg>) {
  const current = visibleMessages.value[index]
  if (!current) return
  visibleMessages.value[index] = { ...current, ...patch }
}

const rootRef = ref<HTMLElement | null>(null)
const bodyRef = ref<HTMLElement | null>(null)
const script = ref<AiChatMsg[]>([])
const visibleMessages = ref<AiChatMsg[]>([])
const playing = ref(false)
const hasPlayed = ref(false)
const loading = ref(false)
const loadError = ref('')
const inputDraft = ref('')
const inputCaret = ref(false)
const sendActive = ref(false)
const sendPressed = ref(false)

let timers: number[] = []
let observer: IntersectionObserver | null = null
let loadPromise: Promise<void> | null = null

function clearTimers() {
  timers.forEach((id) => window.clearTimeout(id))
  timers = []
}

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    const id = window.setTimeout(() => resolve(), ms)
    timers.push(id)
  })
}

async function scrollToBottom() {
  await nextTick()
  const el = bodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function loadScript() {
  if (loadPromise) return loadPromise
  loading.value = true
  script.value = cloneDemoScript()
  loading.value = false
  loadPromise = (async () => {
    let timeoutId = 0
    try {
      const records = await Promise.race([
        listPortalQaDemo(2, 'CHAT'),
        new Promise<null>((resolve) => {
          timeoutId = window.setTimeout(() => resolve(null), 1200)
        }),
      ])
      if (!records?.length || playing.value || hasPlayed.value) return
      const next = historyToScript(records)
      if (next.length >= 2) {
        script.value = next.map((m) => (m.role === 'assistant' ? ensureAssistantHtml(m) : m))
      }
    } catch (error) {
      console.warn('加载问答演示失败，继续使用内置脚本', error)
    } finally {
      if (timeoutId) window.clearTimeout(timeoutId)
    }
  })()
  try {
    await loadPromise
  } finally {
    loadPromise = null
  }
}

async function simulateUserInputAndSend(question: string) {
  inputDraft.value = ''
  inputCaret.value = true
  sendActive.value = false
  sendPressed.value = false
  await sleep(360)
  const chars = Array.from(question)
  for (let i = 0; i < chars.length; i++) {
    inputDraft.value += chars[i]
    sendActive.value = true
    let delay = 34 + (i % 7 === 0 ? 36 : 0)
    if (/[，。？！、；：]/.test(chars[i])) delay += 100
    await sleep(delay)
  }
  await sleep(380)
  inputCaret.value = false
  sendPressed.value = true
  await sleep(160)
  sendPressed.value = false
  await sleep(120)
  visibleMessages.value.push({ role: 'user', content: question, lines: [{ text: question }] })
  inputDraft.value = ''
  sendActive.value = false
  await scrollToBottom()
  await sleep(240)
}

async function simulateAssistantTurn(question: string, full: AiChatMsg) {
  const steps = progressStepsFor(question)
  visibleMessages.value.push({
    role: 'assistant',
    content: '',
    lines: [],
    thinking: true,
    status: 'SSE 已连接 · 等待 progress…',
    liveTraces: [],
  })
  const idx = visibleMessages.value.length - 1
  await scrollToBottom()
  await sleep(260)

  const live: AgentTraceEvent[] = []
  for (const step of steps) {
    live.push({ ...step.event, timestamp: Date.now() })
    patchMessage(idx, { thinking: true, status: step.status, liveTraces: [...live] })
    await scrollToBottom()
    await sleep(step.delayMs)
  }

  const content = full.content || ''
  const tokens = tokenizeForStream(content)
  let buffer = ''
  patchMessage(idx, {
    thinking: false,
    typing: true,
    status: 'SSE · message 真流式输出中…',
    liveTraces: [...live],
    lines: [{ text: '' }],
    html: undefined,
  })
  await scrollToBottom()
  await sleep(180)

  for (let i = 0; i < tokens.length; i++) {
    buffer += tokens[i]
    patchMessage(idx, {
      typing: true,
      liveTraces: [...live],
      lines: buffer.split('\n').map((text) => ({ text })),
    })
    if (i % 2 === 0 || /[。！？\n]/.test(tokens[i])) await scrollToBottom()
    const token = tokens[i]
    let delay = 20
    if (/[\u4e00-\u9fff]/.test(token)) delay = 28
    else if (/^[a-zA-Z0-9]/.test(token)) delay = 12
    if (token.includes('\n')) delay += 60
    if (/[，。！？、；：]/.test(token)) delay += 48
    await sleep(delay)
  }

  await sleep(220)
  patchMessage(idx, {
    thinking: false,
    typing: false,
    liveTraces: undefined,
    traces: live.map((t) => ({ ...t })),
    showTrace: true,
    html: full.html || md.render(content || '（无回答内容）'),
    lines: [],
    content,
  })
  await scrollToBottom()
  await sleep(1200)
  patchMessage(idx, { showTrace: false })
}

async function playDemo(force = false) {
  if (playing.value) return
  if (hasPlayed.value && !force) return
  if (!script.value.length) script.value = cloneDemoScript()
  if (force) {
    hasPlayed.value = false
    script.value = cloneDemoScript()
    void loadScript()
  }
  const list = script.value
  if (!list.length) return
  clearTimers()
  playing.value = true
  visibleMessages.value = []
  inputDraft.value = ''
  inputCaret.value = true
  sendActive.value = false
  sendPressed.value = false
  try {
    let lastQuestion = ''
    for (const msg of list) {
      if (msg.role === 'user') {
        lastQuestion = msg.content
        await simulateUserInputAndSend(msg.content)
      } else {
        await simulateAssistantTurn(lastQuestion || msg.content, ensureAssistantHtml(msg))
        await sleep(640)
      }
    }
    hasPlayed.value = true
  } finally {
    playing.value = false
    inputDraft.value = ''
    inputCaret.value = false
    sendActive.value = false
    sendPressed.value = false
  }
}

function setupObserver() {
  if (!rootRef.value || typeof IntersectionObserver === 'undefined') {
    void playDemo()
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting && e.intersectionRatio >= 0.35)) {
        void playDemo()
        observer?.disconnect()
        observer = null
      }
    },
    { threshold: [0.35] },
  )
  observer.observe(rootRef.value)
}

onMounted(() => {
  void (async () => {
    script.value = cloneDemoScript()
    await nextTick()
    if (props.autoPlay) void playDemo()
    else if (props.playOnVisible) setupObserver()
    else void loadScript()
    if (props.autoPlay || props.playOnVisible) void loadScript()
  })()
})

onUnmounted(() => {
  clearTimers()
  observer?.disconnect()
  observer = null
})
</script>

<style scoped lang="scss">
$primary: #409eff;
$primary-dark: #1a7fd4;
$cyan: #0891b2;
$cyan-bright: #22d3ee;

.ai-chat-demo {
  display: flex;
  flex-direction: column;
  width: 100%;
  overflow: hidden;
}
.ai-chat-demo--fill {
  height: 100%;
  min-height: 360px;
}

.ai-chat-demo__chrome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
}
.ai-chat-demo__chrome-left,
.ai-chat-demo__chrome-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai-chat-demo__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  &--r { background: #f87171; }
  &--y { background: #fbbf24; }
  &--g { background: #34d399; }
}
.ai-chat-demo__title-icon { color: $primary; }
.ai-chat-demo__title {
  font-size: 13px;
  font-weight: 600;
}
.ai-chat-demo__badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
}
.ai-chat-demo__replay {
  border-radius: 8px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  &:disabled { opacity: 0.45; cursor: not-allowed; }
}

.ai-chat-demo__body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  scroll-behavior: smooth;
}
.ai-chat-demo__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  font-size: 13px;
}
.ai-chat-demo__msg {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  animation: aiChatMsgIn 0.35s ease both;
  &--user { flex-direction: row-reverse; }
}
.ai-chat-demo__avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
}
.ai-chat-demo__bubble {
  max-width: min(560px, 86%);
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.65;
  &.is-thinking { min-width: 220px; }
  &.is-streaming { min-width: min(420px, 86%); }
}
.ai-chat-demo__msg--user .ai-chat-demo__bubble {
  background: linear-gradient(135deg, $primary, $primary-dark);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-chat-demo__progress { display: flex; flex-direction: column; gap: 8px; }
.ai-chat-demo__progress-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ai-chat-demo__progress-status { font-size: 11.5px; opacity: 0.78; }
.ai-chat-demo__sse-tag {
  display: inline-flex;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  background: rgba(8, 145, 178, 0.12);
  color: #0e7490;
  border: 1px solid rgba(8, 145, 178, 0.22);
  &.is-message {
    background: rgba(64, 158, 255, 0.12);
    color: $primary-dark;
    border-color: rgba(64, 158, 255, 0.28);
  }
}
.ai-chat-demo__sse-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.ai-chat-demo__sse-hint { font-size: 11px; opacity: 0.7; }
.ai-chat-demo__progress-list { margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 4px; }
.ai-chat-demo__progress-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 5px 8px;
  border-radius: 8px;
  background: rgba(64, 158, 255, 0.06);
  border: 1px solid rgba(64, 158, 255, 0.12);
  font-size: 11.5px;
  animation: aiChatMsgIn 0.28s ease both;
  &.is-tool { background: rgba(245, 158, 11, 0.08); border-color: rgba(245, 158, 11, 0.2); }
  &.is-end { background: rgba(16, 185, 129, 0.07); border-color: rgba(16, 185, 129, 0.18); }
}
.ai-chat-demo__progress-type { font-size: 10px; font-weight: 700; opacity: 0.72; }
.ai-chat-demo__progress-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }
.ai-chat-demo__progress-ms { font-size: 10px; opacity: 0.55; }
.ai-chat-demo__answer { min-width: 0; }
.ai-chat-demo__progress + .ai-chat-demo__answer {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(64, 158, 255, 0.18);
}

.ai-chat-demo__thinking { display: inline-flex; align-items: center; gap: 10px; }
.ai-chat-demo__thinking-text { font-size: 12.5px; opacity: 0.88; }
.ai-chat-demo__thinking-dots {
  display: inline-flex; gap: 4px;
  i {
    width: 5px; height: 5px; border-radius: 50%; background: currentColor; opacity: 0.45;
    animation: aiChatThinkDot 1.1s ease-in-out infinite;
    &:nth-child(2) { animation-delay: 0.15s; }
    &:nth-child(3) { animation-delay: 0.3s; }
  }
}
.ai-chat-demo__line {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  + .ai-chat-demo__line { margin-top: 6px; }
}
.ai-chat-demo__md {
  font-size: 13px;
  line-height: 1.65;
  word-break: break-word;
  :deep(p) { margin: 0 0 8px; &:last-child { margin-bottom: 0; } }
  :deep(ul), :deep(ol) { margin: 0 0 8px; padding-left: 1.2em; }
  :deep(table) { width: 100%; border-collapse: collapse; margin: 8px 0; font-size: 12px; }
  :deep(th), :deep(td) { border: 1px solid rgba(64, 158, 255, 0.2); padding: 4px 6px; }
  :deep(th) { background: rgba(64, 158, 255, 0.08); }
  :deep(code) { padding: 0 4px; border-radius: 4px; background: rgba(64, 158, 255, 0.08); }
}
.ai-chat-demo__cursor {
  display: inline-block;
  width: 7px;
  height: 1em;
  margin-left: 1px;
  vertical-align: text-bottom;
  background: $primary;
  animation: aiChatCaret 0.9s step-end infinite;
}
.ai-chat-demo__composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px 14px 14px;
  flex-shrink: 0;
}
.ai-chat-demo__composer-input {
  flex: 1;
  min-height: 40px;
  padding: 9px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
}
.ai-chat-demo__composer-caret {
  display: inline-block;
  width: 1.5px;
  height: 1em;
  margin-left: 1px;
  vertical-align: text-bottom;
  background: $primary-dark;
  animation: aiChatCaret 0.9s step-end infinite;
}
.ai-chat-demo__composer-send {
  border: none;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #94a3b8;
  &.is-active { background: linear-gradient(135deg, $primary, $primary-dark); }
  &.is-pressed { transform: scale(0.96); }
}
.ai-chat-demo__live {
  display: inline-flex;
  width: 10px;
  height: 10px;
  align-items: center;
  justify-content: center;
}
.ai-chat-demo__live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4ade80;
  animation: aiChatLivePulse 1.8s ease-out infinite;
}

@keyframes aiChatMsgIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes aiChatThinkDot {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.35; }
  40% { transform: translateY(-3px); opacity: 1; }
}
@keyframes aiChatCaret { 50% { opacity: 0; } }
@keyframes aiChatLivePulse {
  0% { box-shadow: 0 0 0 0 rgba(74, 222, 128, 0.5); }
  70% { box-shadow: 0 0 0 7px rgba(74, 222, 128, 0); }
  100% { box-shadow: 0 0 0 0 rgba(74, 222, 128, 0); }
}

.ai-chat-demo--portal {
  max-width: 860px;
  margin-inline: auto;
  border-radius: 16px;
  background: #f7fbfe;
  border: 1px solid rgba(64, 158, 255, 0.18);
  box-shadow: 0 16px 40px rgba(26, 86, 140, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.8);

  .ai-chat-demo__chrome {
    padding: 12px 16px;
    background: linear-gradient(180deg, #eef6fc 0%, #e6f1fa 100%);
    border-bottom: 1px solid rgba(64, 158, 255, 0.14);
  }
  .ai-chat-demo__title { margin-left: 6px; color: #1f3a4d; }
  .ai-chat-demo__badge {
    color: #0e7490;
    background: rgba(8, 145, 178, 0.1);
    border: 1px solid rgba(8, 145, 178, 0.22);
  }
  .ai-chat-demo__replay {
    border: 1px solid rgba(64, 158, 255, 0.28);
    background: rgba(255, 255, 255, 0.7);
    color: #3b6f8f;
  }
  .ai-chat-demo__body {
    min-height: 320px;
    max-height: 520px;
    padding: 18px 16px 12px;
    background:
      radial-gradient(ellipse at top right, rgba(64, 158, 255, 0.08), transparent 48%),
      linear-gradient(180deg, #f8fcff 0%, #f2f7fb 100%);
  }
  .ai-chat-demo__placeholder { min-height: 280px; color: #7a93a8; }
  .ai-chat-demo__msg--user .ai-chat-demo__avatar {
    background: rgba(64, 158, 255, 0.16);
    color: $primary-dark;
  }
  .ai-chat-demo__msg--assistant .ai-chat-demo__bubble {
    background: #fff;
    color: #2c3e50;
    border: 1px solid rgba(64, 158, 255, 0.14);
    box-shadow: 0 2px 10px rgba(26, 86, 140, 0.05);
    border-bottom-left-radius: 4px;
  }
  .ai-chat-demo__msg--assistant .ai-chat-demo__avatar {
    background: linear-gradient(135deg, $cyan, $cyan-bright);
    color: #04202a;
  }
  .ai-chat-demo__composer {
    border-top: 1px solid rgba(64, 158, 255, 0.12);
    background: #fff;
  }
  .ai-chat-demo__composer-input {
    border: 1px solid rgba(64, 158, 255, 0.18);
    background: #f8fbfe;
    color: #1f3a4d;
  }
}

.ai-chat-demo--hero {
  border-radius: 18px;
  background: linear-gradient(165deg, rgba(6, 42, 64, 0.78), rgba(6, 36, 56, 0.82));
  border: 1px solid rgba(103, 232, 249, 0.36);
  .ai-chat-demo__chrome {
    padding: 10px 14px;
    border-bottom: 1px solid rgba(103, 232, 249, 0.22);
  }
  .ai-chat-demo__title { color: #fff; }
  .ai-chat-demo__body { padding: 14px 12px 10px; }
  .ai-chat-demo__msg--assistant .ai-chat-demo__bubble {
    background: rgba(255, 255, 255, 0.14);
    color: #f8fcff;
    border: 1px solid rgba(186, 230, 253, 0.35);
  }
}
</style>
