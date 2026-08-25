<template>
  <div class="open-api-page">
    <section class="page-header">
      <div class="page-header__main">
        <h1 class="page-header__title">开放能力</h1>
        <p class="page-header__desc">
          知枢框架对外开放的 API 能力总览，供他方系统接入与集成使用
        </p>
      </div>
      <div class="page-header__stats">
        <div class="stat-card">
          <div class="stat-card__value">{{ totalApiCount }}</div>
          <div class="stat-card__label">开放接口</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__value">{{ categories.length }}</div>
          <div class="stat-card__label">能力分类</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__value">{{ openApiCount }}</div>
          <div class="stat-card__label">Open API</div>
        </div>
      </div>
    </section>

    <!-- 接入指南 -->
    <section class="guide-section">
      <header class="section-header">
        <h2><el-icon><Connection /></el-icon> 接入说明</h2>
      </header>
      <div class="guide-cards">
        <div class="guide-card">
          <div class="guide-card__icon guide-card__icon--auth">
            <el-icon :size="20"><Key /></el-icon>
          </div>
          <div class="guide-card__body">
            <h3>认证方式</h3>
            <p>管理接口（<code>/api/v1/*</code>）需携带 JWT Token：</p>
            <div class="code-block">
              <code>Authorization: Bearer &lt;your-jwt-token&gt;</code>
            </div>
            <p>登录获取 Token：<code>POST /api/v1/auth/login</code></p>
          </div>
        </div>
        <div class="guide-card">
          <div class="guide-card__icon guide-card__icon--open">
            <el-icon :size="20"><Unlock /></el-icon>
          </div>
          <div class="guide-card__body">
            <h3>Open API 认证（AK/SK）</h3>
            <p>开放接口（<code>/open/v1/*</code>）使用 AK/SK 签名 Token：</p>
            <div class="code-block">
              <code>Authorization: Bearer {ak}:{timestamp}:{signature}</code>
            </div>
            <p>签名算法：<code>signature = Base64(HMAC-SHA256(sk, ak + timestamp))</code></p>
            <p>AK/SK 通过下方"Open API 接入凭证管理"生成，Token 有效期 5 分钟。</p>
          </div>
        </div>
        <div class="guide-card">
          <div class="guide-card__icon guide-card__icon--sso">
            <el-icon :size="20"><Link /></el-icon>
          </div>
          <div class="guide-card__body">
            <h3>SSO 单点登录</h3>
            <p>伙伴系统可通过 RSA / SM2 签名 Ticket 换取业务 JWT：</p>
            <div class="code-block">
              <code>POST /api/v1/auth/sso/exchange</code>
            </div>
            <p>详见 <strong>docs/单点登录对接说明.md</strong> 及 SSO SDK。</p>
          </div>
        </div>
        <div class="guide-card">
          <div class="guide-card__icon guide-card__icon--base">
            <el-icon :size="20"><Monitor /></el-icon>
          </div>
          <div class="guide-card__body">
            <h3>基础信息</h3>
            <p>Base URL：<code>/api/v1</code>（管理）· <code>/open/v1</code>（开放）</p>
            <p>数据格式：JSON · 字符编码：UTF-8</p>
            <p>接口文档：开发工具 → 接口文档（Swagger UI）</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Open API 接入凭证管理 -->
    <section class="cred-section">
      <header class="section-header">
        <div>
          <h2><el-icon><Key /></el-icon> Open API 接入凭证管理</h2>
          <p class="section-header__desc">管理开放应用的 AK/SK 凭证与调用范围</p>
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> 新增应用
        </el-button>
      </header>

      <el-table :data="apps" v-loading="appsLoading" stripe style="width: 100%">
        <el-table-column prop="name" label="应用名称" min-width="140" />
        <el-table-column prop="code" label="编码" min-width="140" />
        <el-table-column label="Access Key" min-width="200">
          <template #default="{ row }">
            <div v-if="row.accessKey" class="ak-cell">
              <code>{{ row.accessKey }}</code>
              <el-button type="primary" link size="small" @click="copyText(row.accessKey)">
                复制
              </el-button>
            </div>
            <span v-else class="text-muted">未生成</span>
          </template>
        </el-table-column>
        <el-table-column label="调用范围" min-width="160">
          <template #default="{ row }">
            <el-tag
              v-for="s in parseScopes(row.allowedScopes)"
              :key="s"
              size="small"
              effect="plain"
              class="scope-tag"
            >
              {{ scopeLabel(s) }}
            </el-tag>
            <span v-if="parseScopes(row.allowedScopes).length === 0" class="text-muted">全部</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small" round>
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button type="primary" link size="small" @click="handleGenerateAkSk(row)">
              {{ row.accessKey ? '重新生成 AK/SK' : '生成 AK/SK' }}
            </el-button>
            <el-button
              v-if="row.accessKey"
              type="warning"
              link
              size="small"
              @click="handleRegenerateSk(row)"
            >
              轮换 SK
            </el-button>
            <el-button
              :type="row.status === 'ENABLED' ? 'danger' : 'success'"
              link
              size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button type="danger" link size="small" @click="handleDeleteApp(row)">
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- AK/SK 生成结果弹窗 -->
    <el-dialog v-model="akskDialogVisible" title="AK/SK 凭证" width="520px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #title>
          Secret Key 仅显示一次，请立即复制保存！关闭后将无法再次查看。
        </template>
      </el-alert>
      <div class="aksk-field">
        <div class="aksk-field__label">Access Key</div>
        <el-input :model-value="akskResult?.accessKey" readonly>
          <template #append>
            <el-button @click="copyText(akskResult?.accessKey || '')">复制</el-button>
          </template>
        </el-input>
      </div>
      <div class="aksk-field">
        <div class="aksk-field__label">Secret Key</div>
        <el-input :model-value="akskResult?.secretKey" readonly>
          <template #append>
            <el-button @click="copyText(akskResult?.secretKey || '')">复制</el-button>
          </template>
        </el-input>
      </div>
      <template #footer>
        <el-button type="primary" @click="akskDialogVisible = false">我已保存，关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑应用弹窗 -->
    <el-dialog
      v-model="appDialogVisible"
      :title="appForm.id ? '编辑应用' : '新增应用'"
      width="500px"
    >
      <el-form :model="appForm" label-width="80px" style="padding: 0 4px">
        <el-form-item label="编码" required>
          <el-input
            v-model="appForm.code"
            placeholder="如 wanxiang-monitor"
            :disabled="!!appForm.id"
            maxlength="64"
          />
          <p class="form-hint">小写字母开头，仅含小写字母、数字、下划线或连字符</p>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="appForm.name" placeholder="如 万象监测平台" maxlength="128" />
        </el-form-item>
        <el-form-item label="调用范围">
          <el-checkbox-group v-model="appForm.scopes" class="scope-checkbox-group">
            <el-checkbox v-for="opt in scopeOptions" :key="opt.value" :label="opt.value">
              <div class="scope-option">
                <span class="scope-option__label">{{ opt.label }}</span>
                <span class="scope-option__desc">{{ opt.desc }}</span>
              </div>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="appForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="appSaving" @click="saveApp">保存</el-button>
      </template>
    </el-dialog>

    <!-- API 分类列表 -->
    <section class="api-section">
      <header class="section-header">
        <h2><el-icon><Grid /></el-icon> 能力分类</h2>
        <p class="section-header__desc">按业务域分类展示所有对外开放接口</p>
      </header>

      <el-tabs v-model="activeCategory" class="api-tabs" type="border-card">
        <el-tab-pane
          v-for="cat in categories"
          :key="cat.key"
          :name="cat.key"
        >
          <template #label>
            <div class="tab-label">
              <el-icon :size="14"><component :is="cat.icon" /></el-icon>
              <span>{{ cat.title }}</span>
              <el-tag size="small" round type="info">{{ cat.endpoints.length }}</el-tag>
            </div>
          </template>

          <div class="api-category-content">
            <p class="api-category-desc">{{ cat.description }}</p>
            <p v-if="cat.basePath" class="api-category-base">
              <el-icon><Position /></el-icon> 基础路径：<code>{{ cat.basePath }}</code>
            </p>

            <div class="api-list">
              <div
                v-for="(api, idx) in cat.endpoints"
                :key="idx"
                class="api-item"
              >
                <div class="api-item__header">
                  <el-tag
                    :type="methodTagType(api.method)"
                    size="small"
                    effect="dark"
                    class="api-item__method"
                  >
                    {{ api.method }}
                  </el-tag>
                  <code class="api-item__path">{{ api.path }}</code>
                  <el-tag
                    v-if="api.requiresAuth"
                    size="small"
                    type="warning"
                    effect="plain"
                    class="api-item__auth"
                  >
                    需认证
                  </el-tag>
                  <el-tag
                    v-else
                    size="small"
                    type="success"
                    effect="plain"
                    class="api-item__auth"
                  >
                    公开
                  </el-tag>
                </div>
                <p class="api-item__summary">{{ api.summary }}</p>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- 他方接入流程 -->
    <section class="flow-section">
      <header class="section-header">
        <h2><el-icon><Guide /></el-icon> 他方接入流程</h2>
      </header>
      <div class="flow-steps">
        <div class="flow-step">
          <div class="flow-step__number">1</div>
          <div class="flow-step__content">
            <h4>申请接入</h4>
            <p>联系平台管理员，获取 API Key 或 SSO 对接密钥对。</p>
          </div>
        </div>
        <div class="flow-step">
          <div class="flow-step__number">2</div>
          <div class="flow-step__content">
            <h4>接口联调</h4>
            <p>使用 Swagger UI（开发工具 → 接口文档）在线调试，确认请求参数与返回格式。</p>
          </div>
        </div>
        <div class="flow-step">
          <div class="flow-step__number">3</div>
          <div class="flow-step__content">
            <h4>集成上线</h4>
            <p>完成业务集成后，通过运维监控确认接口调用正常即可上线运行。</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Connection,
  Key,
  Unlock,
  Link,
  Monitor,
  Grid,
  Position,
  Guide,
  ChatLineRound,
  Cpu,
  OfficeBuilding,
  Promotion,
  Plus,
} from '@element-plus/icons-vue'
import {
  listOpenAppsApi,
  generateAkSkApi,
  regenerateSkApi,
  updateOpenAppStatusApi,
  createOpenAppApi,
  updateOpenAppApi,
  deleteOpenAppApi,
} from '@/api/openApp'
import type { OpenAppVO, GenerateAkSkResult } from '@/types/openApp'

interface ApiEndpoint {
  method: string
  path: string
  summary: string
  requiresAuth: boolean
}

interface ApiCategory {
  key: string
  title: string
  icon: typeof Connection
  description: string
  basePath: string
  endpoints: ApiEndpoint[]
}

const activeCategory = ref('ai-chat')

const categories: ApiCategory[] = [
  {
    key: 'ai-chat',
    title: 'AI 智能对话',
    icon: ChatLineRound,
    description: '对外开放的 AI 对话能力，支持同步调用与 SSE 流式输出，他方可快速集成智能对话功能。',
    basePath: '/open/v1',
    endpoints: [
      { method: 'GET', path: '/open/v1/agents', summary: '获取可用智能体列表，外部简报请先选 Agent 再调对话接口', requiresAuth: true },
      { method: 'POST', path: '/open/v1/chat', summary: '同步对话接口，适用于后台任务、简报生成等场景', requiresAuth: true },
      { method: 'POST', path: '/open/v1/chat/stream', summary: '流式对话接口（SSE），适用于实时对话交互', requiresAuth: true },
    ],
  },
  {
    key: 'ai-qa',
    title: '知识问答',
    icon: Cpu,
    description: '基于知识库文档的智能问答能力（RAG），他方可将自有知识库接入平台进行文档问答。',
    basePath: '/open/v1/knowledges/qa',
    endpoints: [
      { method: 'POST', path: '/open/v1/knowledges/qa/stream', summary: '文档智能问答（SSE 流式），基于上传文档进行 RAG 检索与回答', requiresAuth: true },
    ],
  },
  {
    key: 'ai-kg',
    title: '知识图谱',
    icon: OfficeBuilding,
    description: '支持他方系统推送工程节点、遥测站、告警等数据，实现知识图谱的同步与更新。',
    basePath: '/open/v1/kg',
    endpoints: [
      { method: 'POST', path: '/open/v1/kg/upsert', summary: '接收万象等系统推送的工程/遥测站/告警节点，实现知识图谱数据同步', requiresAuth: true },
    ],
  },
  {
    key: 'mcp',
    title: 'MCP 服务中枢',
    icon: Connection,
    description: 'MCP（Model Context Protocol）服务中枢，支持对外提供 MCP 服务及接入他方 MCP 工具。',
    basePath: '/api/v1/mcp',
    endpoints: [
      { method: 'GET', path: '/api/v1/mcp/overview', summary: 'MCP 中枢概览，展示客户端数量、上游数量等统计信息', requiresAuth: true },
      { method: 'GET', path: '/api/v1/mcp/catalog', summary: '对外能力与 Tool 目录，展示可用的 MCP 能力清单', requiresAuth: true },
      { method: 'GET', path: '/api/v1/mcp/clients', summary: '获取对外 MCP Client 列表', requiresAuth: true },
      { method: 'POST', path: '/api/v1/mcp/clients', summary: '创建对外 MCP Client（apiKey 仅返回一次）', requiresAuth: true },
      { method: 'PUT', path: '/api/v1/mcp/clients/{id}', summary: '更新对外 MCP Client 配置', requiresAuth: true },
      { method: 'POST', path: '/api/v1/mcp/clients/{id}/rotate-key', summary: '轮换 API Key', requiresAuth: true },
      { method: 'DELETE', path: '/api/v1/mcp/clients/{id}', summary: '删除对外 MCP Client', requiresAuth: true },
    ],
  },
  {
    key: 'auth',
    title: '认证与集成',
    icon: Key,
    description: '用户认证、SSO 单点登录换票等接口，供他方系统集成登录能力使用。',
    basePath: '/api/v1/auth',
    endpoints: [
      { method: 'GET', path: '/api/v1/auth/public-key', summary: '获取 RSA 公钥，用于前端加密用户名和密码', requiresAuth: false },
      { method: 'GET', path: '/api/v1/auth/captcha', summary: '获取滑动验证码（背景图、滑块图及验证码标识）', requiresAuth: false },
      { method: 'POST', path: '/api/v1/auth/captcha/verify', summary: '校验滑动验证码，成功后返回 captchaToken', requiresAuth: false },
      { method: 'POST', path: '/api/v1/auth/login', summary: '用户登录，根据加密凭据获取 JWT Token', requiresAuth: false },
      { method: 'POST', path: '/api/v1/auth/logout', summary: '退出登录，将当前 Token 加入黑名单', requiresAuth: true },
      { method: 'POST', path: '/api/v1/auth/sso/exchange', summary: 'SSO 换票，校验伙伴签发的短期 Ticket 并签发业务 JWT', requiresAuth: false },
    ],
  },
  {
    key: 'system',
    title: '系统管理',
    icon: Monitor,
    description: '系统运行状态、公告推送等接口，供他方系统获取平台运行信息与实时通知。',
    basePath: '/api/v1',
    endpoints: [
      { method: 'GET', path: '/api/v1/system/health', summary: '系统健康检查（公开接口，无需认证）', requiresAuth: false },
      { method: 'GET', path: '/api/v1/system/status', summary: '系统运行状态详情（需认证）', requiresAuth: true },
      { method: 'GET', path: '/api/v1/announcements/stream', summary: '公告 SSE 实时推送流', requiresAuth: true },
      { method: 'GET', path: '/api/v1/announcements/unread-count', summary: '获取未读公告数量', requiresAuth: true },
      { method: 'GET', path: '/api/v1/announcements/recent', summary: '获取最近公告列表', requiresAuth: true },
      { method: 'GET', path: '/api/v1/announcements/published/page', summary: '已发布公告分页查询', requiresAuth: true },
    ],
  },
  {
    key: 'manage',
    title: '数据管理',
    icon: Promotion,
    description: '用户、角色、菜单、单位等基础数据的增删改查接口，供他方系统进行数据同步。',
    basePath: '/api/v1',
    endpoints: [
      { method: 'GET', path: '/api/v1/users/page', summary: '用户分页查询', requiresAuth: true },
      { method: 'GET', path: '/api/v1/roles/page', summary: '角色分页查询', requiresAuth: true },
      { method: 'GET', path: '/api/v1/roles/list', summary: '查询全部启用角色（下拉选择用）', requiresAuth: true },
      { method: 'GET', path: '/api/v1/menus/tree', summary: '获取菜单树结构', requiresAuth: true },
      { method: 'GET', path: '/api/v1/menus/current-user', summary: '获取当前用户菜单', requiresAuth: true },
      { method: 'GET', path: '/api/v1/menus/current-user/permissions', summary: '获取当前用户权限列表', requiresAuth: true },
      { method: 'GET', path: '/api/v1/units/tree', summary: '获取单位树结构', requiresAuth: true },
      { method: 'GET', path: '/api/v1/operation-logs/page', summary: '操作日志分页查询', requiresAuth: true },
    ],
  },
]

const totalApiCount = computed(() =>
  categories.reduce((sum, cat) => sum + cat.endpoints.length, 0),
)

const openApiCount = computed(() =>
  categories
    .filter(cat => cat.basePath.startsWith('/open'))
    .reduce((sum, cat) => sum + cat.endpoints.length, 0),
)

function methodTagType(method: string) {
  const map: Record<string, string> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    DELETE: 'danger',
  }
  return map[method] || 'info'
}

/* ── 凭证管理 ─────────────────────────────────────── */

const apps = ref<OpenAppVO[]>([])
const appsLoading = ref(false)

const akskDialogVisible = ref(false)
const akskResult = ref<GenerateAkSkResult | null>(null)

const scopeOptions = [
  { value: 'chat', label: 'AI 智能对话', desc: '/open/v1/chat, /open/v1/agents' },
  { value: 'knowledges', label: '知识问答', desc: '/open/v1/knowledges' },
  { value: 'kg', label: '知识图谱', desc: '/open/v1/kg' },
]

const fetchApps = async () => {
  appsLoading.value = true
  try {
    apps.value = (await listOpenAppsApi()) || []
  } catch {
    apps.value = []
  } finally {
    appsLoading.value = false
  }
}

onMounted(fetchApps)

const parseScopes = (raw: string | null): string[] => {
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

const scopeLabel = (s: string): string => {
  const found = scopeOptions.find((o) => o.value === s)
  return found ? found.label : s
}

const copyText = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const handleGenerateAkSk = async (row: OpenAppVO) => {
  const action = row.accessKey ? '重新生成' : '生成'
  await ElMessageBox.confirm(
    `${action} AK/SK 后，${row.accessKey ? '旧 SK 将立即失效，' : ''}请确认操作。`,
    '确认',
    { type: 'warning' },
  )
  const result = await generateAkSkApi(row.id)
  akskResult.value = result
  akskDialogVisible.value = true
  await fetchApps()
}

const handleRegenerateSk = async (row: OpenAppVO) => {
  await ElMessageBox.confirm(
    '轮换 SK 后，旧 SK 将立即失效，调用方需使用新 SK 重新签名。确认操作？',
    '确认轮换',
    { type: 'warning' },
  )
  const result = await regenerateSkApi(row.id)
  akskResult.value = result
  akskDialogVisible.value = true
}

const handleToggleStatus = async (row: OpenAppVO) => {
  const newStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const label = newStatus === 'ENABLED' ? '启用' : '停用'
  await ElMessageBox.confirm(`确认${label}应用「${row.name}」？`, '确认', { type: 'warning' })
  await updateOpenAppStatusApi(row.id, newStatus)
  ElMessage.success(`已${label}`)
  await fetchApps()
}

/* ── 新增/编辑/移除应用 ──────────────────────────────── */

const appDialogVisible = ref(false)
const appSaving = ref(false)
const appForm = reactive({
  id: null as number | null,
  code: '',
  name: '',
  remark: '',
  scopes: [] as string[],
})

const resetAppForm = () => {
  appForm.id = null
  appForm.code = ''
  appForm.name = ''
  appForm.remark = ''
  appForm.scopes = []
}

const openCreateDialog = () => {
  resetAppForm()
  appDialogVisible.value = true
}

const openEditDialog = (row: OpenAppVO) => {
  appForm.id = row.id
  appForm.code = row.code
  appForm.name = row.name
  appForm.remark = row.remark || ''
  appForm.scopes = [...parseScopes(row.allowedScopes)]
  appDialogVisible.value = true
}

const saveApp = async () => {
  if (!appForm.code.trim() || !appForm.name.trim()) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  appSaving.value = true
  try {
    const data = {
      code: appForm.code.trim(),
      name: appForm.name.trim(),
      remark: appForm.remark,
      allowedScopes: appForm.scopes,
    }
    if (appForm.id) {
      await updateOpenAppApi(appForm.id, data)
      ElMessage.success('应用已更新')
    } else {
      await createOpenAppApi(data)
      ElMessage.success('应用已创建')
    }
    appDialogVisible.value = false
    await fetchApps()
  } finally {
    appSaving.value = false
  }
}

const handleDeleteApp = async (row: OpenAppVO) => {
  await ElMessageBox.confirm(
    `确认移除应用「${row.name}」？移除后其 AK/SK 凭证将一并失效，不可恢复。`,
    '确认移除',
    { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' },
  )
  await deleteOpenAppApi(row.id)
  ElMessage.success('已移除')
  await fetchApps()
}
</script>

<style scoped lang="scss">
.open-api-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: var(--app-content-padding, 16px);
  min-height: 100%;
  box-sizing: border-box;
  background: var(--app-content-bg, #f6f8fa);
}

/* ── Header ── */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
  border-radius: var(--app-radius-lg, 10px);
  background: linear-gradient(135deg, #409eff 0%, #6366f1 100%);
  color: #fff;
}

.page-header__main {
  flex: 1;
}

.page-header__title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
}

.page-header__desc {
  margin: 0;
  font-size: 14px;
  opacity: 0.88;
}

.page-header__stats {
  display: flex;
  gap: 16px;
  flex-shrink: 0;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 80px;
  padding: 12px 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(4px);
}

.stat-card__value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-card__label {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 2px;
}

/* ── Section common ── */
.section-header {
  margin-bottom: 16px;

  h2 {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 16px;
    font-weight: 600;
    color: var(--app-text-primary, #1f2328);
    margin: 0;
  }

  &__desc {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--app-text-secondary, #656d76);
  }
}

/* ── Guide cards ── */
.guide-section {
  padding: 20px 24px;
  border-radius: var(--app-radius-lg, 10px);
  background: var(--app-surface-bg, #fff);
  border: 1px solid var(--app-border-color, #d0d7de);
}

.guide-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.guide-card {
  display: flex;
  gap: 14px;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  background: var(--app-surface-bg, #fff);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }
}

.guide-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  flex-shrink: 0;

  &--auth {
    background: #fef3cd;
    color: #b8860b;
  }

  &--open {
    background: #d1fae5;
    color: #059669;
  }

  &--sso {
    background: #dbeafe;
    color: #2563eb;
  }

  &--base {
    background: #e0e7ff;
    color: #4f46e5;
  }
}

.guide-card__body {
  flex: 1;
  min-width: 0;

  h3 {
    margin: 0 0 6px;
    font-size: 14px;
    font-weight: 600;
    color: var(--app-text-primary, #1f2328);
  }

  p {
    margin: 4px 0;
    font-size: 13px;
    color: var(--app-text-secondary, #656d76);
    line-height: 1.6;
  }
}

.code-block {
  margin: 6px 0;
  padding: 6px 10px;
  border-radius: 4px;
  background: #f6f8fa;
  border: 1px solid #e5e7eb;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: #1f2328;
  overflow-x: auto;
}

/* ── API tabs ── */
.api-section {
  padding: 20px 24px;
  border-radius: var(--app-radius-lg, 10px);
  background: var(--app-surface-bg, #fff);
  border: 1px solid var(--app-border-color, #d0d7de);
}

.api-tabs {
  border: none;
  box-shadow: none;

  :deep(.el-tabs__header) {
    background: transparent;
    border-bottom-color: var(--app-border-color, #e5e7eb);
  }

  :deep(.el-tabs__content) {
    padding: 16px 0 0;
  }
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.api-category-content {
  min-height: 200px;
}

.api-category-desc {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--app-text-secondary, #656d76);
  line-height: 1.6;
}

.api-category-base {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--app-text-secondary, #656d76);

  code {
    padding: 2px 6px;
    border-radius: 3px;
    background: #f0f2f5;
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 12px;
    color: #409eff;
  }
}

.api-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.api-item {
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid var(--app-border-color, #e5e7eb);
  background: var(--app-surface-bg, #fff);
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: #409eff;
    box-shadow: 0 1px 4px rgba(64, 158, 255, 0.1);
  }
}

.api-item__header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.api-item__method {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  min-width: 52px;
  text-align: center;
}

.api-item__path {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  color: var(--app-text-primary, #1f2328);
  word-break: break-all;
}

.api-item__auth {
  margin-left: auto;
}

.api-item__summary {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--app-text-secondary, #656d76);
  line-height: 1.5;
  padding-left: 62px;
}

/* ── Flow section ── */
.flow-section {
  padding: 20px 24px;
  border-radius: var(--app-radius-lg, 10px);
  background: var(--app-surface-bg, #fff);
  border: 1px solid var(--app-border-color, #d0d7de);
}

.flow-steps {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.flow-step {
  flex: 1;
  min-width: 200px;
  display: flex;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
  background: #f6f8fa;
  border: 1px solid var(--app-border-color, #e5e7eb);
}

.flow-step__number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #6366f1);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.flow-step__content {
  flex: 1;

  h4 {
    margin: 0 0 4px;
    font-size: 14px;
    font-weight: 600;
    color: var(--app-text-primary, #1f2328);
  }

  p {
    margin: 0;
    font-size: 13px;
    color: var(--app-text-secondary, #656d76);
    line-height: 1.5;
  }
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }

  .page-header__stats {
    width: 100%;
    justify-content: center;
  }

  .guide-cards {
    grid-template-columns: 1fr;
  }

  .flow-steps {
    flex-direction: column;
  }

  .api-item__summary {
    padding-left: 0;
  }
}

/* ── Credential management ── */
.cred-section {
  padding: 20px 24px;
  border-radius: var(--app-radius-lg, 10px);
  background: var(--app-surface-bg, #fff);
  border: 1px solid var(--app-border-color, #d0d7de);
}

.ak-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  code {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 12px;
    color: #409eff;
    background: #f0f2f5;
    padding: 2px 6px;
    border-radius: 3px;
    word-break: break-all;
  }
}

.text-muted {
  color: var(--app-text-secondary, #656d76);
  font-size: 13px;
}

.scope-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.aksk-field {
  margin-bottom: 14px;

  &__label {
    font-size: 13px;
    font-weight: 600;
    color: var(--app-text-primary, #1f2328);
    margin-bottom: 6px;
  }
}

.scope-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.scope-option {
  &__label {
    font-size: 14px;
    font-weight: 500;
    color: var(--app-text-primary, #1f2328);
  }

  &__desc {
    display: block;
    font-size: 12px;
    color: var(--app-text-secondary, #656d76);
    margin-top: 2px;
  }
}

.form-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--app-text-secondary, #656d76);
  line-height: 1.4;
}

.cred-section .section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
</style>
