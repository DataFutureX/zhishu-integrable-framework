<template>
  <!--
    eslint-disable vue/no-mutating-props
    表单对象由 AgentManage 持有（提交与“生成初稿”共用同一份 reactive 状态），
    本组件定位为受控表单编辑器，故允许就地修改字段。
  -->
  <el-dialog
    v-model="visible"
    :title="editingId ? '编辑智能体' : '新建智能体'"
    width="1080px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="emit('close')"
  >
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
      <el-form-item v-if="!editingId" label="编码" prop="code">
        <el-input v-model="form.code" placeholder="字母开头，如 alert_helper" maxlength="64" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="128" />
      </el-form-item>
      <el-form-item label="简介">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
      </el-form-item>

      <el-form-item label="工作流" prop="workflowType">
        <div class="workflow-picker">
          <button
            v-for="item in workflowTemplates"
            :key="item.code"
            type="button"
            class="workflow-pick"
            :class="[
              `workflow-pick--${workflowTone(item.code)}`,
              { 'is-active': form.workflowType === item.code },
            ]"
            @click="selectWorkflow(item.code)"
          >
            <el-tooltip placement="top" popper-class="agent-workflow-hint-popper">
              <template #content>
                <div class="workflow-hint">
                  <div class="workflow-hint__title">{{ item.label }}</div>
                  <p class="workflow-hint__desc">{{ workflowHint(item) }}</p>
                </div>
              </template>
              <span class="workflow-pick__icon">
                <el-icon :size="20"><component :is="workflowIcon(item.code)" /></el-icon>
              </span>
            </el-tooltip>
            <span class="workflow-pick__label">{{ item.label }}</span>
          </button>
        </div>
      </el-form-item>

      <el-form-item label="能力" prop="capabilities">
        <el-checkbox-group v-model="form.capabilities" class="cap-group">
          <div v-for="cap in capabilities" :key="cap.code" class="cap-option">
            <el-checkbox :label="cap.code" class="cap-option__check">
              <span class="cap-option__title">{{ cap.label }}</span>
              <el-tag v-if="cap.toolBased" size="small" type="primary" effect="plain">Tools</el-tag>
              <el-tag v-else size="small" type="info" effect="plain">非 Tool</el-tag>
            </el-checkbox>
            <p class="cap-option__desc">{{ cap.description }}</p>
            <div v-if="resolveCapabilityTools(cap).length" class="cap-option__tools">
              <el-tooltip
                v-for="tool in resolveCapabilityTools(cap)"
                :key="tool.name"
                :content="tool.description || tool.name"
                placement="top"
                :show-after="150"
              >
                <el-tag size="small" class="tool-chip" effect="plain">
                  {{ tool.name }}
                </el-tag>
              </el-tooltip>
            </div>
          </div>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="系统提示词" prop="systemPrompt">
        <div class="prompt-head">
          <el-button
            type="primary"
            link
            :icon="MagicStick"
            :loading="draftingPrompt"
            @click="emit('generatePrompt')"
          >
            根据表单生成初稿
          </el-button>
        </div>
        <el-input
          v-model="form.systemPrompt"
          type="textarea"
          :rows="8"
          placeholder="可先填名称、简介并勾选能力，再点上方生成初稿"
        />
        <p class="prompt-hint">
          依据名称、简介、工作流、能力、知识库与上游 MCP 生成可再编辑的初稿。
        </p>
      </el-form-item>

      <el-form-item label="网关模型">
        <el-select
          v-model="form.model"
          filterable
          clearable
          :disabled="followGlobalModel"
          placeholder="空则使用网关默认模型"
          style="width: 280px"
        >
          <el-option
            v-for="opt in gatewayModelOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <span class="form-item-hint">来自「模型网关 · 网关模型」，空则使用网关默认模型</span>
      </el-form-item>

      <el-form-item label="模型参数">
        <div class="model-row">
          <el-switch v-model="followGlobal" active-text="跟随全局" inactive-text="自定义" />
        </div>
        <div v-if="!followGlobal" class="model-fields">
          <el-input-number
            v-model="form.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            :precision="2"
          />
          <el-input-number v-model="form.maxTokens" :min="256" :max="16000" :step="256" />
        </div>
      </el-form-item>

      <el-form-item label="多轮记忆">
        <el-switch v-model="form.enableMemory" />
      </el-form-item>

      <el-form-item label="绑定文档">
        <el-select
          v-model="form.documentIds"
          multiple
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          placeholder="空=全部知识库"
          style="width: 100%"
          :loading="docsLoading"
        >
          <el-option
            v-for="doc in documentOptions"
            :key="doc.id"
            :label="doc.label"
            :value="doc.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="上游 MCP">
        <el-select
          v-model="form.mcpUpstreamIds"
          multiple
          filterable
          clearable
          collapse-tags
          placeholder="可选：挂载他方 MCP 工具"
          style="width: 100%"
        >
          <el-option
            v-for="u in mcpUpstreams"
            :key="u.id"
            :label="`${u.name} (${u.code})`"
            :value="u.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio label="ENABLED">启用</el-radio>
          <el-radio label="DISABLED">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="emit('submit')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { CapabilityVO, WorkflowTemplateVO } from '@/types/aiAgent'
import type { McpUpstreamVO } from '@/types/mcp'
import { workflowHint, workflowIcon, workflowTone } from '@/config/agentWorkflow'
import { resolveCapabilityTools } from '@/utils/agentCapability'

/**
 * 智能体新建 / 编辑弹窗（展示型组件）。
 * 表单状态、初稿生成与提交逻辑留在 AgentManage，组件只负责渲染、校验入口与事件转发。
 */
defineOptions({ name: 'AgentFormDialog' })

const props = defineProps<{
  editingId: number | null
  form: {
    code: string
    name: string
    description: string
    systemPrompt: string
    model: string | null
    temperature: number | null
    maxTokens: number | null
    capabilities: string[]
    workflowType: string
    documentIds: number[]
    mcpUpstreamIds: number[]
    enableMemory: boolean
    status: string
  }
  formRules: FormRules
  saving: boolean
  draftingPrompt: boolean
  docsLoading: boolean
  workflowTemplates: WorkflowTemplateVO[]
  capabilities: CapabilityVO[]
  gatewayModelOptions: Array<{ value: string; label: string }>
  documentOptions: Array<{ id: number; label: string }>
  mcpUpstreams: McpUpstreamVO[]
  /** 是否跟随全局模型参数（v-model:follow-global-model） */
  followGlobalModel: boolean
}>()

const emit = defineEmits<{
  'update:followGlobalModel': [value: boolean]
  close: []
  generatePrompt: []
  submit: []
}>()

/* eslint-disable vue/no-mutating-props -- 受控表单：状态由父级持有，见模板注释 */

const visible = defineModel<boolean>({ required: true })

const formRef = ref<FormInstance>()

/** 供父组件调用：校验失败 resolve(false) */
const validate = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  return Boolean(valid)
}

defineExpose({ validate })

/** 切换工作流后即时校验该字段（与原实现一致） */
const selectWorkflow = (code: string) => {
  props.form.workflowType = code
  void formRef.value?.validateField('workflowType')
}

const followGlobal = computed({
  get: () => props.followGlobalModel,
  set: (value: boolean) => emit('update:followGlobalModel', value),
})
</script>

<style lang="scss" scoped>
.cap-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
  align-items: stretch;
}

.prompt-head {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 4px;
}

.prompt-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-placeholder);
}

.form-item-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.workflow-picker {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
}

.workflow-pick {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  color: inherit;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    box-shadow 0.15s ease;

  &--react {
    --wf-tone: #7dd3fc;
  }

  &--sequential {
    --wf-tone: #5eead4;
  }

  &--routing {
    --wf-tone: #fdba74;
  }

  &--graph {
    --wf-tone: #6ee7b7;
  }

  &:hover {
    border-color: color-mix(in srgb, var(--wf-tone) 70%, #94a3b8);
  }

  &.is-active {
    border-color: color-mix(in srgb, var(--wf-tone) 85%, #0891b2);
    background: color-mix(in srgb, var(--wf-tone) 22%, #fff);
    box-shadow: 0 0 0 1px color-mix(in srgb, var(--wf-tone) 35%, transparent);
  }

  &__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 10px;
    color: #0f2740;
    background: linear-gradient(135deg, #fff, var(--wf-tone));
    cursor: help;
  }

  &__label {
    font-size: 12px;
    line-height: 1.3;
    text-align: center;
    color: var(--el-text-color-primary);
  }
}

.cap-option {
  min-width: 0;
  height: 100%;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);

  &__check {
    height: auto;
    align-items: flex-start;

    :deep(.el-checkbox__label) {
      display: inline-flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      white-space: normal;
      line-height: 1.4;
    }
  }

  &__title {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__desc {
    margin: 6px 0 0 24px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }

  &__tools {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin: 8px 0 0 24px;
  }
}

.tool-chip {
  cursor: help;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.model-row {
  margin-bottom: 8px;
}

.model-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
