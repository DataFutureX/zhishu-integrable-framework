<template>
  <ListPageShell
    :loading="loading"
    :show-hero="true"
    hero-title="MCPs"
    hero-eyebrow="智能中心"
    :hero-eyebrow-icon="Link"
    :hero-metrics="heroMetrics"
  >
    <template #heroDescription>
      对外提供只读监测 MCP（<code>/mcp</code>），并接入他方 MCP 供智能中心 Agent 调用。接入工具不会再对外转发。
    </template>
    <template #heroActions>
      <el-button
        v-permission="PERMISSIONS.AI_MCP_EDIT"
        type="primary"
        size="small"
        :icon="Plus"
        @click="openCreateClient"
      >
        签发 Client
      </el-button>
      <el-button
        v-permission="PERMISSIONS.AI_MCP_EDIT"
        size="small"
        :icon="Plus"
        @click="openCreateUpstream"
      >
        登记上游
      </el-button>
      <el-button size="small" :icon="Refresh" :loading="loading" @click="reload">刷新</el-button>
    </template>

    <div class="mcp-home">
      <el-alert
        v-if="!overview.cryptoConfigured"
        class="mcp-alert"
        type="warning"
        :closable="false"
        show-icon
        title="未配置 WANXIANG_MCP_CRYPTO_KEY，上游 Authorization 将明文写入 ai_mcp_upstream。"
      />

      <div class="mcp-toolbar">
        <el-input
          v-model="keyword"
          class="mcp-toolbar__search"
          clearable
          :placeholder="searchPlaceholder"
          :prefix-icon="Search"
        />
        <el-radio-group v-model="tab" size="default">
          <el-radio-button value="out">对外 Client</el-radio-button>
          <el-radio-button value="in">接入上游</el-radio-button>
          <el-radio-button value="log">调用审计</el-radio-button>
        </el-radio-group>
        <el-radio-group v-if="tab === 'log'" v-model="logDirection" size="default" @change="loadLogs">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="OUT">对外</el-radio-button>
          <el-radio-button value="IN">接入</el-radio-button>
        </el-radio-group>
      </div>

      <el-empty
        v-if="tab === 'out' && !filteredClients.length"
        class="mcp-empty"
        :description="keyword ? '没有匹配的 Client' : '暂无对外 Client，请先签发'"
        :image-size="88"
      >
        <el-button
          v-if="!keyword"
          v-permission="PERMISSIONS.AI_MCP_EDIT"
          type="primary"
          :icon="Plus"
          @click="openCreateClient"
        >
          签发 Client
        </el-button>
      </el-empty>

      <div v-else-if="tab === 'out'" class="mcp-grid">
        <article
          v-for="row in filteredClients"
          :key="row.id"
          class="mcp-card mcp-card--out mcp-card--clickable"
          :class="{
            'is-enabled': row.status === 'ENABLED',
            'is-disabled': row.status !== 'ENABLED',
          }"
          role="button"
          tabindex="0"
          @click="openClientAccess(row)"
          @keydown.enter.prevent="openClientAccess(row)"
        >
          <div class="mcp-card__rail" aria-hidden="true" />
          <div class="mcp-card__body">
            <header class="mcp-card__head">
              <div class="mcp-card__avatar">
                <el-icon :size="22"><Key /></el-icon>
                <span v-if="row.status === 'ENABLED'" class="mcp-card__pulse" />
              </div>
              <div class="mcp-card__titles">
                <div class="mcp-card__name-row">
                  <h3 class="mcp-card__name" :title="row.name">{{ row.name }}</h3>
                </div>
                <div class="mcp-card__id-row">
                  <code class="mcp-card__code">{{ row.keyPrefix }}</code>
                  <span class="mcp-card__meta">用户 {{ row.boundUserId }}</span>
                </div>
              </div>
              <el-tag
                size="small"
                :type="row.status === 'ENABLED' ? 'success' : 'info'"
                effect="plain"
                round
              >
                {{ row.status === 'ENABLED' ? '启用' : '停用' }}
              </el-tag>
            </header>

            <p class="mcp-card__desc" :title="row.remark || ''">
              {{ row.remark || row.boundUsername || '他方 Agent 凭 MCP Key 调用本平台只读监测 Tool。' }}
            </p>

            <div class="mcp-card__specs">
              <span class="mcp-card__spec">
                RPM {{ row.rpmLimit }}
                <el-tooltip placement="top" popper-class="mcp-rpm-hint-popper">
                  <template #content>{{ rpmHint }}</template>
                  <span class="rpm-help" @click.stop>
                    <el-icon :size="12"><QuestionFilled /></el-icon>
                  </span>
                </el-tooltip>
              </span>
              <span class="mcp-card__spec mcp-card__spec--muted">
                {{ (row.capabilities || []).length || 11 }} 项能力
              </span>
            </div>

            <div class="mcp-card__skills">
              <span
                v-for="cap in row.capabilities || []"
                :key="cap"
                class="mcp-skill"
              >
                {{ capLabel(cap) }}
              </span>
              <span v-if="!(row.capabilities || []).length" class="mcp-card__skills-empty">
                默认 20 个只读 Tool
              </span>
            </div>

            <p class="mcp-card__hint">点击查看他方接入方式</p>

            <footer class="mcp-card__foot" @click.stop>
              <el-button type="primary" size="small" @click="openClientAccess(row)">
                接入方式
              </el-button>
              <div class="mcp-card__links">
                <el-button
                  v-permission="PERMISSIONS.AI_MCP_EDIT"
                  type="primary"
                  link
                  size="small"
                  @click="openEditClient(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="PERMISSIONS.AI_MCP_EDIT"
                  type="primary"
                  link
                  size="small"
                  @click="rotateKey(row)"
                >
                  轮换 Key
                </el-button>
                <el-button
                  v-permission="PERMISSIONS.AI_MCP_EDIT"
                  type="danger"
                  link
                  size="small"
                  @click="removeClient(row)"
                >
                  删除
                </el-button>
              </div>
            </footer>
          </div>
        </article>
      </div>

      <el-empty
        v-else-if="tab === 'in' && !filteredUpstreams.length"
        class="mcp-empty"
        :description="keyword ? '没有匹配的上游' : '暂无上游 MCP，请先登记'"
        :image-size="88"
      >
        <el-button
          v-if="!keyword"
          v-permission="PERMISSIONS.AI_MCP_EDIT"
          type="primary"
          :icon="Plus"
          @click="openCreateUpstream"
        >
          登记上游
        </el-button>
      </el-empty>

      <div v-else-if="tab === 'in'" class="mcp-grid">
        <article
          v-for="row in filteredUpstreams"
          :key="row.id"
          class="mcp-card mcp-card--in"
          :class="{
            'is-enabled': row.status === 'ENABLED',
            'is-disabled': row.status !== 'ENABLED',
          }"
        >
          <div class="mcp-card__rail" aria-hidden="true" />
          <div class="mcp-card__body">
            <header class="mcp-card__head">
              <div class="mcp-card__avatar">
                <el-icon :size="22"><Connection /></el-icon>
                <span v-if="row.healthStatus === 'UP'" class="mcp-card__pulse" />
              </div>
              <div class="mcp-card__titles">
                <div class="mcp-card__name-row">
                  <h3 class="mcp-card__name" :title="row.name">{{ row.name }}</h3>
                </div>
                <div class="mcp-card__id-row">
                  <code class="mcp-card__code">{{ row.code }}</code>
                  <span class="mcp-card__meta">{{ protocolLabel(row.protocol) }}</span>
                </div>
              </div>
              <el-tag
                size="small"
                :type="healthTag(row.healthStatus)"
                effect="plain"
                round
              >
                {{ row.healthStatus || 'UNKNOWN' }}
              </el-tag>
            </header>

            <p class="mcp-card__desc" :title="row.baseUrl">
              {{ row.baseUrl }}{{ row.endpoint || '/mcp' }}
            </p>

            <div class="mcp-card__specs">
              <span class="mcp-card__spec">{{ row.toolCount }} Tools</span>
              <span class="mcp-card__spec mcp-card__spec--muted">
                {{ row.status === 'ENABLED' ? '已启用' : '已停用' }}
              </span>
              <span v-if="row.hasAuthHeader" class="mcp-card__spec">已配置凭证</span>
            </div>

            <p v-if="row.healthMessage" class="mcp-card__health" :title="row.healthMessage">
              {{ row.healthMessage }}
            </p>

            <footer class="mcp-card__foot">
              <el-button
                v-permission="PERMISSIONS.AI_MCP_EDIT"
                type="primary"
                size="small"
                @click="probe(row)"
              >
                探活
              </el-button>
              <div class="mcp-card__links">
                <el-button type="primary" link size="small" @click="showTools(row)">
                  Tools
                </el-button>
                <el-button
                  v-permission="PERMISSIONS.AI_MCP_EDIT"
                  type="primary"
                  link
                  size="small"
                  @click="openEditUpstream(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="PERMISSIONS.AI_MCP_EDIT"
                  type="danger"
                  link
                  size="small"
                  @click="removeUpstream(row)"
                >
                  删除
                </el-button>
              </div>
            </footer>
          </div>
        </article>
      </div>

      <el-empty
        v-else-if="tab === 'log' && !filteredLogs.length"
        class="mcp-empty"
        description="暂无调用记录"
        :image-size="88"
      />

      <el-table v-else-if="tab === 'log'" :data="filteredLogs" stripe class="mcp-log-table">
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="direction" label="方向" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.direction === 'OUT' ? 'primary' : 'success'" effect="plain">
              {{ row.direction === 'OUT' ? '对外' : '接入' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="toolName" label="Tool" min-width="200" show-overflow-tooltip />
        <el-table-column prop="success" label="结果" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'" effect="plain">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时 ms" width="100" />
        <el-table-column prop="errorMessage" label="错误" min-width="180" show-overflow-tooltip />
      </el-table>
    </div>

    <template #extra>
      <el-dialog
        v-model="clientVisible"
        :title="clientEditingId ? '编辑 Client' : '签发 Client'"
        width="640px"
        :close-on-click-modal="false"
      >
        <el-form label-width="110px" :model="clientForm">
          <el-form-item label="名称" required>
            <el-input v-model="clientForm.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="绑定用户 ID" required>
            <el-input-number v-model="clientForm.boundUserId" :min="1" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="clientForm.boundUsername" />
          </el-form-item>
          <el-form-item required>
            <template #label>
              <span class="rpm-label">
                RPM
                <el-tooltip placement="top" popper-class="mcp-rpm-hint-popper">
                  <template #content>{{ rpmHint }}</template>
                  <span class="rpm-help">
                    <el-icon :size="13"><QuestionFilled /></el-icon>
                  </span>
                </el-tooltip>
              </span>
            </template>
            <el-input-number v-model="clientForm.rpmLimit" :min="1" :max="600" />
          </el-form-item>
          <el-form-item label="开放能力">
            <el-checkbox-group v-model="clientForm.capabilities" class="cap-group">
              <el-checkbox v-for="cap in outboundCaps" :key="cap.code" :label="cap.code">
                {{ cap.label }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item v-if="clientEditingId" label="状态">
            <el-select v-model="clientForm.status" style="width: 100%">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="clientForm.remark" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="clientVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveClient">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="upstreamVisible"
        :title="upstreamEditingId ? '编辑上游' : '登记上游'"
        width="680px"
        :close-on-click-modal="false"
      >
        <el-form label-width="110px" :model="upstreamForm">
          <el-form-item label="快捷粘贴">
            <el-input
              v-model="upstreamSnippet"
              type="textarea"
              :rows="5"
              :placeholder="snippetPlaceholder"
              @paste="onUpstreamSnippetPaste"
              @input="onUpstreamSnippetChange"
              @change="onUpstreamSnippetChange"
            />
            <p v-if="snippetHint" class="curl-hint curl-hint--ok">已识别 {{ snippetHint }}</p>
            <p v-else class="curl-hint">支持 curl、纯 URL；解析后自动填充协议、地址与鉴权。</p>
          </el-form-item>
          <el-form-item label="编码" required>
            <el-input v-model="upstreamForm.code" :disabled="!!upstreamEditingId" placeholder="weather" />
          </el-form-item>
          <el-form-item label="名称" required>
            <el-input v-model="upstreamForm.name" />
          </el-form-item>
          <el-form-item label="协议">
            <el-select v-model="upstreamForm.protocol" style="width: 100%">
              <el-option label="Streamable HTTP" value="STREAMABLE_HTTP" />
              <el-option label="SSE" value="SSE" />
            </el-select>
          </el-form-item>
          <el-form-item label="Base URL" required>
            <el-input
              v-model="upstreamForm.baseUrl"
              placeholder="https://partner.example.com"
              @paste="onBaseUrlPaste"
              @change="onBaseUrlChange"
            />
          </el-form-item>
          <el-form-item label="Endpoint">
            <el-input v-model="upstreamForm.endpoint" placeholder="/mcp" />
          </el-form-item>
          <el-form-item label="Authorization">
            <el-input v-model="upstreamForm.authHeader" placeholder="留空表示不改；Bearer xxx" />
          </el-form-item>
          <el-form-item label="超时 ms">
            <el-input-number v-model="upstreamForm.requestTimeoutMs" :min="1000" :step="1000" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="upstreamForm.status" style="width: 100%">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="upstreamVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveUpstream">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="accessVisible"
        :title="accessClient ? `接入方式 · ${accessClient.name}` : '他方接入方式'"
        width="720px"
        class="mcp-access-dialog"
        :close-on-click-modal="false"
      >
        <template v-if="accessClient">
          <el-alert
            v-if="accessJustRevealed"
            type="warning"
            :closable="false"
            show-icon
            title="完整 API Key 只显示这一次，请立刻复制。关闭后无法再查看，只能轮换。"
          />
          <el-alert
            v-else-if="!hasFullAccessKey"
            type="info"
            :closable="false"
            show-icon
            title="完整 Key 仅在签发或轮换时显示一次。下方配置里的密钥需替换为当时复制的完整值；遗失请轮换。"
          />

          <el-descriptions :column="2" border class="access-meta" size="small">
            <el-descriptions-item label="协议">Streamable HTTP</el-descriptions-item>
            <el-descriptions-item label="状态">
              {{ accessClient.status === 'ENABLED' ? '启用' : '停用' }}
            </el-descriptions-item>
            <el-descriptions-item label="Key 前缀">
              <code>{{ accessClient.keyPrefix }}</code>
            </el-descriptions-item>
            <el-descriptions-item>
              <template #label>
                <span class="rpm-label">
                  RPM
                  <el-tooltip placement="top" popper-class="mcp-rpm-hint-popper">
                    <template #content>{{ rpmHint }}</template>
                    <span class="rpm-help">
                      <el-icon :size="12"><QuestionFilled /></el-icon>
                    </span>
                  </el-tooltip>
                </span>
              </template>
              {{ accessClient.rpmLimit }}
            </el-descriptions-item>
            <el-descriptions-item label="开放能力" :span="2">
              {{
                (accessClient.capabilities || []).map(capLabel).join('、') || '默认只读监测 Tool'
              }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="access-field">
            <div class="access-field__label">接入地址</div>
            <el-input v-model="publicMcpUrl">
              <template #append>
                <el-button @click="copyText(publicMcpUrl)">复制</el-button>
              </template>
            </el-input>
            <p class="access-field__hint">他方按公网/内网可达地址填写；开发环境默认本机 8180。</p>
          </div>

          <div v-if="hasFullAccessKey" class="access-field">
            <div class="access-field__label">API Key</div>
            <el-input :model-value="plainKey" readonly>
              <template #append>
                <el-button @click="copyText(plainKey)">复制 Key</el-button>
              </template>
            </el-input>
          </div>

          <div class="access-field">
            <div class="access-field__label">Authorization</div>
            <el-input :model-value="accessAuthHeader" readonly>
              <template #append>
                <el-button @click="copyText(accessAuthHeader)">复制</el-button>
              </template>
            </el-input>
          </div>

          <el-tabs v-model="accessTab" class="access-tabs">
            <el-tab-pane label="Cursor" name="cursor">
              <p class="access-field__hint">写入 Cursor 的 <code>mcp.json</code> → <code>mcpServers</code>。</p>
              <el-input :model-value="cursorSnippet" type="textarea" :rows="11" readonly />
              <el-button class="copy-snippet" @click="copyText(cursorSnippet)">复制 Cursor 配置</el-button>
            </el-tab-pane>
            <el-tab-pane label="curl" name="curl">
              <p class="access-field__hint">用 Streamable HTTP 探测 initialize；成功应返回 MCP 会话而非 401。</p>
              <el-input :model-value="curlSnippet" type="textarea" :rows="10" readonly />
              <el-button class="copy-snippet" @click="copyText(curlSnippet)">复制 curl</el-button>
            </el-tab-pane>
          </el-tabs>
        </template>
        <template #footer>
          <el-button
            v-if="accessClient"
            v-permission="PERMISSIONS.AI_MCP_EDIT"
            @click="rotateKey(accessClient)"
          >
            轮换 Key
          </el-button>
          <el-button type="primary" @click="accessVisible = false">关闭</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="toolsVisible" title="上游 Tools" width="720px">
        <el-table :data="upstreamTools">
          <el-table-column prop="exposedName" label="暴露名" min-width="180" />
          <el-table-column prop="originalName" label="原名" min-width="140" />
          <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled"
                @change="(on: string | number | boolean) => toggleTool(row as McpUpstreamToolVO, on === true)"
              />
            </template>
          </el-table-column>
        </el-table>
      </el-dialog>
    </template>
  </ListPageShell>
</template>

<script setup lang="ts">
defineOptions({ name: 'AIMcpHub' })

import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Key, Link, Plus, QuestionFilled, Refresh, Search } from '@element-plus/icons-vue'
import ListPageShell from '@/components/list-page/ListPageShell.vue'
import { useRouteActivate } from '@/composables/useRouteActivate'
import { PERMISSIONS } from '@/constants/permissions'
import {
  createMcpClient,
  createMcpUpstream,
  deleteMcpClient,
  deleteMcpUpstream,
  getMcpOverview,
  listMcpCalls,
  listMcpClients,
  listMcpUpstreamTools,
  listMcpUpstreams,
  patchMcpUpstreamTool,
  probeMcpUpstream,
  rotateMcpClientKey,
  updateMcpClient,
  updateMcpUpstream,
} from '@/api/ai'
import type {
  McpCallLogVO,
  McpClientVO,
  McpOverviewVO,
  McpUpstreamToolVO,
  McpUpstreamVO,
} from '@/types/mcp'
import {
  parseMcpUpstreamSnippet,
  type ParsedMcpUpstreamSnippet,
} from '@/utils/parseMcpCurl'

const loading = ref(false)
const saving = ref(false)
const tab = ref<'out' | 'in' | 'log'>('out')
const keyword = ref('')
const overview = ref<McpOverviewVO>({
  serverEnabled: false,
  endpoint: '/mcp',
  clientCount: 0,
  upstreamCount: 0,
  enabledUpstreamCount: 0,
  cryptoConfigured: true,
})
const clients = ref<McpClientVO[]>([])
const upstreams = ref<McpUpstreamVO[]>([])
const logs = ref<McpCallLogVO[]>([])
const logDirection = ref('')

const rpmHint =
  'RPM（Requests Per Minute）即每分钟请求数上限。该 Client 每分钟最多可调用本平台 MCP Tool 这么多次，超出后请求会被拒绝。'
const outboundCaps = [
  { code: 'MCP_TOOLS', label: '上游 MCP 工具' },
  { code: 'KNOWLEDGE_GRAPH', label: '知识图谱' },
]

const clientVisible = ref(false)
const clientEditingId = ref<number | null>(null)
const clientForm = reactive({
  name: '',
  boundUserId: 1,
  boundUsername: 'admin',
  rpmLimit: 60,
  status: 'ENABLED',
  remark: '',
  capabilities: outboundCaps.map((c) => c.code),
})

const upstreamVisible = ref(false)
const upstreamEditingId = ref<number | null>(null)
const upstreamSnippet = ref('')
const snippetHint = ref('')
const snippetPlaceholder = [
  '粘贴 curl 或 MCP URL，自动填充下方字段。例如：',
  'curl -X POST "https://mcpmarket.cn/mcp/..." \\',
  '  -H "Accept: application/json, text/event-stream"',
].join('\n')
const upstreamForm = reactive({
  code: '',
  name: '',
  protocol: 'STREAMABLE_HTTP',
  baseUrl: '',
  endpoint: '/mcp',
  authHeader: '',
  requestTimeoutMs: 20000,
  status: 'ENABLED',
})

const accessVisible = ref(false)
const accessClient = ref<McpClientVO | null>(null)
const accessTab = ref<'cursor' | 'curl'>('cursor')
const accessJustRevealed = ref(false)
const publicMcpUrl = ref('')
const revealedKeys = ref<Record<number, string>>({})
const plainKey = ref('')
const toolsVisible = ref(false)
const toolsUpstreamId = ref<number | null>(null)
const upstreamTools = ref<McpUpstreamToolVO[]>([])

const defaultMcpUrl = () => {
  const base = String(import.meta.env.VITE_API_BASE_URL || '')
    .trim()
    .replace(/\/$/, '')
  const endpoint = overview.value.endpoint || '/mcp'
  return `${base || 'http://127.0.0.1:8180'}${endpoint}`
}

const mcpServerSlug = (name: string, id?: number) => {
  const slug = (name || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40)
  return slug || `wanxiang-client-${id || 'mcp'}`
}

const hasFullAccessKey = computed(() => {
  const id = accessClient.value?.id
  return !!id && !!revealedKeys.value[id]
})

const accessBearerToken = computed(() => {
  const row = accessClient.value
  if (!row) return 'wxmcp_<完整密钥>'
  if (revealedKeys.value[row.id]) return revealedKeys.value[row.id]
  return `${row.keyPrefix}<完整密钥>`
})

const accessAuthHeader = computed(() => `Authorization: Bearer ${accessBearerToken.value}`)

const cursorSnippet = computed(() => {
  const slug = mcpServerSlug(accessClient.value?.name || 'wanxiang-monitor', accessClient.value?.id)
  return `{
  "mcpServers": {
    "${slug}": {
      "url": "${publicMcpUrl.value || defaultMcpUrl()}",
      "headers": {
        "Authorization": "Bearer ${accessBearerToken.value}"
      }
    }
  }
}`
})

const curlSnippet = computed(() => {
  const url = publicMcpUrl.value || defaultMcpUrl()
  const token = accessBearerToken.value
  return `curl -N -X POST "${url}" \\
  -H "Authorization: Bearer ${token}" \\
  -H "Accept: application/json, text/event-stream" \\
  -H "Content-Type: application/json" \\
  -H "MCP-Protocol-Version: 2025-03-26" \\
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"probe","version":"1.0"}}}'`
})

const openClientAccess = (row: McpClientVO, options?: { apiKey?: string; justRevealed?: boolean }) => {
  accessClient.value = row
  accessTab.value = 'cursor'
  publicMcpUrl.value = defaultMcpUrl()
  if (options?.apiKey) {
    revealedKeys.value = { ...revealedKeys.value, [row.id]: options.apiKey }
    plainKey.value = options.apiKey
  } else {
    plainKey.value = revealedKeys.value[row.id] || ''
  }
  accessJustRevealed.value = !!options?.justRevealed
  accessVisible.value = true
}

const heroMetrics = computed(() => [
  {
    key: 'clients',
    label: '对外 Client',
    value: clients.value.length,
    icon: Key,
    accent: 'primary' as const,
  },
  {
    key: 'upstreams',
    label: '接入上游',
    value: upstreams.value.length,
    icon: Connection,
    accent: 'primary' as const,
  },
  {
    key: 'endpoint',
    label: '接入端点',
    value: overview.value.endpoint || '/mcp',
    icon: Link,
    accent: 'primary' as const,
  },
])

const searchPlaceholder = computed(() => {
  if (tab.value === 'in') return '搜索上游名称、编码或 URL…'
  if (tab.value === 'log') return '搜索 Tool 名称…'
  return '搜索 Client 名称或 Key 前缀…'
})

const matchKw = (text: string | null | undefined, kw: string) =>
  (text || '').toLowerCase().includes(kw)

const filteredClients = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return clients.value
  return clients.value.filter(
    (r) => matchKw(r.name, kw) || matchKw(r.keyPrefix, kw) || matchKw(r.boundUsername, kw),
  )
})

const filteredUpstreams = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return upstreams.value
  return upstreams.value.filter(
    (r) => matchKw(r.name, kw) || matchKw(r.code, kw) || matchKw(r.baseUrl, kw),
  )
})

const filteredLogs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return logs.value
  return logs.value.filter((r) => matchKw(r.toolName, kw) || matchKw(r.errorMessage, kw))
})

const capLabel = (code: string) => outboundCaps.find((c) => c.code === code)?.label || code

const protocolLabel = (protocol: string) =>
  protocol === 'SSE' ? 'SSE' : 'Streamable HTTP'

const healthTag = (status?: string | null) => {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'danger'
  return 'info'
}

const reload = async () => {
  loading.value = true
  try {
    const [ov, cs, us] = await Promise.all([getMcpOverview(), listMcpClients(), listMcpUpstreams()])
    overview.value = ov
    clients.value = cs || []
    upstreams.value = us || []
    await loadLogs()
  } finally {
    loading.value = false
  }
}

const loadLogs = async () => {
  logs.value = (await listMcpCalls(logDirection.value || undefined)) || []
}

const openCreateClient = () => {
  clientEditingId.value = null
  clientForm.name = ''
  clientForm.boundUserId = 1
  clientForm.boundUsername = 'admin'
  clientForm.rpmLimit = 60
  clientForm.status = 'ENABLED'
  clientForm.remark = ''
  clientForm.capabilities = outboundCaps.map((c) => c.code)
  tab.value = 'out'
  clientVisible.value = true
}

const openEditClient = (row: McpClientVO) => {
  clientEditingId.value = row.id
  clientForm.name = row.name
  clientForm.boundUserId = row.boundUserId
  clientForm.boundUsername = row.boundUsername || ''
  clientForm.rpmLimit = row.rpmLimit
  clientForm.status = row.status
  clientForm.remark = row.remark || ''
  clientForm.capabilities = [...(row.capabilities || [])]
  clientVisible.value = true
}

const saveClient = async () => {
  saving.value = true
  try {
    if (clientEditingId.value) {
      await updateMcpClient(clientEditingId.value, {
        name: clientForm.name,
        boundUserId: clientForm.boundUserId,
        boundUsername: clientForm.boundUsername,
        rpmLimit: clientForm.rpmLimit,
        status: clientForm.status,
        remark: clientForm.remark,
        capabilities: clientForm.capabilities,
      })
      ElMessage.success('已更新')
    } else {
      const created = await createMcpClient({
        name: clientForm.name,
        boundUserId: clientForm.boundUserId,
        boundUsername: clientForm.boundUsername,
        rpmLimit: clientForm.rpmLimit,
        remark: clientForm.remark,
        capabilities: clientForm.capabilities,
      })
      if (created?.apiKey) {
        openClientAccess(created, { apiKey: created.apiKey, justRevealed: true })
      }
      ElMessage.success('已创建')
    }
    clientVisible.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

const rotateKey = async (row: McpClientVO) => {
  await ElMessageBox.confirm(`轮换「${row.name}」的 API Key？旧 Key 立即失效。`, '确认')
  const vo = await rotateMcpClientKey(row.id)
  await reload()
  const latest = clients.value.find((c) => c.id === row.id) || vo
  if (vo?.apiKey && latest) {
    openClientAccess(latest, { apiKey: vo.apiKey, justRevealed: true })
  }
}

const removeClient = async (row: McpClientVO) => {
  await ElMessageBox.confirm(`删除 Client「${row.name}」？`, '确认', { type: 'warning' })
  await deleteMcpClient(row.id)
  ElMessage.success('已删除')
  await reload()
}

const resetUpstreamSnippet = () => {
  upstreamSnippet.value = ''
  snippetHint.value = ''
}

const fillUpstreamFromParsed = (parsed: ParsedMcpUpstreamSnippet) => {
  upstreamForm.protocol = parsed.protocol
  upstreamForm.baseUrl = parsed.baseUrl
  upstreamForm.endpoint = parsed.endpoint
  if (parsed.authHeader) {
    upstreamForm.authHeader = parsed.authHeader
  }
  if (parsed.requestTimeoutMs) {
    upstreamForm.requestTimeoutMs = parsed.requestTimeoutMs
  }
  if (!upstreamEditingId.value && !upstreamForm.code) {
    upstreamForm.code = parsed.code
  }
  if (!upstreamForm.name) {
    upstreamForm.name = parsed.name
  }
  snippetHint.value = `${parsed.protocol === 'SSE' ? 'SSE' : 'Streamable HTTP'} · ${parsed.baseUrl}${parsed.endpoint}`
}

const applyUpstreamSnippet = (raw = upstreamSnippet.value, silent = false) => {
  const text = raw.trim()
  if (!text) {
    snippetHint.value = ''
    return false
  }
  const parsed = parseMcpUpstreamSnippet(text)
  if (!parsed) {
    snippetHint.value = ''
    if (!silent) {
      ElMessage.warning('无法从这段文本解析出 MCP 地址，请粘贴 curl 或 URL')
    }
    return false
  }
  fillUpstreamFromParsed(parsed)
  if (!silent) {
    ElMessage.success('已自动填充')
  }
  return true
}

const onUpstreamSnippetPaste = () => {
  void nextTick(() => applyUpstreamSnippet(upstreamSnippet.value, true))
}

const onUpstreamSnippetChange = (value?: string) => {
  applyUpstreamSnippet(typeof value === 'string' ? value : upstreamSnippet.value, true)
}

const trySplitFullUrl = (raw: string) => {
  const text = raw.trim()
  if (!text) {
    return
  }
  const isCurl = text.includes('curl') || text.includes('\n')
  if (!isCurl && !/^https?:\/\//i.test(text)) {
    return
  }
  if (!isCurl) {
    try {
      const url = new URL(text)
      if (!url.pathname || url.pathname === '/') {
        return
      }
    } catch {
      return
    }
  }
  const parsed = parseMcpUpstreamSnippet(text)
  if (parsed) {
    fillUpstreamFromParsed(parsed)
  }
}

const onBaseUrlPaste = () => {
  void nextTick(() => trySplitFullUrl(upstreamForm.baseUrl))
}

const onBaseUrlChange = () => {
  trySplitFullUrl(upstreamForm.baseUrl)
}

const openCreateUpstream = () => {
  upstreamEditingId.value = null
  upstreamForm.code = ''
  upstreamForm.name = ''
  upstreamForm.protocol = 'STREAMABLE_HTTP'
  upstreamForm.baseUrl = ''
  upstreamForm.endpoint = '/mcp'
  upstreamForm.authHeader = ''
  upstreamForm.requestTimeoutMs = 20000
  upstreamForm.status = 'ENABLED'
  resetUpstreamSnippet()
  tab.value = 'in'
  upstreamVisible.value = true
}

const openEditUpstream = (row: McpUpstreamVO) => {
  upstreamEditingId.value = row.id
  upstreamForm.code = row.code
  upstreamForm.name = row.name
  upstreamForm.protocol = row.protocol
  upstreamForm.baseUrl = row.baseUrl
  upstreamForm.endpoint = row.endpoint || '/mcp'
  upstreamForm.authHeader = ''
  upstreamForm.requestTimeoutMs = row.requestTimeoutMs ?? 20000
  upstreamForm.status = row.status
  resetUpstreamSnippet()
  upstreamVisible.value = true
}

const saveUpstream = async () => {
  saving.value = true
  try {
    const payload = { ...upstreamForm }
    if (upstreamEditingId.value) {
      await updateMcpUpstream(upstreamEditingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await createMcpUpstream(payload)
      ElMessage.success('已登记')
    }
    upstreamVisible.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

const probe = async (row: McpUpstreamVO) => {
  await probeMcpUpstream(row.id)
  ElMessage.success('探活完成')
  await reload()
}

const showTools = async (row: McpUpstreamVO) => {
  toolsUpstreamId.value = row.id
  upstreamTools.value = (await listMcpUpstreamTools(row.id)) || []
  toolsVisible.value = true
}

const toggleTool = async (row: McpUpstreamToolVO, enabled: boolean) => {
  if (toolsUpstreamId.value == null) {
    return
  }
  const updated = await patchMcpUpstreamTool(toolsUpstreamId.value, {
    originalName: row.originalName,
    enabled,
  })
  row.enabled = updated?.enabled ?? enabled
}

const copyText = async (text: string) => {
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}

const removeUpstream = async (row: McpUpstreamVO) => {
  await ElMessageBox.confirm(`删除上游「${row.name}」？已绑定的智能体会解除该上游。`, '确认', {
    type: 'warning',
  })
  await deleteMcpUpstream(row.id)
  ElMessage.success('已删除')
  await reload()
}

useRouteActivate(() => {
  void reload()
})
</script>

<style scoped lang="scss">
.mcp-home {
  --mcp-ink: #0f2740;
  --mcp-muted: #5b738a;
  --mcp-line: rgba(26, 43, 60, 0.1);
  --mcp-accent: #0891b2;
  --mcp-primary: #1a7fd4;
  --mcp-shelf: #f4f7fa;
  padding: 14px 12px 16px;
  min-height: 360px;
}

.mcp-alert {
  margin-bottom: 12px;
}

.mcp-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--mcp-line);
  border-radius: var(--app-radius-md);
  background: var(--mcp-shelf);

  &__search {
    flex: 1;
    min-width: 200px;
    max-width: 360px;
  }
}

.mcp-empty {
  padding: 48px 0;
}

.mcp-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
}

.mcp-card {
  position: relative;
  display: grid;
  grid-template-columns: 6px minmax(0, 1fr);
  min-height: 248px;
  border-radius: 12px;
  border: 1px solid rgba(26, 43, 60, 0.08);
  background: #fff;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--mcp-primary) 35%, transparent);
    box-shadow: 0 4px 8px rgba(15, 23, 42, 0.05), 0 12px 24px rgba(15, 23, 42, 0.07);
  }

  &--clickable {
    cursor: pointer;

    &:focus-visible {
      outline: 2px solid color-mix(in srgb, var(--mcp-primary) 55%, transparent);
      outline-offset: 2px;
    }
  }

  &.is-disabled {
    opacity: 0.78;
  }

  &--out {
    --persona-tone: #7dd3fc;
  }

  &--in {
    --persona-tone: #5eead4;
  }

  &__rail {
    background: linear-gradient(180deg, var(--persona-tone), color-mix(in srgb, var(--persona-tone) 70%, #fff));
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px 16px 14px;
    min-width: 0;
  }

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  &__avatar {
    position: relative;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    border-radius: 12px;
    color: #04202a;
    background: linear-gradient(135deg, #ecfeff, var(--persona-tone));
    box-shadow: 0 4px 12px color-mix(in srgb, var(--persona-tone) 28%, transparent);
  }

  &__pulse {
    position: absolute;
    right: -2px;
    bottom: -2px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #22c55e;
    border: 2px solid #fff;
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.45);
    animation: mcp-pulse 1.6s ease-out infinite;
  }

  &__titles {
    flex: 1;
    min-width: 0;
  }

  &__name-row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__name {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: var(--mcp-ink);
    line-height: 1.3;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__id-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
    min-width: 0;
    flex-wrap: wrap;
  }

  &__code {
    padding: 1px 8px;
    border-radius: 4px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 11px;
    color: var(--mcp-muted);
    background: #f1f6fa;
  }

  &__meta {
    font-size: 11px;
    font-weight: 650;
    color: var(--persona-tone);
  }

  &__desc {
    margin: 0;
    min-height: 40px;
    font-size: 13px;
    line-height: 1.55;
    color: var(--mcp-muted);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__health {
    margin: 0;
    font-size: 12px;
    color: var(--mcp-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__specs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__spec {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 650;
    color: var(--mcp-primary);
    background: rgba(26, 127, 212, 0.08);
    border: 1px solid rgba(26, 127, 212, 0.16);

    &--muted {
      color: var(--mcp-muted);
      background: #f5f8fb;
      border-color: #e8eef3;
      font-weight: 500;
    }
  }

  &__skills {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    min-height: 28px;
  }

  &__skills-empty {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }

  &__hint {
    margin: 0;
    font-size: 12px;
    color: var(--mcp-accent);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-top: auto;
    padding-top: 10px;
    border-top: 1px solid #eef3f7;
    flex-wrap: wrap;
  }

  &__links {
    display: flex;
    flex-wrap: wrap;
    gap: 2px;
  }
}

.mcp-skill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  color: var(--mcp-ink);
  background: #f5f8fb;
  border: 1px solid #e8eef3;
}

.rpm-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.rpm-help {
  display: inline-flex;
  align-items: center;
  color: var(--el-text-color-placeholder);
  cursor: help;
  line-height: 1;

  &:hover {
    color: var(--el-color-primary);
  }
}

.mcp-log-table {
  width: 100%;
}

.cap-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.curl-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-placeholder);

  &--ok {
    color: #0891b2;
  }
}

.access-meta {
  margin: 12px 0 16px;

  :deep(.el-descriptions__content),
  :deep(.el-descriptions__label) {
    white-space: normal;
    word-break: break-word;
    overflow-wrap: anywhere;
  }
}

.access-field {
  margin-bottom: 14px;

  &__label {
    margin-bottom: 6px;
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__hint {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-placeholder);

    code {
      font-size: 12px;
    }
  }
}

.access-tabs {
  margin-top: 4px;
}

.copy-snippet {
  margin-top: 8px;
}

@keyframes mcp-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.45);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(34, 197, 94, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(34, 197, 94, 0);
  }
}
</style>

<style lang="scss">
.el-popper.mcp-rpm-hint-popper {
  max-width: min(280px, 80vw);
}

.el-popper.mcp-rpm-hint-popper,
.el-popper.mcp-rpm-hint-popper .el-popper__content {
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.55;
}
</style>
