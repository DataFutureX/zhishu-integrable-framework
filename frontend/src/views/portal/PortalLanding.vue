<template>
  <div class="portal">
    <div class="portal__sky" aria-hidden="true">
      <div class="portal__sky-wash" />
      <div class="portal__sky-glow portal__sky-glow--a" />
      <div class="portal__sky-glow portal__sky-glow--b" />
      <SkyCloud
        variant="near"
        top="14%"
        left="-8%"
        width="380px"
        duration="36s"
      />
      <SkyCloud
        variant="mid"
        flip
        top="28%"
        left="38%"
        width="460px"
        duration="48s"
        delay="-14s"
      />
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
    </div>

    <header
      class="portal-header"
      :class="{ 'portal-header--scrolled': scrolled, 'portal-header--menu-open': menuOpen }"
    >
      <div class="portal-header__inner">
        <a class="portal-brand" href="#top" @click.prevent="scrollTo('#top')">
          <img :src="systemIconUrl" alt="" class="portal-brand__icon" />
          <span class="portal-brand__name">{{ systemName }}</span>
        </a>
        <nav class="portal-nav" aria-label="页面导航">
          <a
            v-for="item in navItems"
            :key="item.id"
            :href="`#${item.id}`"
            class="portal-nav__link"
            :class="{ 'portal-nav__link--active': activeSection === item.id }"
            @click.prevent="scrollTo(`#${item.id}`)"
          >
            {{ item.label }}
          </a>
        </nav>
        <div class="portal-header__actions">
          <a
            class="portal-header__studio"
            :href="studioPortal.url"
            target="_blank"
            rel="noopener noreferrer"
            :title="studioPortal.label"
          >
            {{ studioPortal.label }}
          </a>
          <a
            v-for="repo in sourceRepos"
            :key="repo.key"
            class="portal-header__repo"
            :href="repo.url"
            target="_blank"
            rel="noopener noreferrer"
            :aria-label="repo.label"
            :title="repo.label"
          >
            <svg
              class="portal-header__repo-icon"
              :viewBox="repo.iconViewBox"
              aria-hidden="true"
            >
              <path fill="currentColor" :d="repo.iconPath" />
            </svg>
          </a>
          <el-button type="primary" class="portal-header__cta" @click="goLogin">
            在线体验
          </el-button>
          <button
            type="button"
            class="portal-header__menu"
            :aria-expanded="menuOpen"
            aria-controls="portal-mobile-nav"
            aria-label="菜单"
            @click="menuOpen = !menuOpen"
          >
            <span />
            <span />
            <span />
          </button>
        </div>
      </div>
      <nav
        id="portal-mobile-nav"
        class="portal-nav-mobile"
        aria-label="移动端导航"
        :hidden="!menuOpen"
      >
        <a
          v-for="item in navItems"
          :key="`m-${item.id}`"
          :href="`#${item.id}`"
          class="portal-nav-mobile__link"
          :class="{ 'portal-nav-mobile__link--active': activeSection === item.id }"
          @click.prevent="scrollTo(`#${item.id}`)"
        >
          {{ item.label }}
        </a>
        <a
          class="portal-nav-mobile__link portal-nav-mobile__link--external"
          :href="studioPortal.url"
          target="_blank"
          rel="noopener noreferrer"
          @click="menuOpen = false"
        >
          {{ studioPortal.label }}
        </a>
      </nav>
    </header>

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
            <el-button size="large" class="portal-btn-ghost" @click="scrollTo('#quickstart')">
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
            <article v-for="item in openSourceHighlights" :key="item.title" class="portal-open-item">
              <span class="portal-open-item__mark">{{ item.mark }}</span>
              <h3 class="portal-open-item__title">{{ item.title }}</h3>
              <p class="portal-open-item__desc">{{ item.desc }}</p>
            </article>
          </div>
          <div class="portal-repo-row">
            <a
              v-for="repo in sourceRepos"
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
              以统一技术架构为骨架，沉淀业务组件、权限安全与运维观测，并为 AI 能力与行业扩展预留清晰接入路径。
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
              前端包 <code>yunqi-application-platform</code>，后端 Maven 模块 <code>yqap-*</code>，包名
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

      <!-- 快速开始（含上手路径） -->
      <section id="quickstart" class="portal-section portal-section--muted">
        <div class="portal-section-inner">
          <div class="portal-section-head">
            <p class="portal-section-eyebrow">Quick Start</p>
            <h2 class="portal-section-title">快速开始</h2>
            <p class="portal-section-desc">
              先体验产品能力，再克隆源码；演示模式无需后端，联调可用根目录一键启动脚本。
            </p>
          </div>
          <div class="portal-path">
            <div v-for="(step, index) in workflowSteps" :key="step.title" class="portal-path__step">
              <span class="portal-path__num">{{ index + 1 }}</span>
              <h3 class="portal-path__title">{{ step.title }}</h3>
              <p class="portal-path__desc">{{ step.desc }}</p>
            </div>
          </div>
          <div class="portal-commands">
            <article
              v-for="cmd in quickStartCommands"
              :key="cmd.title"
              class="portal-command"
            >
              <div class="portal-command__head">
                <h3 class="portal-command__title">{{ cmd.title }}</h3>
                <p class="portal-command__hint">{{ cmd.hint }}</p>
              </div>
              <pre class="portal-command__code"><code>{{ cmd.code }}</code></pre>
            </article>
          </div>
          <details class="portal-readme-more">
            <summary>查看完整 README</summary>
            <article class="portal-readme" v-html="quickStartHtml" />
          </details>
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

    <footer class="portal-footer">
      <div class="portal-footer__inner">
        <div class="portal-footer__brand">
          <img :src="systemIconUrl" alt="" class="portal-footer__icon" />
          <div>
            <span class="portal-footer__name">{{ systemName }}</span>
            <span class="portal-footer__license">YunQi Application Platform · MIT</span>
          </div>
        </div>
        <div class="portal-footer__links">
          <a
            :href="studioPortal.url"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ studioPortal.label }}
          </a>
          <a
            v-for="repo in sourceRepos"
            :key="`ft-${repo.key}`"
            :href="repo.url"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ repo.label }}
          </a>
          <a href="https://yunqi.datafuturex.cn" target="_blank" rel="noopener noreferrer">
            在线演示
          </a>
        </div>
        <p class="portal-footer__copy">{{ copyright }}</p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { Connection, Cpu, Grid, Key, Odometer, Setting } from '@element-plus/icons-vue'
import SkyCloud from '@/components/common/SkyCloud.vue'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'
import {
  DEFAULT_COPYRIGHT,
  DEFAULT_SYSTEM_INTRODUCTION,
  DEFAULT_SYSTEM_NAME,
} from '@/stores/useSystemConfigStore'
import quickStartSource from '../../../../README.md?raw'

/** 门户预览图：放 public，不进 JS 包；路由守卫会更早 preload */
const previewWebp = '/portal/dashboard.webp'
const previewPng = '/portal/dashboard.png'

const router = useRouter()
const systemConfigStore = useSystemConfigStore()

const scrolled = ref(false)
const menuOpen = ref(false)
const activeSection = ref('')

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: false,
})

const defaultLinkOpen =
  md.renderer.rules.link_open ??
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const href = token.attrGet('href') ?? ''
  if (/^https?:\/\//i.test(href)) {
    token.attrSet('target', '_blank')
    token.attrSet('rel', 'noopener noreferrer')
  }
  return defaultLinkOpen(tokens, idx, options, env, self)
}

md.renderer.rules.image = () => ''

/** 门户完整 README：去掉截图章节与图片 */
function preparePortalQuickStartMarkdown(source: string): string {
  let text = source
    .replace(/^## 界面一览[\s\S]*?(?=^## )/m, '')
    .replace(/!\[[^\]]*\]\([^)]*\)\s*/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  text = text.replace(/^#\s+.+\n+/, '')
  return text
}

const quickStartHtml = computed(() => md.render(preparePortalQuickStartMarkdown(quickStartSource)))

const systemName = computed(() => systemConfigStore.systemName || DEFAULT_SYSTEM_NAME)
const englishTitle = computed(() => systemConfigStore.displayEnglishTitle)
const systemIconUrl = computed(() => systemConfigStore.iconUrl)
const copyright = computed(() => systemConfigStore.copyright || DEFAULT_COPYRIGHT)
const introduction = computed(
  () => systemConfigStore.systemIntroduction || DEFAULT_SYSTEM_INTRODUCTION,
)

const heroTagline = '企业数字化应用建设的模块化开发基础平台'
const heroSupport =
  '通过统一技术架构、业务组件、AI 能力与行业扩展能力，帮助企业快速构建智能化应用系统。'

/** 组织门户：数智未来AI工坊 */
const studioPortal = {
  label: '数智未来AI工坊',
  url: 'https://www.datafuturex.cn/',
}

const navItems = [
  { id: 'opensource', label: '开源' },
  { id: 'features', label: '能力' },
  { id: 'stack', label: '技术栈' },
  { id: 'quickstart', label: '快速开始' },
]

const sourceRepos = [
  {
    key: 'github',
    label: 'GitHub',
    url: 'https://github.com/DataFutureX/yunqi-application-platform',
    iconViewBox: '0 0 24 24',
    iconPath:
      'M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.17 6.839 9.49.5.09.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.464-1.11-1.464-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.578 9.578 0 0 1 12 6.836c.85.004 1.705.114 2.504.336 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.202 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.138 20.167 22 16.418 22 12c0-5.523-4.477-10-10-10Z',
  },
  {
    key: 'gitee',
    label: 'Gitee',
    url: 'https://gitee.com/DataFutureX/yunqi-application-platform',
    iconViewBox: '0 0 1024 1024',
    iconPath:
      'M512 1024C230.4 1024 0 793.6 0 512S230.4 0 512 0s512 230.4 512 512-230.4 512-512 512z m259.2-569.6H480c-12.8 0-25.6 12.8-25.6 25.6v64c0 12.8 12.8 25.6 25.6 25.6h177.6c12.8 0 25.6 12.8 25.6 25.6v12.8c0 41.6-33.6 75.2-75.2 75.2H390.4c-12.8 0-25.6-12.8-25.6-25.6V416c0-41.6 33.6-75.2 75.2-75.2h332.8c12.8 0 25.6-12.8 25.6-25.6v-64c0-12.8-12.8-25.6-25.6-25.6H440c-102.4 0-185.6 83.2-185.6 185.6v332.8c0 12.8 12.8 25.6 25.6 25.6h374.4c89.6 0 163.2-73.6 163.2-163.2v-134.4c0-12.8-12.8-25.6-25.6-25.6z',
  },
]

const openSourceHighlights = [
  {
    mark: 'MIT',
    title: '宽松开源可商用',
    desc: 'MIT License，可自由使用、修改与商用，适合作为企业数字化应用的二次开发底座。',
  },
  {
    mark: 'MOD',
    title: '模块化后端架构',
    desc: 'yqap-api / security / biz / core 分层清晰，Spring Modulith 支撑业务模块独立演进。',
  },
  {
    mark: 'DEMO',
    title: '纯前端演示模式',
    desc: 'npm run dev:demo 即可体验登录、权限、配置、监控与审计，无需启动后端。',
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
    desc: 'Vue 3 + Spring Boot 前后端一体交付，约定统一、工程边界清晰，降低应用搭建成本。',
    icon: Cpu,
  },
  {
    title: '业务组件开箱',
    desc: '用户、角色、菜单、单位、系统配置、公告与操作日志等能力即用，支撑组织与运维闭环。',
    icon: Grid,
  },
  {
    title: '权限安全体系',
    desc: 'RBAC + JWT，菜单驱动路由与按钮级鉴权；滑动验证码、RSA 传输与登录锁定开箱可用。',
    icon: Key,
  },
  {
    title: 'AI 能力扩展',
    desc: '平台定位面向智能化应用建设，预留 AI 能力接入与编排空间，便于叠加智能场景。',
    icon: Connection,
  },
  {
    title: '行业扩展能力',
    desc: '业务模块可插拔扩展，按行业沉淀组件与流程，快速形成可复用的应用系统。',
    icon: Setting,
  },
  {
    title: '运维观测闭环',
    desc: '工作台总览运行状态，系统监控覆盖 JVM / 数据库 / Web 服务，关键操作可审计追溯。',
    icon: Odometer,
  },
]

const techStack = [
  { name: 'Vue 3', role: '前端框架' },
  { name: 'TypeScript', role: '类型安全' },
  { name: 'Element Plus', role: 'UI 组件' },
  { name: 'Vite', role: '前端构建' },
  { name: 'Spring Boot 4', role: '后端框架' },
  { name: 'Spring Security', role: '认证鉴权' },
  { name: 'Spring Modulith', role: '模块化' },
  { name: 'MyBatis-Plus', role: '数据访问' },
  { name: 'MySQL', role: '数据存储' },
  { name: 'JWT / RSA', role: '安全传输' },
]

const workflowSteps = [
  {
    title: '在线体验',
    desc: '打开演示站或本地演示模式，先感受平台权限、配置与运维能力。',
  },
  {
    title: '克隆源码',
    desc: '从 GitHub / Gitee 拉取 monorepo，前端包名为 yunqi-application-platform。',
  },
  {
    title: '联调落地',
    desc: '初始化 MySQL 后，用根目录一键脚本或分别启动 yqap-core 与前端。',
  },
]

const quickStartCommands = [
  {
    title: '路径 A · 纯前端演示',
    hint: '无需后端 · 账号 demo / demo123',
    code: `git clone git@github.com:DataFutureX/yunqi-application-platform.git
cd yunqi-application-platform/frontend
npm install && npm run dev:demo`,
  },
  {
    title: '路径 B · 前后端联调',
    hint: '先导入 init.sql · 根目录一键启动 · 账号 admin / admin123',
    code: `cd yunqi-application-platform
# Windows: start.bat   |   PowerShell: .\\start.ps1   |   Linux/macOS: ./start.sh
# 前端 http://localhost:3000  ·  后端 http://localhost:8080`,
  },
]

let sectionObserver: IntersectionObserver | null = null

function handleScroll() {
  scrolled.value = window.scrollY > 24
}

function scrollTo(selector: string) {
  menuOpen.value = false
  const el = document.querySelector(selector)
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function goLogin() {
  router.push('/login')
}

function setupSectionObserver() {
  const ids = navItems.map((item) => item.id)
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

onMounted(() => {
  document.title = `${systemName.value} · 开源门户`
  systemConfigStore.fetchConfig({ publicOnly: true })
  window.addEventListener('scroll', handleScroll, { passive: true })
  setupSectionObserver()
  ensureBaiduAnalytics()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
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

.portal {
  position: relative;
  min-height: 100vh;
  color: $fg;
  background: $canvas;
  overflow-x: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans SC', Helvetica, Arial,
    sans-serif;
  font-size: 14px;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}

.portal__sky {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: min(100vh, 820px);
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.portal__sky-wash {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, $canvas-subtle 0%, $canvas 48%, $canvas 100%);
}

.portal__sky-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(48px);
  animation: portal-drift 20s ease-in-out infinite alternate;

  &--a {
    top: -12%;
    right: 4%;
    width: 48vw;
    height: 48vw;
    max-width: 560px;
    max-height: 560px;
    background: radial-gradient(circle, rgba(9, 105, 218, 0.1) 0%, transparent 68%);
  }

  &--b {
    top: 22%;
    left: -10%;
    width: 40vw;
    height: 40vw;
    max-width: 440px;
    max-height: 440px;
    background: radial-gradient(circle, rgba(31, 136, 61, 0.08) 0%, transparent 70%);
    animation-duration: 26s;
    animation-delay: -6s;
  }
}

@keyframes portal-drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(1.5%, 2%, 0) scale(1.04);
  }
}

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

// ── Header ──
.portal-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: transparent;
  transition:
    background 0.2s,
    border-color 0.2s,
    box-shadow 0.2s;

  &--scrolled,
  &--menu-open {
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(12px);
    border-bottom: 1px solid $border;
  }

  &__inner {
    display: flex;
    align-items: center;
    gap: 24px;
    max-width: $max;
    margin: 0 auto;
    padding: 12px 24px;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-left: auto;
    flex-shrink: 0;
  }

  &__studio {
    display: inline-flex;
    align-items: center;
    height: 32px;
    padding: 0 8px;
    margin-right: 2px;
    font-size: 13px;
    font-weight: 500;
    color: $fg-muted;
    text-decoration: none;
    white-space: nowrap;
    border-radius: $radius;
    transition:
      color 0.15s,
      background 0.15s;

    &:hover,
    &:focus-visible {
      color: $fg;
      background: $canvas-subtle;
      outline: none;
    }
  }

  &__repo {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    padding: 0;
    color: $fg-muted;
    text-decoration: none;
    border-radius: $radius;
    transition:
      color 0.15s,
      background 0.15s;

    &:hover,
    &:focus-visible {
      color: $fg;
      background: $canvas-subtle;
      outline: none;
    }
  }

  &__repo-icon {
    width: 18px;
    height: 18px;
    flex-shrink: 0;
  }

  &__cta {
    margin-left: 6px;
    height: 32px;
    padding: 0 12px;
    border: 1px solid rgba(31, 35, 40, 0.15) !important;
    border-radius: $radius !important;
    font-size: 14px;
    font-weight: 600;
    color: #fff !important;
    background: $success !important;

    &:hover,
    &:focus {
      background: $success-hover !important;
      border-color: rgba(31, 35, 40, 0.15) !important;
      color: #fff !important;
    }
  }

  &__menu {
    display: none;
    flex-direction: column;
    justify-content: center;
    gap: 5px;
    width: 32px;
    height: 32px;
    margin-left: 2px;
    padding: 7px;
    border: 1px solid $border;
    border-radius: $radius;
    background: $canvas;
    cursor: pointer;

    span {
      display: block;
      height: 2px;
      width: 100%;
      background: $fg;
      border-radius: 1px;
      transition:
        transform 0.15s,
        opacity 0.15s;
    }
  }

  &--menu-open &__menu span:nth-child(1) {
    transform: translateY(7px) rotate(45deg);
  }

  &--menu-open &__menu span:nth-child(2) {
    opacity: 0;
  }

  &--menu-open &__menu span:nth-child(3) {
    transform: translateY(-7px) rotate(-45deg);
  }
}

.portal-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: $fg;

  &__icon {
    width: 32px;
    height: 32px;
    border-radius: $radius;
    object-fit: contain;
  }

  &__name {
    font-size: 14px;
    font-weight: 600;
  }
}

.portal-nav {
  display: flex;
  gap: 2px;

  &__link {
    padding: 6px 10px;
    font-size: 14px;
    font-weight: 500;
    color: $fg;
    text-decoration: none;
    border-radius: $radius;
    transition:
      color 0.15s,
      background 0.15s;

    &:hover {
      color: $fg;
      background: $canvas-subtle;
    }

    &--active {
      color: $accent;
    }
  }
}

.portal-nav-mobile {
  display: none;
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

// ── Path ──
.portal-path {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-bottom: 32px;
}

.portal-path__step {
  position: relative;
  padding: 20px;
  background: $canvas;
  border: 1px solid $border;
  border-radius: $radius;
}

.portal-path__num {
  display: block;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: $accent;
}

.portal-path__title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: $fg;
}

.portal-path__desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: $fg-muted;
}

// ── Quick start commands ──
.portal-commands {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.portal-command {
  min-width: 0;
  padding: 16px;
  background: $canvas;
  border: 1px solid $border;
  border-radius: $radius;

  &__head {
    margin-bottom: 12px;
  }

  &__title {
    margin: 0 0 4px;
    font-size: 14px;
    font-weight: 600;
    color: $fg;
  }

  &__hint {
    margin: 0;
    font-size: 12px;
    color: $fg-muted;
  }

  &__code {
    margin: 0;
    padding: 14px 16px;
    overflow-x: auto;
    background: $canvas-dark-subtle;
    border: 1px solid #30363d;
    border-radius: $radius;
    color: #e6edf3;
    font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre;
  }
}

.portal-readme-more {
  margin-top: 8px;
  border: 1px solid $border;
  border-radius: $radius;
  background: $canvas;
  overflow: hidden;

  summary {
    cursor: pointer;
    list-style: none;
    padding: 12px 16px;
    font-size: 14px;
    font-weight: 600;
    color: $fg;
    user-select: none;

    &::-webkit-details-marker {
      display: none;
    }

    &::before {
      content: '▸';
      display: inline-block;
      margin-right: 8px;
      color: $fg-muted;
      transition: transform 0.15s;
    }
  }

  &[open] summary {
    border-bottom: 1px solid $border;
    background: $canvas-subtle;

    &::before {
      transform: rotate(90deg);
    }
  }

  .portal-readme {
    border: none;
    border-radius: 0;
  }
}

// ── README ──
.portal-readme {
  max-width: 100%;
  width: 100%;
  margin: 0;
  padding: 24px 28px;
  background: $canvas;
  border: 1px solid $border;
  border-radius: $radius;
  color: $fg;
  font-size: 14px;
  line-height: 1.6;
  overflow-x: auto;

  :deep(h1) {
    margin: 0 0 12px;
    font-size: 24px;
    font-weight: 700;
    line-height: 1.25;
  }

  :deep(h2) {
    margin: 28px 0 12px;
    padding-bottom: 8px;
    font-size: 18px;
    font-weight: 600;
    border-bottom: 1px solid $border;
  }

  :deep(h3) {
    margin: 20px 0 8px;
    font-size: 16px;
    font-weight: 600;
  }

  :deep(h4) {
    margin: 16px 0 6px;
    font-size: 14px;
    font-weight: 600;
  }

  :deep(p) {
    margin: 0 0 12px;
    color: $fg;
  }

  :deep(a) {
    color: $accent;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  :deep(ul),
  :deep(ol) {
    margin: 0 0 12px;
    padding-left: 1.5em;
    color: $fg;
  }

  :deep(li) {
    margin: 4px 0;
  }

  :deep(blockquote) {
    margin: 0 0 12px;
    padding: 0 12px;
    color: $fg-muted;
    border-left: 3px solid $border;
  }

  :deep(hr) {
    margin: 20px 0;
    border: none;
    border-top: 1px solid $border;
  }

  :deep(img) {
    max-width: 100%;
    height: auto;
    vertical-align: middle;
    border: 1px solid $border;
    border-radius: $radius;
  }

  :deep(code) {
    padding: 2px 6px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 0.9em;
    color: $fg;
    background: $canvas-subtle;
    border-radius: 4px;
  }

  :deep(pre) {
    margin: 0 0 12px;
    padding: 14px 16px;
    overflow-x: auto;
    background: $canvas-dark-subtle;
    border: 1px solid #30363d;
    border-radius: $radius;

    code {
      padding: 0;
      color: #e6edf3;
      background: transparent;
      font-size: 12px;
      line-height: 1.6;
    }
  }

  :deep(table) {
    width: 100%;
    margin: 0 0 16px;
    border-collapse: collapse;
    font-size: 13px;
  }

  :deep(th),
  :deep(td) {
    padding: 8px 12px;
    text-align: left;
    border: 1px solid $border;
  }

  :deep(th) {
    font-weight: 600;
    background: $canvas-subtle;
  }

  :deep(td) {
    color: $fg;
  }

  :deep(details) {
    margin: 0 0 10px;
    padding: 10px 12px;
    background: $canvas-subtle;
    border: 1px solid $border;
    border-radius: $radius;
  }

  :deep(summary) {
    cursor: pointer;
    font-weight: 600;
  }
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

// ── Footer ──
.portal-footer {
  position: relative;
  z-index: 1;
  padding: 32px 24px;
  background: $canvas-dark;
  border-top: 1px solid #30363d;
  color: #e6edf3;
}

.portal-footer__inner {
  display: grid;
  grid-template-columns: 1.2fr 1fr auto;
  align-items: center;
  gap: 20px;
  max-width: $max;
  margin: 0 auto;
}

.portal-footer__brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.portal-footer__icon {
  width: 28px;
  height: 28px;
  border-radius: $radius;
}

.portal-footer__name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #f0f6fc;
}

.portal-footer__license {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #8b949e;
}

.portal-footer__links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;

  a {
    font-size: 13px;
    color: #8b949e;
    text-decoration: none;

    &:hover {
      color: #58a6ff;
      text-decoration: underline;
    }
  }
}

.portal-footer__copy {
  margin: 0;
  font-size: 12px;
  color: #7d8590;
  text-align: right;
}

// ── Responsive ──
@media (max-width: 1024px) {
  .portal-nav {
    display: none;
  }

  .portal-header__menu {
    display: inline-flex;
  }

  .portal-nav-mobile {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 8px 24px 14px;
    background: $canvas;
    border-bottom: 1px solid $border;

    &[hidden] {
      display: none;
    }

    &__link {
      padding: 10px 12px;
      font-size: 14px;
      font-weight: 500;
      color: $fg;
      text-decoration: none;
      border-radius: $radius;

      &:hover,
      &--active {
        color: $accent;
        background: $canvas-subtle;
      }

      &--external {
        margin-top: 4px;
        color: $fg-muted;
        border-top: 1px solid $border;
        border-radius: 0 0 $radius $radius;

        &::after {
          content: ' ↗';
          font-size: 12px;
        }
      }
    }
  }

  .portal-hero {
    min-height: auto;
    padding-top: 112px;
    padding-bottom: 40px;
    align-items: center;
  }

  .portal-open-grid,
  .portal-stack,
  .portal-commands {
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

  .portal-path {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .portal-footer__inner {
    grid-template-columns: 1fr;
  }

  .portal-footer__copy {
    text-align: left;
  }
}

@media (max-width: 768px) {
  .portal-header__studio,
  .portal-header__repo {
    display: none;
  }

  .portal-open-grid,
  .portal-repo-row,
  .portal-stack,
  .portal-commands {
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

  .portal-readme {
    padding: 18px 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .portal__sky-glow,
  .portal-hero__content,
  :deep(.sky-cloud) {
    animation: none !important;
  }
}
</style>
