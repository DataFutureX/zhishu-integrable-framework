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
        title="供应商只维护连接与密钥；对话/向量模型与生成参数请在「模型设置」中按供应商添加。"
      />

      <el-table v-loading="loading" :data="providers" class="modern-table" empty-text="暂无供应商">
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
          <template #default="{ row }">对话 {{ row.chatModelCount }} / 向量 {{ row.embeddingModelCount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small" effect="light">
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑供应商' : '新建供应商'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 阿里百炼、DeepSeek" />
        </el-form-item>
        <el-form-item label="程序标识" prop="providerKey">
          <el-input v-model="form.providerKey" :disabled="!!editingId" placeholder="如 dashscope / deepseek" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId ? '留空表示不修改' : '粘贴供应商 API Key'"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
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
      <template #footer>
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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createGatewayProviderApi,
  deleteGatewayProviderApi,
  listGatewayProvidersApi,
  setDefaultGatewayProviderApi,
  testGatewayProviderApi,
  updateGatewayProviderApi,
} from '@/api/gateway'
import type { GatewayProviderUpsertDTO, GatewayProviderVO } from '@/types/modelGateway'

defineOptions({ name: 'GatewayProviderList' })

const loading = ref(false)
const saving = ref(false)
const testingId = ref<number | null>(null)
const providers = ref<GatewayProviderVO[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
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
    const result = await testGatewayProviderApi(row.id)
    ElMessage.info(result || '测试完成')
  } finally {
    testingId.value = null
  }
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

.ml-6 {
  margin-left: 6px;
}
</style>
