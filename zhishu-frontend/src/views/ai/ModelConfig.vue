<template>
  <div v-loading="loading" class="model-config-page">
    <header class="page-header">
      <div class="page-header__main">
        <h1 class="page-header__title">模型设置</h1>
        <p class="page-header__desc">
          配置对话模型、向量模型、生成参数与 API Key。密钥加密后写入数据库，接口只回脱敏值。
        </p>
      </div>
      <p v-if="formData.updateTime" class="page-header__meta">
        最近更新 {{ formatTime(formData.updateTime) }}
      </p>
    </header>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="140px"
      class="config-form"
    >
      <section class="config-section">
        <div class="config-section__head">
          <el-icon :size="18"><Cpu /></el-icon>
          <div>
            <div class="config-section__title">对话模型</div>
            <div class="config-section__desc">智能问答与 Tool Calling 使用的聊天模型</div>
          </div>
        </div>
        <el-form-item label="对话模型" prop="chatModel">
          <el-select
            v-model="formData.chatModel"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入模型名"
            style="width: 360px"
          >
            <el-option
              v-for="m in chatModelOptions"
              :key="m"
              :label="m"
              :value="m"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="温度 Temperature" prop="temperature">
          <div class="slider-row">
            <el-slider v-model="formData.temperature" :min="0" :max="2" :step="0.05" style="width: 280px" />
            <el-input-number v-model="formData.temperature" :min="0" :max="2" :step="0.05" :precision="2" />
          </div>
        </el-form-item>
        <el-form-item label="最大 Token" prop="maxTokens">
          <el-input-number v-model="formData.maxTokens" :min="256" :max="8192" :step="256" />
        </el-form-item>
        <el-form-item label="Top P" prop="topP">
          <div class="slider-row">
            <el-slider v-model="formData.topP" :min="0" :max="1" :step="0.05" style="width: 280px" />
            <el-input-number v-model="formData.topP" :min="0" :max="1" :step="0.05" :precision="2" />
          </div>
        </el-form-item>
        <el-form-item label="默认开启知识库">
          <el-switch v-model="formData.enableRagDefault" active-text="开启" inactive-text="关闭" />
          <span class="form-hint">智能问答未显式指定时，是否默认启用 RAG</span>
        </el-form-item>
        <el-form-item label="记忆窗口" prop="memoryWindowSize">
          <el-input-number v-model="formData.memoryWindowSize" :min="4" :max="100" :step="2" />
          <span class="form-hint">多轮会话保留的最近消息条数（需重启服务才完全生效于 Memory Bean）</span>
        </el-form-item>
      </section>

      <section class="config-section">
        <div class="config-section__head">
          <el-icon :size="18"><Connection /></el-icon>
          <div>
            <div class="config-section__title">向量模型</div>
            <div class="config-section__desc">文档向量化与知识库检索使用；变更后建议对存量文档重新处理</div>
          </div>
        </div>
        <el-form-item label="向量模型" prop="embeddingModel">
          <el-select
            v-model="formData.embeddingModel"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入模型名"
            style="width: 360px"
          >
            <el-option
              v-for="m in embeddingModelOptions"
              :key="m"
              :label="m"
              :value="m"
            />
          </el-select>
        </el-form-item>
      </section>

      <section class="config-section">
        <div class="config-section__head">
          <el-icon :size="18"><Key /></el-icon>
          <div>
            <div class="config-section__title">接入信息</div>
            <div class="config-section__desc">
              API Key 加密存库，页面不回显明文。留空保存表示不改动已有密钥。
            </div>
          </div>
        </div>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input
            v-model="formData.baseUrl"
            placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1"
            class="readonly-input"
          />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="formData.apiKey"
            type="password"
            show-password
            clearable
            :placeholder="formData.apiKeyConfigured ? '已保存，输入新值可更换' : '请输入模型 API Key'"
            class="readonly-input"
          />
          <span v-if="formData.apiKeyMasked" class="form-hint">当前：{{ formData.apiKeyMasked }}</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="可选说明"
            style="max-width: 560px"
          />
        </el-form-item>
      </section>

      <div class="form-actions">
        <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
        <el-button :disabled="saving" @click="fetchConfig">重新加载</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'AIModelConfig' })

import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Connection, Cpu, Key } from '@element-plus/icons-vue'
import { getAiModelConfig, updateAiModelConfig } from '@/api/ai'
import type { AiModelConfigVO } from '@/types/aiModelConfig'
import { useRouteActivate } from '@/composables/useRouteActivate'

const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const chatModelOptions = ref<string[]>([])
const embeddingModelOptions = ref<string[]>([])

const formData = reactive({
  chatModel: 'qwen-plus',
  embeddingModel: 'qwen3.7-text-embedding',
  temperature: 0.7,
  maxTokens: 2000,
  topP: 0.9,
  enableRagDefault: false,
  memoryWindowSize: 20,
  baseUrl: '',
  apiKey: '',
  apiKeyMasked: '',
  apiKeyConfigured: false,
  remark: '',
  updateTime: '' as string | null,
})

const formRules: FormRules = {
  chatModel: [{ required: true, message: '请选择对话模型', trigger: 'change' }],
  embeddingModel: [{ required: true, message: '请选择向量模型', trigger: 'change' }],
  temperature: [{ required: true, message: '请设置温度', trigger: 'change' }],
  maxTokens: [{ required: true, message: '请设置最大 Token', trigger: 'change' }],
  topP: [{ required: true, message: '请设置 Top P', trigger: 'change' }],
  memoryWindowSize: [{ required: true, message: '请设置记忆窗口', trigger: 'change' }],
}

const applyConfig = (config: AiModelConfigVO) => {
  formData.chatModel = config.chatModel
  formData.embeddingModel = config.embeddingModel
  formData.temperature = Number(config.temperature)
  formData.maxTokens = config.maxTokens
  formData.topP = Number(config.topP)
  formData.enableRagDefault = !!config.enableRagDefault
  formData.memoryWindowSize = config.memoryWindowSize
  formData.baseUrl = config.baseUrl || ''
  formData.apiKey = ''
  formData.apiKeyMasked = config.apiKeyMasked || ''
  formData.apiKeyConfigured = !!config.apiKeyConfigured
  formData.remark = config.remark || ''
  formData.updateTime = config.updateTime || null
  chatModelOptions.value = config.chatModelOptions?.length
    ? config.chatModelOptions
    : [config.chatModel]
  embeddingModelOptions.value = config.embeddingModelOptions?.length
    ? config.embeddingModelOptions
    : [config.embeddingModel]
}

const fetchConfig = async () => {
  loading.value = true
  try {
    const config = await getAiModelConfig()
    applyConfig(config)
  } catch (error) {
    console.error('加载模型配置失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const config = await updateAiModelConfig({
      chatModel: formData.chatModel.trim(),
      embeddingModel: formData.embeddingModel.trim(),
      temperature: formData.temperature,
      maxTokens: formData.maxTokens,
      topP: formData.topP,
      enableRagDefault: formData.enableRagDefault,
      memoryWindowSize: formData.memoryWindowSize,
      baseUrl: formData.baseUrl.trim() || null,
      apiKey: formData.apiKey.trim() || null,
      remark: formData.remark || null,
    })
    applyConfig(config)
    ElMessage.success('模型配置已保存，后续调用将使用新参数')
  } catch (error) {
    console.error('保存模型配置失败:', error)
  } finally {
    saving.value = false
  }
}

const formatTime = (value?: string | null) => {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString()
}

useRouteActivate(fetchConfig)
onMounted(fetchConfig)
</script>

<style lang="scss" scoped>
.model-config-page {
  max-width: 960px;
  padding: 8px 4px 32px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;

  &__title {
    margin: 0 0 6px;
    font-size: 22px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__desc {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1.6;
    max-width: 640px;
  }

  &__meta {
    margin: 0;
    color: var(--el-text-color-placeholder);
    font-size: 12px;
    white-space: nowrap;
  }
}

.config-section {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 18px 20px 8px;
  margin-bottom: 16px;

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    margin-bottom: 16px;
    color: var(--el-color-primary);
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__desc {
    margin-top: 2px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.slider-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.readonly-input {
  max-width: 560px;
}

.form-actions {
  display: flex;
  gap: 12px;
  padding-top: 8px;
}
</style>
