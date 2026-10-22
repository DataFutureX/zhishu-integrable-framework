<template>
  <div class="gateway-page">
    <el-card shadow="never">
      <template #header>
        <div class="gateway-page__header">
          <span class="gateway-page__title">额度策略</span>
          <div class="gateway-page__actions">
            <el-select v-model="subjectFilter" size="small" style="width: 140px" @change="loadQuotas">
              <el-option label="全部主体" value="" />
              <el-option label="用户" value="USER" />
              <el-option label="角色" value="ROLE" />
              <el-option label="兜底" value="DEFAULT" />
            </el-select>
            <el-button size="small" :icon="Refresh" @click="loadQuotas">刷新</el-button>
            <el-button type="primary" size="small" :icon="Plus" @click="openCreate">新建策略</el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="gateway-page__hint"
        title="解析顺序：用户额度 → 角色额度 → 兜底额度 → 配置默认值。用户首次调用时按当时解析结果生成用户行。"
      />

      <el-table v-loading="loading" :data="quotas" class="modern-table" empty-text="暂无额度策略">
        <el-table-column label="主体" min-width="180">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.subjectType }}</el-tag>
            <span class="gateway-page__key">{{ row.subjectKey }}</span>
          </template>
        </el-table-column>
        <el-table-column label="周期" width="140">
          <template #default="{ row }">{{ row.periodKind }} / {{ formatDate(row.periodEnd) }}</template>
        </el-table-column>
        <el-table-column label="上限" width="140" align="right">
          <template #default="{ row }">{{ row.tokenLimit == null ? '不限量' : row.tokenLimit }}</template>
        </el-table-column>
        <el-table-column label="已用" width="140" align="right">
          <template #default="{ row }">{{ row.usedTokens ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="剩余" width="140" align="right">
          <template #default="{ row }">{{ row.remaining != null && row.remaining >= 0 ? row.remaining : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row as GatewayQuotaVO)">编辑</el-button>
            <el-button link type="primary" size="small" @click="handleReset(row as GatewayQuotaVO)">清零</el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="row.subjectType === 'DEFAULT'"
              @click="handleDelete(row as GatewayQuotaVO)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑额度策略' : '新建额度策略'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="主体类型" prop="subjectType">
          <el-select v-model="form.subjectType" :disabled="!!editingId" style="width: 100%">
            <el-option label="用户（username）" value="USER" />
            <el-option label="角色（role_code）" value="ROLE" />
            <el-option label="兜底（*）" value="DEFAULT" />
          </el-select>
        </el-form-item>
        <el-form-item label="主体键" prop="subjectKey">
          <el-input
            v-model="form.subjectKey"
            :disabled="!!editingId || form.subjectType === 'DEFAULT'"
            placeholder="USER=用户名，ROLE=角色编码"
          />
        </el-form-item>
        <el-form-item label="周期">
          <el-select v-model="form.periodKind" style="width: 100%">
            <el-option label="每月（MONTHLY）" value="MONTHLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="token 上限">
          <el-input-number
            v-model="form.tokenLimit"
            :min="1"
            :step="100000"
            controls-position="right"
            style="width: 100%"
          />
          <div class="gateway-page__meta">留空表示不限量（谨慎使用）</div>
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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createGatewayQuotaApi,
  deleteGatewayQuotaApi,
  listGatewayQuotasApi,
  resetGatewayQuotaApi,
  updateGatewayQuotaApi,
} from '@/api/gateway'
import type { GatewayQuotaUpsertDTO, GatewayQuotaVO } from '@/types/modelGateway'

defineOptions({ name: 'GatewayQuotaPolicy' })

const loading = ref(false)
const saving = ref(false)
const quotas = ref<GatewayQuotaVO[]>([])
const subjectFilter = ref('')
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<GatewayQuotaUpsertDTO>({
  subjectType: 'USER',
  subjectKey: '',
  periodKind: 'MONTHLY',
  tokenLimit: 1000000,
  status: 'ENABLED',
  remark: null,
})

const rules: FormRules = {
  subjectType: [{ required: true, message: '请选择主体类型', trigger: 'change' }],
  subjectKey: [{ required: true, message: '请填写主体键', trigger: 'blur' }],
}

async function loadQuotas() {
  loading.value = true
  try {
    quotas.value = await listGatewayQuotasApi(subjectFilter.value || undefined)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.subjectType = 'USER'
  form.subjectKey = ''
  form.periodKind = 'MONTHLY'
  form.tokenLimit = 1000000
  form.status = 'ENABLED'
  form.remark = null
  dialogVisible.value = true
}

function openEdit(row: GatewayQuotaVO) {
  editingId.value = row.id
  form.subjectType = row.subjectType
  form.subjectKey = row.subjectKey
  form.periodKind = row.periodKind || 'MONTHLY'
  form.tokenLimit = row.tokenLimit ?? undefined
  form.status = row.status || 'ENABLED'
  form.remark = row.remark ?? null
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload: GatewayQuotaUpsertDTO = {
    ...form,
    subjectKey: form.subjectType === 'DEFAULT' ? '*' : form.subjectKey,
    tokenLimit: form.tokenLimit ?? null,
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateGatewayQuotaApi(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await createGatewayQuotaApi(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await loadQuotas()
  } finally {
    saving.value = false
  }
}

async function handleReset(row: GatewayQuotaVO) {
  await ElMessageBox.confirm(`确认清零 ${row.subjectType}:${row.subjectKey} 的已用量？`, '提示', {
    type: 'warning',
  })
  await resetGatewayQuotaApi(row.id)
  ElMessage.success('已清零')
  await loadQuotas()
}

async function handleDelete(row: GatewayQuotaVO) {
  await ElMessageBox.confirm(`确认删除 ${row.subjectType}:${row.subjectKey} 的额度策略？`, '提示', {
    type: 'warning',
  })
  await deleteGatewayQuotaApi(row.id)
  ElMessage.success('已删除')
  await loadQuotas()
}

function formatDate(value?: string | null) {
  return value ? value.slice(0, 10) : '—'
}

onMounted(loadQuotas)
</script>

<style scoped>
.gateway-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.gateway-page__title {
  font-size: 15px;
  font-weight: 600;
}

.gateway-page__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.gateway-page__hint {
  margin-bottom: 12px;
}

.gateway-page__key {
  margin-left: 8px;
  font-family: var(--el-font-family-monospace, monospace);
}

.gateway-page__meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
