<template>
  <div :class="['message-row', message.role]">
    <div class="message-avatar">
      <el-avatar
        v-if="message.role === 'user'"
        :size="36"
        :style="{ backgroundColor: userAvatarColor }"
      >
        {{ userInitial }}
      </el-avatar>
      <el-avatar v-else :size="36" class="message-avatar--agent">
        <el-icon><Service /></el-icon>
      </el-avatar>
    </div>

    <div class="message-body">
      <div class="message-role">{{ message.role === 'user' ? '我' : agentName }}</div>

      <template v-if="message.role === 'assistant'">
        <div class="message-reply-block">
          <div v-if="message.content" class="message-bubble markdown-body" v-html="htmlContent" />

          <div v-if="message.examples?.length" class="welcome-examples">
            <div class="welcome-examples__label">试试这样问</div>
            <div class="welcome-examples__list">
              <button
                v-for="(example, exampleIndex) in message.examples"
                :key="`${index}-ex-${exampleIndex}`"
                type="button"
                class="welcome-examples__chip"
                :disabled="loading"
                @click="emit('example', example)"
              >
                {{ example }}
              </button>
            </div>
          </div>

          <div v-if="message.structured" class="structured-panel">
            <template v-if="isCompare(message.structured)">
              <div class="structured-title">多站对比 · {{ message.structured.element }}</div>
              <div class="structured-summary">{{ message.structured.summary }}</div>
              <el-table :data="message.structured.items" size="small" border stripe>
                <el-table-column prop="stationAddress" label="站号" min-width="120" />
                <el-table-column prop="observeTime" label="观测时间" min-width="150" />
                <el-table-column prop="value" label="数值" width="100" />
                <el-table-column prop="remark" label="备注" min-width="100" />
              </el-table>
            </template>
            <template v-else-if="isTrend(message.structured)">
              <div class="structured-title">
                趋势分析 · {{ message.structured.stationAddress }} /
                {{ message.structured.element }}
              </div>
              <div class="structured-summary">{{ message.structured.summary }}</div>
              <el-descriptions :column="3" size="small" border class="trend-desc">
                <el-descriptions-item label="样本">
                  {{ message.structured.sampleCount }}
                </el-descriptions-item>
                <el-descriptions-item label="最小">{{
                  message.structured.min
                }}</el-descriptions-item>
                <el-descriptions-item label="最大">{{
                  message.structured.max
                }}</el-descriptions-item>
                <el-descriptions-item label="均值">{{
                  message.structured.avg
                }}</el-descriptions-item>
                <el-descriptions-item label="合计">{{
                  message.structured.sum
                }}</el-descriptions-item>
                <el-descriptions-item label="趋势">
                  {{ message.structured.trend }}
                </el-descriptions-item>
              </el-descriptions>
            </template>
            <template v-else-if="isAlarm(message.structured)">
              <div class="structured-title">
                告警摘要
                <el-tag
                  size="small"
                  :type="alarmTagType(message.structured.level)"
                  style="margin-left: 8px"
                >
                  {{ message.structured.level }}
                </el-tag>
              </div>
              <div class="structured-summary">{{ message.structured.summary }}</div>
              <el-table :data="message.structured.items" size="small" border stripe>
                <el-table-column prop="stationAddress" label="站号" min-width="120" />
                <el-table-column prop="element" label="要素" width="80" />
                <el-table-column prop="currentValue" label="当前值" width="90" />
                <el-table-column prop="threshold" label="阈值" width="90" />
                <el-table-column prop="message" label="说明" min-width="140" />
              </el-table>
            </template>
          </div>

          <AgentTracePanel v-if="message.traces?.length" :traces="message.traces" />

          <div v-if="message.stopped" class="message-stopped">
            <el-icon><VideoPause /></el-icon>
            回复已中断，以上为已接收的部分内容
          </div>

          <div v-if="plainText" class="message-actions">
            <el-button
              link
              type="primary"
              size="small"
              :icon="DocumentCopy"
              @click="emit('copy', plainText)"
            >
              复制
            </el-button>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="message-user-block">
          <div class="message-bubble message-bubble--user" :class="{ 'is-editing': isEditing }">
            {{ message.content }}
          </div>
          <div class="message-actions">
            <el-button
              link
              type="primary"
              size="small"
              :icon="DocumentCopy"
              @click="emit('copy', message.content)"
            >
              复制
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              :icon="EditPen"
              :disabled="loading"
              @click.stop="emit('edit', index, message.content)"
            >
              编辑
            </el-button>
          </div>
        </div>
      </template>

      <div class="message-time">{{ formatTime(message.timestamp) }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DocumentCopy, EditPen, Service, VideoPause } from '@element-plus/icons-vue'
import AgentTracePanel from '@/components/ai/AgentTracePanel.vue'
import { renderMarkdown } from '@/utils/markdown'
import { isAlarmSummary, isStationCompareResult, isTrendAnalysisResult } from '@/types/aiChat'
import type { AgentChatMessage } from '@/types/chatMessage'

defineOptions({ name: 'ChatMessageItem' })

const props = withDefaults(
  defineProps<{
    message: AgentChatMessage
    index: number
    /** 该条用户消息是否正处于编辑态 */
    isEditing?: boolean
    /** 会话是否在等待回复（禁用示例与编辑） */
    loading?: boolean
    /** 助手名称，取当前智能体名 */
    agentName?: string
    userInitial?: string
    userAvatarColor?: string
  }>(),
  {
    isEditing: false,
    loading: false,
    agentName: 'Agent',
    userInitial: '',
    userAvatarColor: '#409eff',
  },
)

const emit = defineEmits<{
  copy: [text: string]
  edit: [index: number, content: string]
  example: [example: string]
}>()

const isCompare = isStationCompareResult
const isTrend = isTrendAnalysisResult
const isAlarm = isAlarmSummary

/** 按消息维度缓存 Markdown 渲染结果：流式逐字刷新时不再整列表重解析 */
const htmlContent = computed(() => renderMarkdown(props.message.content))

/** 复制用的纯文本（含结构化摘要） */
const plainText = computed(() => {
  const parts = [props.message.content?.trim() || '']
  if (props.message.structured?.summary) parts.push(props.message.structured.summary)
  return parts.filter(Boolean).join('\n')
})

const alarmTagType = (level?: string) =>
  level === 'CRITICAL' ? 'danger' : level === 'WARN' ? 'warning' : 'info'

const formatTime = (date: Date): string => {
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
}
</script>

<style lang="scss" scoped>
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 22px;
  animation: chat-fade-in 0.28s ease;

  &.user {
    flex-direction: row-reverse;

    .message-body {
      align-items: flex-end;
    }

    .message-role {
      text-align: right;
    }
  }
}

.message-avatar {
  flex-shrink: 0;
  padding-top: 2px;

  &--agent {
    background: linear-gradient(135deg, #409eff, #1a7fd4) !important;
  }
}

.message-body {
  display: flex;
  flex-direction: column;
  max-width: min(960px, calc(100% - 48px));
  min-width: 0;
}

.message-role {
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
}

.welcome-examples {
  margin-top: 10px;
  max-width: 100%;

  &__label {
    margin-bottom: 8px;
    font-size: 12px;
    font-weight: 600;
    color: #86909c;
  }

  &__list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  &__chip {
    max-width: 100%;
    padding: 6px 12px;
    border: 1px solid rgba(64, 158, 255, 0.35);
    border-radius: 999px;
    background: rgba(64, 158, 255, 0.06);
    color: #1d4f91;
    font-size: 13px;
    line-height: 1.4;
    text-align: left;
    cursor: pointer;
    transition:
      background 0.15s ease,
      border-color 0.15s ease;

    &:hover:not(:disabled) {
      background: rgba(64, 158, 255, 0.12);
      border-color: rgba(64, 158, 255, 0.55);
    }

    &:disabled {
      opacity: 0.55;
      cursor: not-allowed;
    }
  }
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.65;
  word-wrap: break-word;
  background: #fff;
  color: #303133;
  border: 1px solid rgba(228, 231, 237, 0.9);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);

  &--user {
    background: linear-gradient(135deg, #409eff, #2b8ef0);
    color: #fff;
    border: none;
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.28);
    white-space: pre-wrap;

    &.is-editing {
      outline: 2px solid rgba(255, 255, 255, 0.85);
      box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.35);
    }
  }

  &.markdown-body {
    :deep(p) {
      margin-bottom: 8px;
      line-height: 1.8;

      &:last-child {
        margin-bottom: 0;
      }
    }

    :deep(ul),
    :deep(ol) {
      margin: 8px 0;
      padding-left: 20px;
    }

    :deep(code) {
      padding: 2px 6px;
      background-color: rgba(0, 0, 0, 0.06);
      border-radius: 3px;
      font-family: Consolas, Monaco, monospace;
      font-size: 0.9em;
    }

    :deep(pre) {
      margin: 8px 0;
      padding: 12px;
      background-color: #f6f8fa;
      border-radius: 6px;
      overflow-x: auto;

      code {
        padding: 0;
        background-color: transparent;
      }
    }

    :deep(table) {
      margin: 8px 0;
      border-collapse: collapse;
      width: 100%;

      th,
      td {
        padding: 6px 12px;
        border: 1px solid #dcdfe6;
      }

      th {
        background-color: #f5f7fa;
        font-weight: 600;
      }
    }
  }
}

.structured-panel {
  margin-top: 8px;
  padding: 12px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  width: 100%;

  .structured-title {
    font-weight: 600;
    margin-bottom: 6px;
    color: #303133;
  }

  .structured-summary {
    font-size: 13px;
    color: #606266;
    margin-bottom: 10px;
  }

  .trend-desc {
    margin-top: 4px;
  }
}

.message-stopped {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.message-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  margin-top: 4px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;

  :deep(.el-button) {
    padding: 0 4px;
    height: 22px;
    font-size: 12px;
  }
}

.message-user-block,
.message-reply-block {
  max-width: 100%;

  &:hover .message-actions,
  &:focus-within .message-actions {
    opacity: 1;
    pointer-events: auto;
  }
}

.message-user-block {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-row.user .message-actions {
  justify-content: flex-end;
}

.message-time {
  margin-top: 4px;
  font-size: 11px;
  color: #a8abb2;
}

@keyframes chat-fade-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
