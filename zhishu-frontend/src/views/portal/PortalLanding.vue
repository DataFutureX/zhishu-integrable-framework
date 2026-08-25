<template>
  <PortalPublicLayout :active-section="activeSection">
    <main id="top">
      <section class="portal-hero">
        <div class="portal-hero__content">
          <p class="portal-hero__tagline">{{ heroTagline }}</p>
          <p class="portal-hero__caps">
            <span v-for="tag in capabilityTags" :key="tag">{{ tag }}</span>
          </p>
          <p class="portal-hero__desc">{{ heroSupport }}</p>
          <div class="portal-hero__actions">
            <button type="button" class="portal-btn-primary portal-btn-primary--lg" @click="goLogin">
              立即体验
            </button>
            <button type="button" class="portal-btn-ghost portal-btn-ghost--lg" @click="goDocs('quickstart')">
              快速开始
            </button>
          </div>
        </div>
      </section>

      <!-- 产品界面：真实工作台作为视觉锚点（public 静态资源） -->
      <section class="portal-preview" aria-label="产品界面预览">
        <img
          class="portal-preview__shot"
          :src="previewPng"
          alt="知枢可集成框架工作台界面"
          width="1440"
          height="900"
          loading="eager"
          fetchpriority="high"
          decoding="async"
        />
      </section>

      <!-- 开源亮点 -->
      <section id="opensource" class="portal-section">
        <div class="portal-section-inner">
          <div class="portal-section-head">
            <p class="portal-section-eyebrow">Open Source</p>
            <h2 class="portal-section-title">开源、可扩展的智能体集成底座</h2>
            <p class="portal-section-desc">{{ introduction }}</p>
          </div>
          <div class="portal-open-grid">
            <article
              v-for="item in openSourceHighlights"
              :key="item.title"
              class="portal-open-item"
            >
              <span class="portal-open-item__mark">{{ item.mark }}</span>
              <h3 class="portal-open-item__title">{{ item.title }}</h3>
              <p class="portal-open-item__desc">{{ item.desc }}</p>
            </article>
          </div>
          <div class="portal-repo-row">
            <a
              v-for="repo in SOURCE_REPOS"
              :key="`row-${repo.key}`"
              class="portal-repo-card"
              :href="repo.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              <span class="portal-repo-card__platform">{{ repo.label }}</span>
              <span class="portal-repo-card__path">DataFutureX/zhishu-integrable-framework</span>
              <span class="portal-repo-card__action">查看仓库 →</span>
            </a>
          </div>
        </div>
      </section>

      <!-- 核心能力 -->
      <section id="features" class="portal-section portal-section--muted">
        <div class="portal-section-inner">
          <div class="portal-section-head">
            <p class="portal-section-eyebrow">Capabilities</p>
            <h2 class="portal-section-title">Agent、检索、图谱、MCP 与 Open API 一体</h2>
            <p class="portal-section-desc">
              以智能体为编排核心，RAG 混合检索增强问答，知识图谱补全关联，MCP 打通上下游工具，Open API 对外开放 AI 能力，并叠加权限、SSO 与运维观测。
            </p>
          </div>
          <div class="portal-feature-list">
            <article
              v-for="(feature, index) in features"
              :key="feature.title"
              class="portal-feature"
            >
              <span class="portal-feature__index">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="portal-feature__body">
                <div class="portal-feature__head">
                  <component :is="feature.icon" class="portal-feature__icon" />
                  <h3 class="portal-feature__title">{{ feature.title }}</h3>
                </div>
                <p class="portal-feature__desc">{{ feature.desc }}</p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <!-- 技术栈 -->
      <section id="stack" class="portal-section">
        <div class="portal-section-inner">
          <div class="portal-section-head">
            <p class="portal-section-eyebrow">Tech Stack</p>
            <h2 class="portal-section-title">统一技术架构，前后端协同交付</h2>
            <p class="portal-section-desc">
              前端包 <code>zhishu-integrable-framework</code>，后端 Maven 模块
              <code>zhishu-*</code>（继承知枢工程结构），包名
              <code>cn.datafuturex.zhishu</code>，工程边界清晰、便于二次开发。
            </p>
          </div>
          <ul class="portal-stack">
            <li v-for="tech in techStack" :key="tech.name" class="portal-stack__item">
              <span class="portal-stack__name">{{ tech.name }}</span>
              <span class="portal-stack__role">{{ tech.role }}</span>
            </li>
          </ul>
        </div>
      </section>

      <section class="portal-cta">
        <div class="portal-section-inner portal-cta__panel">
          <h2 class="portal-cta__title">从知枢开始，编排智能体与开放 AI 能力</h2>
          <p class="portal-cta__desc">
            用 Agent 跑通业务，用 RAG 与图谱加强检索，用 MCP 接入工具链，用 Open API 对外开放 AI 能力，更快交付可集成的智能化应用。
          </p>
          <div class="portal-cta__actions">
            <button type="button" class="portal-btn-primary portal-btn-primary--lg" @click="goLogin">
              登录体验
            </button>
            <button type="button" class="portal-btn-ghost portal-btn-ghost--lg" @click="goDocs('quickstart')">
              查看文档
            </button>
            <a
              class="portal-cta__link"
              href="https://github.com/DataFutureX/zhishu-integrable-framework"
              target="_blank"
              rel="noopener noreferrer"
            >
              GitHub 源码 →
            </a>
          </div>
        </div>
      </section>
    </main>
  </PortalPublicLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  PortalConnectionIcon,
  PortalCpuIcon,
  PortalKeyIcon,
  PortalOdometerIcon,
  PortalOpenApiIcon,
  PortalSearchIcon,
  PortalShareIcon,
  PortalWorkflowIcon,
} from './portalIcons'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'
import { DEFAULT_SYSTEM_INTRODUCTION, DEFAULT_SYSTEM_NAME } from '@/stores/useSystemConfigStore'
import PortalPublicLayout from './PortalPublicLayout.vue'
import { PORTAL_SECTION_NAV, SOURCE_REPOS } from './portalMeta'
import { parsePortalDocRef, portalDocPath, type PortalDocId } from '@/utils/portalDocRoutes'

/** 门户预览图：放 public，不进 JS 包；路由守卫会更早 preload */
const previewPng = '/portal/dashboard.png'

const router = useRouter()
const route = useRoute()
const systemConfigStore = useSystemConfigStore()

const activeSection = ref('')

const systemName = computed(() => systemConfigStore.systemName || DEFAULT_SYSTEM_NAME)
const introduction = computed(
  () => systemConfigStore.systemIntroduction || DEFAULT_SYSTEM_INTRODUCTION,
)

const capabilityTags = ['Agent 智能体', 'RAG', '知识图谱', 'MCP', '工作流', 'Open API']
const heroTagline = '编排智能体，连接知识与工具，开放 AI 能力'
const heroSupport =
  '以 Agent 为核心，融合 RAG 检索、知识图谱、MCP 工具链与工作流编排，通过 Open API 对外开放，帮助企业快速构建可集成的智能化应用。'

const openSourceHighlights = [
  {
    mark: 'MIT',
    title: '宽松开源可商用',
    desc: 'MIT License，可自由使用、修改与商用，适合作为企业数字化应用的二次开发底座。',
  },
  {
    mark: 'MOD',
    title: '模块化后端架构',
    desc: 'zhishu-api / security / biz / ai / core 分层清晰；根目录一键启动前后端，Mock 演示模式无需后端即可体验。',
  },
  {
    mark: 'SSO',
    title: '伙伴单点登录',
    desc: 'Ticket 换票协议已落地，支持 RS256 与国密 SM2；提供 Java Partner SDK，万象 / 数智 IoT 可按文档对接。',
  },
  {
    mark: 'API',
    title: 'Open API 对外开放',
    desc: 'AK/SK HMAC-SHA256 签名鉴权，支持 chat / knowledges / kg 三种 Scope；提供 Java SDK 与多语言签名协议，外部系统可按文档快速对接。',
  },
]

const features = [
  {
    title: 'Agent 智能体',
    desc: '多 Agent 编排与会话，工具调用、工作流与运行轨迹可观测，业务以智能体为入口落地。',
    icon: PortalCpuIcon,
  },
  {
    title: '工作流编排',
    desc: '基于 Vue Flow 的可视化 Graph 编辑器，拖拽连线编排 Agent 执行步骤，复杂业务流程一目了然。',
    icon: PortalWorkflowIcon,
  },
  {
    title: 'RAG 混合检索',
    desc: '向量 + 关键词混合加强检索，知识库问答可追溯片段来源，降低幻觉、提升命中。',
    icon: PortalSearchIcon,
  },
  {
    title: '知识图谱',
    desc: '实体关系可视化与 GraphRAG，补全关联路径与影响面，让检索不止于文档切片。',
    icon: PortalShareIcon,
  },
  {
    title: 'MCP Hub',
    desc: '接入上游 MCP、对外签发 Client，工具目录统一编排，Agent 与外部协议双向打通。',
    icon: PortalConnectionIcon,
  },
  {
    title: 'Open API',
    desc: 'AK/SK 签名鉴权对外开放 AI 对话、知识问答、知识图谱能力；附 Java SDK 与多语言签名协议。',
    icon: PortalOpenApiIcon,
  },
  {
    title: '权限与伙伴 SSO',
    desc: 'RBAC + JWT；本地登录（滑动验证码 + RSA）与伙伴 Ticket 换票（RS256 / 国密 SM2）并存。',
    icon: PortalKeyIcon,
  },
  {
    title: '运维观测闭环',
    desc: '系统监控覆盖 JVM / 数据库 / Web 服务；操作日志按月分表，关键操作可审计。',
    icon: PortalOdometerIcon,
  },
]

const techStack = [
  { name: 'Vue 3.5', role: '前端框架' },
  { name: 'TypeScript', role: '类型安全' },
  { name: 'Element Plus', role: 'UI 组件' },
  { name: 'Vite', role: '前端构建' },
  { name: 'Spring Boot 4', role: '后端框架' },
  { name: 'Spring AI 2', role: 'AI 集成' },
  { name: 'Spring Security 7', role: '认证鉴权' },
  { name: 'Spring Modulith', role: '模块化' },
  { name: 'MyBatis-Plus', role: '数据访问' },
  { name: 'PostgreSQL', role: '数据存储' },
  { name: 'Neo4j', role: '知识图谱' },
  { name: 'MCP', role: '工具协议' },
  { name: 'RAG / GraphRAG', role: '检索增强' },
  { name: 'Open API', role: 'AK/SK 对外开放' },
  { name: 'JWT / RSA / SM2', role: '登录与 SSO' },
]

let sectionObserver: IntersectionObserver | null = null

function goDocs(id: PortalDocId = 'quickstart') {
  void router.push(portalDocPath(id))
}

function goLogin() {
  void router.push('/login')
}

function applyPortalHash() {
  const legacyDoc = parsePortalDocRef(route.hash)
  if (legacyDoc && (route.hash === '#docs' || route.hash.startsWith('#docs/'))) {
    void router.replace(portalDocPath(legacyDoc))
    return
  }
  const id = route.hash.replace(/^#/, '')
  if (!id || id === 'top') return
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function setupSectionObserver() {
  const ids = PORTAL_SECTION_NAV.map((item) => item.id)
  sectionObserver?.disconnect()
  sectionObserver = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)
      const top = visible[0]
      if (top?.target?.id) {
        activeSection.value = top.target.id
      }
    },
    { rootMargin: '-20% 0px -55% 0px', threshold: [0.1, 0.35, 0.6] },
  )
  for (const id of ids) {
    const el = document.getElementById(id)
    if (el) sectionObserver.observe(el)
  }
}

const BAIDU_HM_ID = 'f37a71b15185e3742e12ec24800ad896'
const BAIDU_HM_SCRIPT_ID = 'baidu-hm-portal'

declare global {
  interface Window {
    _hmt?: Array<unknown[]>
  }
}

function ensureBaiduAnalytics() {
  window._hmt = window._hmt || []
  if (document.getElementById(BAIDU_HM_SCRIPT_ID)) {
    window._hmt.push(['_trackPageview', '/portal'])
    return
  }
  const hm = document.createElement('script')
  hm.id = BAIDU_HM_SCRIPT_ID
  hm.async = true
  hm.src = `https://hm.baidu.com/hm.js?${BAIDU_HM_ID}`
  const firstScript = document.getElementsByTagName('script')[0]
  firstScript?.parentNode?.insertBefore(hm, firstScript)
}

watch(() => route.hash, applyPortalHash)

onMounted(() => {
  document.title = `${systemName.value} · 开源门户`
  setupSectionObserver()
  ensureBaiduAnalytics()
  applyPortalHash()
})

onUnmounted(() => {
  sectionObserver?.disconnect()
  sectionObserver = null
})
</script>

<style scoped lang="scss">
// GitHub Primer-inspired tokens
$fg: #1f2328;
$fg-muted: #656d76;
$fg-subtle: #8c959f;
$canvas: #ffffff;
$canvas-subtle: #f6f8fa;
$border: #d0d7de;
$border-muted: #d8dee4;
$accent: #0969da;
$accent-muted: #ddf4ff;
$success: #1f883d;
$success-hover: #1a7f37;
$success-muted: #dafbe1;
$neutral-emphasis: #0969da;
$canvas-dark: #0d1117;
$canvas-dark-subtle: #161b22;
$radius: 6px;
$radius-lg: 12px;
$max: 1120px;

@keyframes portal-rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// ── Hero ──
.portal-hero {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  max-width: $max;
  margin: 0 auto;
  padding: 128px 24px 56px;
  min-height: 68vh;
}

.portal-hero__content {
  max-width: 680px;
  animation: portal-rise 0.7s ease-out both;
}

.portal-hero__brand {
  margin: 0 0 8px;
  font-size: clamp(32px, 5vw, 52px);
  font-weight: 500;
  line-height: 1.1;
  letter-spacing: -0.03em;
  color: $fg;
}

.portal-hero__english {
  margin: 0 0 16px;
  font-family: Outfit, 'Noto Sans SC', sans-serif;
  font-size: clamp(14px, 1.6vw, 18px);
  font-weight: 500;
  letter-spacing: 0.04em;
  color: $fg-muted;
}

.portal-hero__caps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 16px;

  span {
    padding: 4px 10px;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.02em;
    color: $accent;
    background: rgba(9, 105, 218, 0.08);
    border: 1px solid rgba(9, 105, 218, 0.16);
    border-radius: 999px;
  }
}

.portal-hero__tagline {
  margin: 0 0 12px;
  font-size: clamp(28px, 3.4vw, 36px);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.25;
  color: $fg;
  white-space: nowrap;
}

.portal-hero__desc {
  margin: 0 0 24px;
  max-width: 560px;
  font-size: 18px;
  line-height: 1.6;
  color: $fg-muted;
}

.portal-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.portal-btn-primary,
.portal-btn-ghost {
  cursor: pointer;
  font-family: inherit;
  appearance: none;
  line-height: 1.2;
}

.portal-btn-primary {
  height: 40px;
  padding: 0 16px;
  border: 1px solid rgba(31, 35, 40, 0.15);
  border-radius: $radius;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: $success;
  box-shadow: none;

  &:hover,
  &:focus {
    background: $success-hover;
    border-color: rgba(31, 35, 40, 0.15);
    color: #fff;
  }

  &--lg {
    height: 44px;
    padding: 0 20px;
  }
}

.portal-btn-ghost {
  height: 40px;
  padding: 0 16px;
  border: 1px solid $border;
  border-radius: $radius;
  font-size: 14px;
  font-weight: 500;
  color: $fg;
  background: $canvas-subtle;
  box-shadow: none;

  &:hover,
  &:focus {
    color: $fg;
    background: #eff2f5;
    border-color: $border-muted;
  }

  &--lg {
    height: 44px;
    padding: 0 20px;
  }
}

// ── Product preview ──
.portal-preview {
  position: relative;
  z-index: 1;
  padding: 0 24px;
  margin-bottom: -1px;
  background: transparent;
}

.portal-preview__shot {
  display: block;
  width: min(1100px, 100%);
  margin: 0 auto;
  height: auto;
  border: 1px solid $border;
  border-bottom: none;
  border-radius: $radius-lg $radius-lg 0 0;
  box-shadow: 0 8px 24px rgba(31, 35, 40, 0.08);
}

// ── Sections ──
.portal-section {
  position: relative;
  z-index: 1;
  padding: 80px 24px;
  scroll-margin-top: 64px;
  background: $canvas;
  border-top: 1px solid $border;

  &--muted {
    background: $canvas-subtle;
  }
}

.portal-preview + .portal-section {
  padding-top: 64px;
  border-top: none;
}

.portal-section-inner {
  max-width: $max;
  margin: 0 auto;
}

.portal-section-head {
  max-width: 680px;
  margin: 0 0 40px;
}

.portal-section-eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: $accent;
}

.portal-section-title {
  margin: 0 0 12px;
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.25;
  color: $fg;
}

.portal-section-desc {
  margin: 0;
  font-size: 16px;
  line-height: 1.6;
  color: $fg-muted;

  code {
    padding: 1px 6px;
    font-size: 0.92em;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    color: $fg;
    background: rgba(31, 35, 40, 0.06);
    border-radius: 4px;
  }
}

// ── Open source ──
.portal-open-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 32px;
}

.portal-open-item {
  &__mark {
    display: inline-block;
    margin-bottom: 10px;
    padding: 2px 8px;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.04em;
    color: $accent;
    background: $accent-muted;
    border-radius: 999px;
  }

  &__title {
    margin: 0 0 6px;
    font-size: 16px;
    font-weight: 600;
    color: $fg;
  }

  &__desc {
    margin: 0;
    font-size: 14px;
    line-height: 1.6;
    color: $fg-muted;
  }
}

.portal-repo-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.portal-repo-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  text-decoration: none;
  color: inherit;
  background: $canvas;
  border: 1px solid $border;
  border-radius: $radius;
  transition:
    border-color 0.15s,
    background 0.15s;

  &:hover {
    background: $canvas-subtle;
    border-color: $border-muted;
  }

  &__platform {
    font-size: 14px;
    font-weight: 600;
    color: $fg;
  }

  &__path {
    font-size: 13px;
    color: $fg-muted;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__action {
    font-size: 13px;
    font-weight: 500;
    color: $accent;
    white-space: nowrap;
  }
}

// ── Features ──
.portal-feature-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  border-top: 1px solid $border;
}

.portal-feature {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 12px;
  padding: 24px 20px 24px 0;
  border-bottom: 1px solid $border;

  &:nth-child(odd) {
    padding-right: 24px;
    border-right: 1px solid $border;
  }

  &:nth-child(even) {
    padding-left: 24px;
  }

  &__index {
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.04em;
    color: $fg-subtle;
    padding-top: 4px;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }

  &__icon {
    width: 20px;
    height: 20px;
    flex-shrink: 0;
    color: $accent;
  }

  &__title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: $fg;
  }

  &__desc {
    margin: 0;
    font-size: 14px;
    line-height: 1.6;
    color: $fg-muted;
  }
}

// ── Tech stack ──
.portal-stack {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.portal-stack__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px;
  background: $canvas;
  border: 1px solid $border;
  border-radius: $radius;
}

.portal-stack__name {
  font-size: 14px;
  font-weight: 600;
  color: $fg;
}

.portal-stack__role {
  font-size: 12px;
  color: $fg-muted;
}

// ── CTA ──
.portal-cta {
  position: relative;
  z-index: 1;
  padding: 24px 24px 80px;
  background: $canvas;
  border-top: 1px solid $border;
}

.portal-cta__panel {
  padding: 48px 32px;
  text-align: center;
  background: $canvas-subtle;
  border: 1px solid $border;
  border-radius: $radius-lg;
}

.portal-cta__title {
  margin: 0 0 8px;
  font-size: clamp(22px, 2.8vw, 28px);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: $fg;
}

.portal-cta__desc {
  margin: 0 auto 24px;
  max-width: 480px;
  font-size: 15px;
  line-height: 1.6;
  color: $fg-muted;
}

.portal-cta__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.portal-cta__link {
  font-size: 14px;
  font-weight: 600;
  color: $accent;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

// ── Responsive ──
@media (max-width: 1024px) {
  .portal-hero {
    min-height: auto;
    padding-top: 112px;
    padding-bottom: 40px;
    align-items: center;
  }

  .portal-open-grid,
  .portal-stack {
    grid-template-columns: repeat(2, 1fr);
  }

  .portal-feature-list {
    grid-template-columns: 1fr;
  }

  .portal-feature {
    padding-left: 0 !important;
    padding-right: 0 !important;
    border-right: none !important;
  }
}

@media (max-width: 768px) {
  .portal-open-grid,
  .portal-repo-row,
  .portal-stack {
    grid-template-columns: 1fr;
  }

  .portal-preview {
    padding: 0 16px;
  }

  .portal-preview__shot {
    border-radius: $radius $radius 0 0;
  }

  .portal-cta__panel {
    padding: 32px 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .portal-hero__content {
    animation: none !important;
  }
}
</style>
