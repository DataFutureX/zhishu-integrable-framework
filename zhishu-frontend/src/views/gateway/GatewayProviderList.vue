<template>
  <div class="gateway-page">
    <el-card shadow="never">
      <template #header>
        <div class="gateway-page__header">
          <span class="gateway-page__title">供应商管理</span>
          <div class="gateway-page__actions">
            <el-button size="small" :icon="Refresh" @click="loadProviders">刷新</el-button>
            <el-button
              v-permission="PERMISSIONS.GATEWAY_PROVIDER_UPDATE"
              type="primary"
              size="small"
              :icon="Plus"
              @click="openCreate"
            >
              新建供应商
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="gateway-page__hint"
        title="供应商只维护连接与密钥；对话/向量模型与生成参数请在「模型设置」中按供应商添加。列表按「默认 → 启用 → 排序号」展示，停用的排在最后。"
      />

      <el-table
        v-loading="loading"
        :data="providers"
        class="modern-table"
        empty-text="暂无供应商"
        :row-class-name="providerRowClassName"
      >
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.isDefault" size="small" type="success" class="ml-6">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="providerKey" label="标识" width="120" show-overflow-tooltip />
        <el-table-column prop="baseUrl" label="Base URL" min-width="240" show-overflow-tooltip />
        <el-table-column label="API Key" width="150">
          <template #default="{ row }">
            <el-tag :type="row.apiKeyConfigured ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.apiKeyConfigured ? row.apiKeyMasked || '已配置' : '未配置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="模型数" width="140" align="center">
          <template #default="{ row }"
            >对话 {{ row.chatModelCount }} / 向量 {{ row.embeddingModelCount }}</template
          >
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'ENABLED' ? 'success' : 'info'"
              size="small"
              effect="light"
            >
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-permission="PERMISSIONS.GATEWAY_PROVIDER_UPDATE"
              link
              type="primary"
              size="small"
              @click="openEdit(row as GatewayProviderVO)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              :loading="testingId === row.id"
              @click="handleTest(row as GatewayProviderVO)"
            >
              测试连接
            </el-button>
            <el-button
              v-permission="PERMISSIONS.GATEWAY_PROVIDER_UPDATE"
              link
              type="primary"
              size="small"
              :disabled="row.isDefault"
              @click="handleDefault(row as GatewayProviderVO)"
            >
              设默认
            </el-button>
            <el-button
              v-permission="PERMISSIONS.GATEWAY_PROVIDER_UPDATE"
              link
              type="danger"
              size="small"
              @click="handleDelete(row as GatewayProviderVO)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑供应商' : '新建供应商'"
      width="620px"
      @closed="resetTestState"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 阿里百炼、DeepSeek" />
        </el-form-item>
        <el-form-item label="程序标识" prop="providerKey">
          <el-input
            v-model="form.providerKey"
            :disabled="!!editingId"
            placeholder="如 dashscope / deepseek"
          />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input
            v-model="form.baseUrl"
            placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
          />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId ? '留空表示不修改' : '可留空：Ollama 等无需鉴权的上游直接测试'"
          />
          <span class="gateway-page__meta">
            可留空：编辑态留空时测试使用已保存的 Key；新建时按匿名方式探测，由上游 401
            反馈是否需要鉴权
          </span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="启用" value="ENABLED" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="lastTest"
        class="gateway-page__test-result"
        :type="lastTest.ok ? 'success' : 'warning'"
        :title="lastTest.ok ? '上次测试：连通成功' : '上次测试：连通失败'"
        :description="`${lastTest.time} · ${lastTest.message}`"
        show-icon
        :closable="false"
      />
      <template #footer>
        <el-button :loading="testingDraft" @click="handleTestDraft">测试连接</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { PERMISSIONS } from '@/constants/permissions'
import { formatDateTime } from '@/utils/format'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createGatewayProviderApi,
  deleteGatewayProviderApi,
  listGatewayProvidersApi,
  setDefaultGatewayProviderApi,
  testGatewayProviderApi,
  testGatewayProviderDraftApi,
  updateGatewayProviderApi,
} from '@/api/gateway'
import type { GatewayId, GatewayProviderUpsertDTO, GatewayProviderVO } from '@/types/modelGateway'

defineOptions({ name: 'GatewayProviderList' })

const loading = ref(false)
const saving = ref(false)
const testingId = ref<GatewayId | null>(null)
/** 弹窗内「测试连接」的加载态（与行内测试互不影响） */
const testingDraft = ref(false)
/** 弹窗内展示上一次试跑结果，便于保存前回溯 */
const lastTest = ref<{ ok: boolean; message: string; time: string } | null>(null)
const providers = ref<GatewayProviderVO[]>([])
const dialogVisible = ref(false)
const editingId = ref<GatewayId | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<GatewayProviderUpsertDTO>({
  name: '',
  providerKey: '',
  baseUrl: '',
  apiKey: '',
  isDefault: false,
  status: 'ENABLED',
  sortOrder: 0,
  remark: null,
})

const rules: FormRules = {
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  providerKey: [{ required: true, message: '请填写程序标识', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请填写 Base URL', trigger: 'blur' }],
}

async function loadProviders() {
  loading.value = true
  try {
    providers.value = await listGatewayProvidersApi()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  resetTestState()
  form.name = ''
  form.providerKey = ''
  form.baseUrl = ''
  form.apiKey = ''
  form.isDefault = false
  form.status = 'ENABLED'
  form.sortOrder = 0
  form.remark = null
  dialogVisible.value = true
}

function openEdit(row: GatewayProviderVO) {
  editingId.value = row.id
  resetTestState()
  form.name = row.name
  form.providerKey = row.providerKey
  form.baseUrl = row.baseUrl
  form.apiKey = ''
  form.isDefault = Boolean(row.isDefault)
  form.status = row.status
  form.sortOrder = row.sortOrder ?? 0
  form.remark = row.remark ?? null
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateGatewayProviderApi(editingId.value, { ...form })
      ElMessage.success('已保存')
    } else {
      await createGatewayProviderApi({ ...form })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await loadProviders()
  } finally {
    saving.value = false
  }
}

async function handleTest(row: GatewayProviderVO) {
  testingId.value = row.id
  try {
    showTestResult(await testGatewayProviderApi(row.id))
  } finally {
    testingId.value = null
  }
}

/** 未配置 API Key 也可测：后端会以匿名方式探测，401 时返回需要鉴权的提示 */
async function handleTestDraft() {
  if (!form.baseUrl.trim()) {
    ElMessage.warning('请先填写 Base URL')
    return
  }
  // 只校验 Base URL：名称/标识/API Key 都不是测试的必填项
  const baseUrlValid = await new Promise<boolean>((resolve) => {
    const result = formRef.value?.validateField('baseUrl', (isValid: boolean) =>
      resolve(Boolean(isValid)),
    )
    if (!result) resolve(false)
  })
  if (!baseUrlValid) return

  testingDraft.value = true
  try {
    const result = await testGatewayProviderDraftApi({
      providerId: editingId.value,
      providerKey: form.providerKey?.trim() || undefined,
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey?.trim() || null,
    })
    showTestResult(result, true)
  } finally {
    testingDraft.value = false
  }
}

/** 统一测试结果提示：成功用 success，其余作为警告展示后端原因 */
function showTestResult(result?: string, record = false) {
  const message = result || '测试完成'
  const ok = message.includes('连通成功')
  if (record) {
    lastTest.value = { ok, message, time: formatDateTime(new Date()) }
  }
  if (ok) {
    ElMessage.success(message)
    return
  }
  ElMessage.warning(message)
}

/** 打开/关闭弹窗时清空上一轮试跑结果 */
function resetTestState() {
  lastTest.value = null
}

/** 停用的供应商整行置灰，配合排序（停用沉底）减少误选 */
function providerRowClassName({ row }: { row: GatewayProviderVO }) {
  return row.status === 'ENABLED' ? '' : 'provider-row--disabled'
}

async function handleDefault(row: GatewayProviderVO) {
  await setDefaultGatewayProviderApi(row.id)
  ElMessage.success('已设为默认供应商')
  await loadProviders()
}

async function handleDelete(row: GatewayProviderVO) {
  await ElMessageBox.confirm(`确认删除供应商 ${row.name}？`, '提示', { type: 'warning' })
  await deleteGatewayProviderApi(row.id)
  ElMessage.success('已删除')
  await loadProviders()
}

onMounted(loadProviders)
</script>

<style scoped>
.gateway-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.gateway-page__title {
  font-size: 15px;
  font-weight: 600;
}

.gateway-page__actions {
  display: flex;
  gap: 8px;
}

.gateway-page__hint {
  margin-bottom: 12px;
}

.gateway-page__meta {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.gateway-page__test-result {
  margin-top: 4px;
}

:deep(.provider-row--disabled) {
  color: var(--el-text-color-placeholder);
}

.ml-6 {
  margin-left: 6px;
}
</style>
