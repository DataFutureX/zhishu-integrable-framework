<template>
  <PortalPublicLayout :active-section="activeSection">
    <template #sky>
      <SkyCloud variant="near" top="14%" left="-8%" width="380px" duration="36s" />
      <SkyCloud variant="mid" flip top="28%" left="38%" width="460px" duration="48s" delay="-14s" />
      <SkyCloud
        variant="far"
        flip
        top="48%"
        right="-4%"
        left="auto"
        width="300px"
        duration="54s"
        delay="-22s"
      />
    </template>

    <main id="top">
      <!-- Hero：品牌 + 定位 + 一句说明 + CTA；天空为全幅视觉平面 -->
      <section class="portal-hero">
        <div class="portal-hero__content">
          <h1 class="portal-hero__brand">{{ systemName }}</h1>
          <p class="portal-hero__english">{{ englishTitle }}</p>
          <p class="portal-hero__tagline">{{ heroTagline }}</p>
          <p class="portal-hero__desc">{{ heroSupport }}</p>
          <div class="portal-hero__actions">
            <el-button type="primary" size="large" class="portal-btn-primary" @click="goLogin">
              立即体验
            </el-button>
            <el-button size="large" class="portal-btn-ghost" @click="goDocs('quickstart')">
              快速开始
            </el-button>
          </div>
        </div>
      </section>

      <!-- 产品界面：真实工作台作为视觉锚点（public 静态资源 + WebP，优先加载） -->
      <section class="portal-preview" aria-label="产品界面预览">
        <picture>
          <source type="image/webp" :srcset="previewWebp" />
          <img
            class="portal-preview__shot"
            :src="previewPng"
            alt="云起应用平台工作台界面"
            width="1280"
            height="800"
            loading="eager"
            fetchpriority="high"
            decoding="async"
          />
        </picture>
      </section>

      <!-- 开源亮点 -->
      <section id="opensource" class="portal-section">
        <div class="portal-section-inner">
          <div class="portal-section-head">
            <p class="portal-section-eyebrow">Open Source</p>
            <h2 class="portal-section-title">开源、可扩展的应用开发底座</h2>
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
              <span class="portal-repo-card__path">DataFutureX/yunqi-application-platform</span>
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
            <h2 class="portal-section-title">模块化能力，支撑智能化应用落地</h2>
            <p class="portal-section-desc">
              以统一技术架构为骨架，沉淀业务组件、权限安全、伙伴 SSO
              与运维观测，并为行业系统接入预留清晰路径。
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
                  <el-icon :size="20" class="portal-feature__icon">
                    <component :is="feature.icon" />
                  </el-icon>
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
              前端包 <code>yunqi-application-platform</code>，后端 Maven 模块
              <code>yqap-*</code>，包名
              <code>cn.datafuturex.yunqi</code>，工程边界清晰、便于二次开发。
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
          <h2 class="portal-cta__title">从云起开始，构建智能化应用</h2>
          <p class="portal-cta__desc">
            云起应用平台提供模块化开发底座，帮助企业更快交付数字化与智能化应用。
          </p>
          <div class="portal-cta__actions">
            <el-button
              type="primary"
              size="large"
              class="portal-btn-primary portal-btn-primary--lg"
              @click="goLogin"
            >
              登录体验
            </el-button>
            <el-button size="large" class="portal-btn-ghost" @click="goDocs('quickstart')">
              查看文档
            </el-button>
            <a
              class="portal-cta__link"
              href="https://github.com/DataFutureX/yunqi-application-platform"
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
import { Connection, Cpu, Grid, Key, Odometer, Setting } from '@element-plus/icons-vue'
import SkyCloud from '@/components/common/SkyCloud.vue'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'
import { DEFAULT_SYSTEM_INTRODUCTION, DEFAULT_SYSTEM_NAME } from '@/stores/useSystemConfigStore'
import PortalPublicLayout from './PortalPublicLayout.vue'
import { PORTAL_SECTION_NAV, SOURCE_REPOS } from './portalMeta'
import { parsePortalDocRef, portalDocPath, type PortalDocId } from './portalMarkdown'

/** 门户预览图：放 public，不进 JS 包；路由守卫会更早 preload */
const previewWebp = '/portal/dashboard.webp'
const previewPng = '/portal/dashboard.png'

const router = useRouter()
const route = useRoute()
const systemConfigStore = useSystemConfigStore()

const activeSection = ref('')

const systemName = computed(() => systemConfigStore.systemName || DEFAULT_SYSTEM_NAME)
const englishTitle = computed(() => systemConfigStore.displayEnglishTitle)
const introduction = computed(
  () => systemConfigStore.systemIntroduction || DEFAULT_SYSTEM_INTRODUCTION,
)

const heroTagline = '企业数字化应用建设的模块化开发基础平台'
const heroSupport =
  '统一技术架构、业务组件、伙伴单点登录与运维观测，帮助企业更快构建可落地的数字化应用。'

const openSourceHighlights = [
  {
    mark: 'MIT',
    title: '宽松开源可商用',
    desc: 'MIT License，可自由使用、修改与商用，适合作为企业数字化应用的二次开发底座。',
  },
  {
    mark: 'MOD',
    title: '模块化后端架构',
    desc: 'yqap-api / security / biz / core 分层清晰；security 含 JWT 登录与伙伴 SSO 换票，Spring Modulith 支撑模块独立演进。',
  },
  {
    mark: 'SSO',
    title: '伙伴单点登录',
    desc: 'Ticket 换票协议已落地，支持 RS256 与国密 SM2；提供 Java Partner SDK，万象 / 数智 IoT 可按文档对接。',
  },
  {
    mark: 'ONE',
    title: '一键联调启动',
    desc: '仓库根目录 start.bat / start.ps1 / start.sh，同时拉起前端与后端开发服务。',
  },
]

const features = [
  {
    title: '统一技术架构',
    desc: 'Vue 3 + Spring Boot 4 前后端一体交付，约定统一、工程边界清晰，降低应用搭建成本。',
    icon: Cpu,
  },
  {
    title: '业务组件开箱',
    desc: '用户、角色、菜单、单位、系统配置、公告与操作日志等能力即用，支撑组织与运维闭环。',
    icon: Grid,
  },
  {
    title: '权限安全体系',
    desc: 'RBAC + JWT；本地登录（滑动验证码 + RSA）与伙伴 SSO（Ticket 换票，RS256 / 国密 SM2）并存。',
    icon: Key,
  },
  {
    title: '伙伴系统接入',
    desc: '万象、数智 IoT 等通过 Partner SDK 签发短期 Ticket，经 /sso/callback 换票进入云起，沿用本地 RBAC。',
    icon: Connection,
  },
  {
    title: '行业扩展能力',
    desc: '业务模块可插拔扩展，按行业沉淀组件与流程；新增 SSO 来源只需登记公钥，不必新增换票接口。',
    icon: Setting,
  },
  {
    title: '运维观测闭环',
    desc: '工作台总览运行状态；系统监控覆盖 JVM / 数据库 / Web 服务；操作日志按月分表，关键操作可审计。',
    icon: Odometer,
  },
]

const techStack = [
  { name: 'Vue 3', role: '前端框架' },
  { name: 'TypeScript', role: '类型安全' },
  { name: 'Element Plus', role: 'UI 组件' },
  { name: 'Vite', role: '前端构建' },
  { name: 'Spring Boot 4', role: '后端框架' },
  { name: 'Spring Security 7', role: '认证鉴权' },
  { name: 'Spring Modulith', role: '模块化' },
  { name: 'MyBatis-Plus', role: '数据访问' },
  { name: 'MySQL 8', role: '数据存储' },
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
  font-size: clamp(40px, 6.5vw, 64px);
  font-weight: 700;
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

.portal-hero__tagline {
  margin: 0 0 12px;
  font-size: clamp(20px, 2.4vw, 28px);
  font-weight: 500;
  line-height: 1.35;
  color: $fg;
}

.portal-hero__desc {
  margin: 0 0 24px;
  max-width: 480px;
  font-size: 18px;
  line-height: 1.6;
  color: $fg-muted;
}

.portal-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.portal-btn-primary {
  height: 40px !important;
  padding: 0 16px !important;
  border: 1px solid rgba(31, 35, 40, 0.15) !important;
  border-radius: $radius !important;
  font-size: 14px !important;
  font-weight: 600 !important;
  color: #fff !important;
  background: $success !important;
  box-shadow: none !important;

  &:hover,
  &:focus {
    background: $success-hover !important;
    border-color: rgba(31, 35, 40, 0.15) !important;
    color: #fff !important;
  }

  &--lg {
    height: 44px !important;
    padding: 0 20px !important;
  }
}

.portal-btn-ghost {
  height: 40px !important;
  padding: 0 16px !important;
  border: 1px solid $border !important;
  border-radius: $radius !important;
  font-size: 14px !important;
  font-weight: 500 !important;
  color: $fg !important;
  background: $canvas-subtle !important;
  box-shadow: none !important;

  &:hover,
  &:focus {
    color: $fg !important;
    background: #eff2f5 !important;
    border-color: $border-muted !important;
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
  width: min($max, 100%);
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
  .portal-hero__content,
  :deep(.sky-cloud) {
    animation: none !important;
  }
}
</style>
