<template>
  <div class="gateway-page">
    <el-card shadow="never">
      <template #header>
        <span class="gateway-page__title">接入说明</span>
      </template>

      <el-descriptions :column="1" border class="gateway-page__desc">
        <el-descriptions-item label="Base URL">
          <code>{{ baseUrl }}</code>
          <span class="gateway-page__meta">（客户端拼接 <code>/v1</code>）</span>
        </el-descriptions-item>
        <el-descriptions-item label="鉴权">
          <code>Authorization: Bearer &lt;知枢 JWT&gt;</code>
          <span class="gateway-page__meta">桌面端使用登录后拿到的 token，平台内部自动透传</span>
        </el-descriptions-item>
        <el-descriptions-item label="通道标识（可选）">
          <code>X-Zhishu-Channel: DESKTOP | CONSOLE | AGENT | BRIEFING | OPENAPI | TEST</code>
        </el-descriptions-item>
      </el-descriptions>

      <h4 class="gateway-page__section">端点</h4>
      <el-table :data="endpoints" size="small" class="modern-table">
        <el-table-column prop="method" label="方法" width="80" />
        <el-table-column prop="path" label="路径" width="240" />
        <el-table-column prop="desc" label="说明" />
      </el-table>

      <h4 class="gateway-page__section">调用示例</h4>
      <pre class="gateway-page__code">{{ curlSample }}</pre>

      <h4 class="gateway-page__section">错误码</h4>
      <el-table :data="errorCodes" size="small" class="modern-table">
        <el-table-column prop="status" label="HTTP" width="80" />
        <el-table-column prop="code" label="error.code" width="220" />
        <el-table-column prop="desc" label="含义" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'GatewayGuide' })

const baseUrl = computed(() => window.location.origin)

const endpoints = [
  { method: 'GET', path: '/v1/models', desc: '模型目录（含能力元数据与默认模型）' },
  { method: 'POST', path: '/v1/chat/completions', desc: '推理转发（支持 SSE 流式，末帧带 usage）' },
  { method: 'GET', path: '/v1/quota', desc: '当前账号额度：limit / used / remaining / reset_at' },
]

const errorCodes = [
  { status: 401, code: 'invalid_api_key', desc: '缺少或无效 token' },
  { status: 401, code: 'token_revoked', desc: 'token 已吊销（登出/改密）' },
  { status: 403, code: 'account_disabled', desc: '账号不存在或已禁用' },
  { status: 404, code: 'model_not_found', desc: '模型不存在或当前账号不可见' },
  { status: 429, code: 'insufficient_quota', desc: '月度 token 额度已用尽（不重试）' },
  { status: 503, code: 'upstream_unavailable', desc: '上游不可达或未配置 Key' },
  { status: 504, code: 'upstream_timeout', desc: '上游超时' },
]

const curlSample = `curl -X POST http://127.0.0.1:8180/v1/chat/completions \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer <知枢 JWT>" \\
  -H "X-Zhishu-Channel: DESKTOP" \\
  -d '{
    "model": "gw-dashscope-qwen-plus",
    "messages": [{"role": "user", "content": "你好"}],
    "stream": true
  }'`
</script>

<style scoped>
.gateway-page__title {
  font-size: 15px;
  font-weight: 600;
}

.gateway-page__desc {
  margin-bottom: 8px;
}

.gateway-page__section {
  margin: 20px 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.gateway-page__code {
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-size: 12px;
  line-height: 1.7;
  overflow-x: auto;
  white-space: pre;
}

.gateway-page__meta {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
