<template>
  <!--
    eslint-disable vue/no-mutating-props
    表单对象由 GatewayModelList 持有（提交与上游回填都依赖同一份 reactive 状态），
    本组件定位为受控表单编辑器，故允许就地修改字段。
  -->
  <el-dialog v-model="visible" :title="editingId ? '编辑模型' : '新建模型'" width="760px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
      <el-form-item label="供应商" prop="providerId">
        <el-select
          v-model="form.providerId"
          filterable
          placeholder="请选择供应商"
          style="width: 100%"
          @change="emit('providerChange')"
        >
          <el-option
            v-for="provider in providers"
            :key="provider.id"
            :label="provider.isDefault ? `${provider.name}（默认）` : provider.name"
            :value="provider.id"
          />
        </el-select>
        <span class="gateway-page__meta"
          >模型必须归属一个供应商；未配置时请先去「供应商管理」新建</span
        >
      </el-form-item>

      <el-form-item label="模型类型">
        <el-radio-group
          v-model="form.modelType"
          :disabled="!!editingId"
          @change="emit('typeChange', $event)"
        >
          <el-radio-button value="CHAT">对话</el-radio-button>
          <el-radio-button value="EMBEDDING">向量</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="模型 ID" prop="modelId">
        <el-input
          v-model="form.modelId"
          :disabled="!!editingId"
          placeholder="如 gw-dashscope-qwen-plus"
        />
      </el-form-item>

      <el-form-item label="展示名" prop="displayName">
        <el-input v-model="form.displayName" placeholder="如 通义千问 Plus" />
      </el-form-item>

      <el-form-item label="上游模型名" prop="upstreamModel">
        <div class="upstream-picker">
          <el-select
            v-model="form.upstreamModel"
            filterable
            allow-create
            default-first-option
            :loading="loadingUpstream"
            placeholder="从供应商已接入模型中选择，也可直接输入"
            style="flex: 1"
            @change="emit('upstreamChange', $event)"
          >
            <el-option
              v-for="item in upstreamModels"
              :key="item.id"
              :label="item.id"
              :value="item.id"
            >
              <span>{{
                item.name && item.name !== item.id ? `${item.id} · ${item.name}` : item.id
              }}</span>
              <span class="gateway-page__meta" style="float: right; margin-left: 12px">
                {{ item.contextWindow ?? '?' }} / {{ item.maxOutputTokens ?? '?' }}
              </span>
            </el-option>
          </el-select>
          <el-button :loading="loadingUpstream" @click="emit('reloadUpstream')">重新拉取</el-button>
        </div>
        <span v-if="upstreamError" class="gateway-page__meta upstream-picker__error">
          未能自动拉取上游模型（{{ upstreamError }}），可手工输入模型名，或先到「供应商管理」确认
          Base URL 与 API Key
        </span>
        <span
          v-else-if="!loadingUpstream && upstreamModels.length === 0"
          class="gateway-page__meta"
        >
          该供应商未返回可用模型，可直接输入上游模型名
        </span>
        <span v-else class="gateway-page__meta">
          选中后自动回填展示名、上下文窗口、输出上限、模态与推理档位
        </span>
      </el-form-item>

      <el-form-item label="上下文窗口">
        <el-input-number
          v-model="form.contextWindow"
          :min="1"
          :step="1024"
          controls-position="right"
        />
      </el-form-item>

      <el-form-item label="输出上限">
        <el-input-number
          v-model="form.maxOutputTokens"
          :min="1"
          :step="1024"
          controls-position="right"
        />
      </el-form-item>

      <el-form-item label="默认温度">
        <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" />
        <span class="gateway-page__meta">请求未携带 temperature 时使用</span>
      </el-form-item>

      <el-form-item label="默认 Top P">
        <el-input-number v-model="form.topP" :min="0" :max="1" :step="0.05" :precision="2" />
      </el-form-item>

      <el-form-item label="默认 max_tokens">
        <el-input-number v-model="form.maxTokens" :min="1" :step="256" controls-position="right" />
        <span class="gateway-page__meta">请求未携带 max_tokens 时使用；向量模型不需要</span>
      </el-form-item>

      <el-form-item label="输入模态">
        <el-input v-model="form.inputModalities" placeholder="逗号分隔，如 text,image" />
      </el-form-item>

      <el-form-item label="system 策略">
        <el-select v-model="form.systemPromptUpdate" style="width: 100%">
          <el-option label="in-history" value="in-history" />
          <el-option label="leading-only" value="leading-only" />
        </el-select>
      </el-form-item>

      <el-form-item label="reasoning 档位">
        <el-input v-model="form.reasoningEfforts" placeholder="逗号分隔，如 low,medium,high" />
      </el-form-item>

      <el-form-item label="默认档位">
        <el-input v-model="form.defaultReasoningEffort" placeholder="如 high" />
      </el-form-item>

      <el-form-item label="客户端门禁">
        <el-input
          v-model="form.clientRequirements"
          type="textarea"
          :rows="2"
          placeholder='{"LetsWork":{"min_version":"0.2.1"}}'
        />
      </el-form-item>

      <el-form-item label="透传参数">
        <el-input
          v-model="form.extraParams"
          type="textarea"
          :rows="2"
          placeholder='{"enable_thinking":true}'
        />
      </el-form-item>

      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
      </el-form-item>

      <el-form-item label="设为默认">
        <el-switch v-model="form.isDefault" />
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('submit')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type {
  GatewayId,
  GatewayModelUpsertDTO,
  GatewayProviderVO,
  GatewayUpstreamModel,
} from '@/types/modelGateway'

/**
 * 模型新建 / 编辑弹窗（纯展示组件）。
 * 表单状态、上游模型拉取与提交逻辑留在 GatewayModelList，组件只负责渲染与转发事件。
 */
defineOptions({ name: 'GatewayModelFormDialog' })

// 只在模板中使用，无需绑定局部变量；表单对象来自父组件 reactive，子组件内改字段会回写父级
defineProps<{
  editingId: GatewayId | null
  providers: GatewayProviderVO[]
  form: GatewayModelUpsertDTO
  rules: FormRules
  saving: boolean
  upstreamModels: GatewayUpstreamModel[]
  loadingUpstream: boolean
  upstreamError: string
}>()

const emit = defineEmits<{
  providerChange: []
  typeChange: [value: string | number | boolean | undefined]
  upstreamChange: [value: string]
  reloadUpstream: []
  submit: []
}>()

const visible = defineModel<boolean>({ required: true })

const formRef = ref<FormInstance>()

/** 供父组件调用：校验失败时 resolve(false) 而不是抛错 */
const validate = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  return Boolean(valid)
}

defineExpose({ validate })
</script>

<style lang="scss" scoped>
.gateway-page__meta {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.upstream-picker {
  display: flex;
  gap: 8px;
  width: 100%;
}

.upstream-picker__error {
  color: var(--el-color-danger);
}
</style>
