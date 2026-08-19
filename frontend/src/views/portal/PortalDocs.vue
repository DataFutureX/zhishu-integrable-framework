<template>
  <div class="portal-docs">
    <nav class="portal-docs__nav" aria-label="文档目录">
      <div v-for="group in docGroups" :key="group.name" class="portal-docs__group">
        <p class="portal-docs__group-name">{{ group.name }}</p>
        <a
          v-for="doc in group.items"
          :key="doc.id"
          :href="portalDocPath(doc.id)"
          class="portal-docs__link"
          :class="{ 'portal-docs__link--active': activeDoc === doc.id }"
          @click.prevent="selectDoc(doc.id)"
        >
          <span class="portal-docs__link-title">{{ doc.label }}</span>
          <span class="portal-docs__link-desc">{{ doc.navHint }}</span>
        </a>
      </div>

      <div v-if="currentToc.length" class="portal-docs__toc">
        <p class="portal-docs__group-name">本篇目录</p>
        <a
          v-for="item in currentToc"
          :key="item.id"
          :href="`#${item.id}`"
          class="portal-docs__toc-link"
          :class="[
            `portal-docs__toc-link--h${item.level}`,
            { 'portal-docs__toc-link--active': activeHeadingId === item.id },
          ]"
          @click.prevent="scrollToHeading(item.id)"
        >
          {{ item.text }}
        </a>
      </div>
    </nav>

    <div class="portal-docs__main">
      <header class="portal-docs__toolbar">
        <div>
          <h3 class="portal-docs__heading">{{ currentDoc.title }}</h3>
          <p class="portal-docs__summary">{{ currentDoc.summary }}</p>
        </div>
        <a
          class="portal-docs__repo"
          :href="currentDoc.repoUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          在仓库中查看 →
        </a>
      </header>

      <article
        ref="articleRef"
        class="portal-readme"
        v-if="docLoading"
        aria-busy="true"
      >
        <p class="portal-docs__loading">文档加载中…</p>
      </article>
      <article
        v-else
        ref="articleRef"
        class="portal-readme"
        v-html="currentHtml"
        @click="onArticleClick"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  parsePortalDocRef,
  portalDocPath,
  type PortalDocId,
} from '@/utils/portalDocRoutes'
import type { PortalDocTocItem } from './portalMarkdown'

interface PortalDocMeta {
  id: PortalDocId
  label: string
  navHint: string
  title: string
  summary: string
  repoUrl: string
}

const GITHUB_BLOB = 'https://github.com/DataFutureX/zhishu-integrable-framework/blob/master'

const docs: PortalDocMeta[] = [
  {
    id: 'quickstart',
    label: '快速开始',
    navHint: '演示 / 联调',
    title: '快速开始',
    summary: '先体验产品能力，再克隆源码。演示模式无需后端；联调用根目录一键启动脚本。',
    repoUrl: `${GITHUB_BLOB}/README.md`,
  },
  {
    id: 'sso',
    label: '单点登录对接',
    navHint: '协议与换票',
    title: '单点登录对接说明',
    summary: '伙伴签发短期 Ticket，云起验签换票并签发业务 JWT。支持 RS256 与国密 SM2。',
    repoUrl: `${GITHUB_BLOB}/docs/单点登录对接说明.md`,
  },
  {
    id: 'wanxiang',
    label: '万象接入联调',
    navHint: '逐步清单',
    title: '万象接入联调实现步骤',
    summary: '万象（iss=wanxiang）→ 云起单向 SSO：环境地址、公钥登记、签发跳转与验收用例。',
    repoUrl: `${GITHUB_BLOB}/docs/万象接入联调实现步骤.md`,
  },
  {
    id: 'sso-sdk',
    label: '他方 SSO SDK',
    navHint: 'Java 签发',
    title: '他方 SSO 接入 SDK',
    summary: '伙伴侧 Java SDK：生成 RSA / SM2 密钥、签发 Ticket、拼装 /sso/callback 回调地址。',
    repoUrl: `${GITHUB_BLOB}/docs/他方SSO接入SDK使用说明.md`,
  },
]

const docGroups = [
  { name: '上手', items: docs.filter((doc) => doc.id === 'quickstart') },
  { name: '单点登录', items: docs.filter((doc) => doc.id !== 'quickstart') },
]

interface DocCacheEntry {
  html: string
  toc: PortalDocTocItem[]
}

const htmlCache = new Map<PortalDocId, DocCacheEntry>()
const currentHtml = ref('')
const currentToc = ref<PortalDocTocItem[]>([])
const activeHeadingId = ref('')
const articleRef = ref<HTMLElement | null>(null)
const docLoading = ref(false)
let loadSeq = 0
let headingObserver: IntersectionObserver | null = null

const route = useRoute()
const router = useRouter()

const activeDoc = computed<PortalDocId>(() => parsePortalDocRef(route.path) ?? 'quickstart')

const currentDoc = computed(() => docs.find((doc) => doc.id === activeDoc.value) ?? docs[0])

async function ensureDocHtml(id: PortalDocId) {
  const cached = htmlCache.get(id)
  if (cached) {
    currentHtml.value = cached.html
    currentToc.value = cached.toc
    docLoading.value = false
    await nextTick()
    setupHeadingObserver()
    return
  }

  const seq = ++loadSeq
  docLoading.value = true
  activeHeadingId.value = ''
  try {
    const { loadPortalDocContent } = await import('./portalMarkdown')
    const content = await loadPortalDocContent(id)
    if (seq !== loadSeq) return
    htmlCache.set(id, content)
    currentHtml.value = content.html
    currentToc.value = content.toc
  } finally {
    if (seq === loadSeq) {
      docLoading.value = false
      await nextTick()
      setupHeadingObserver()
    }
  }
}

function scrollToHeading(id: string) {
  const target = document.getElementById(id)
  if (!target) return
  activeHeadingId.value = id
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  history.replaceState(null, '', `#${encodeURIComponent(id)}`)
}

function setupHeadingObserver() {
  headingObserver?.disconnect()
  headingObserver = null

  const article = articleRef.value
  if (!article || currentToc.value.length === 0) return

  const headingIds = new Set(currentToc.value.map((item) => item.id))
  const visible = new Map<string, IntersectionObserverEntry>()

  headingObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const id = entry.target.id
        if (!headingIds.has(id)) continue
        if (entry.isIntersecting) {
          visible.set(id, entry)
        } else {
          visible.delete(id)
        }
      }

      if (visible.size === 0) return

      const topmost = [...visible.values()].sort(
        (a, b) => a.boundingClientRect.top - b.boundingClientRect.top,
      )[0]
      if (topmost) {
        activeHeadingId.value = topmost.target.id
      }
    },
    {
      root: null,
      rootMargin: '-88px 0px -65% 0px',
      threshold: [0, 1],
    },
  )

  for (const item of currentToc.value) {
    const el = article.querySelector<HTMLElement>(`#${CSS.escape(item.id)}`)
    if (el) headingObserver.observe(el)
  }

  const hash = decodeURIComponent(location.hash.replace(/^#/, ''))
  if (hash && headingIds.has(hash)) {
    activeHeadingId.value = hash
  } else if (currentToc.value[0]) {
    activeHeadingId.value = currentToc.value[0].id
  }
}

function selectDoc(id: PortalDocId) {
  const path = portalDocPath(id)
  if (route.path !== path) {
    void router.push(path)
  }
  activeHeadingId.value = ''
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function onArticleClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Element)) return
  const anchor = target.closest('a')
  if (!anchor) return
  const href = anchor.getAttribute('href') ?? ''
  const docId = parsePortalDocRef(href)
  const isDocsNav =
    href.startsWith('/docs') ||
    href.startsWith('#docs') ||
    href.includes('/docs/') ||
    /\.md($|[?#])/i.test(href)
  if (docId && isDocsNav) {
    event.preventDefault()
    selectDoc(docId)
    return
  }
  if (href.startsWith('#') && !href.startsWith('#docs')) {
    event.preventDefault()
    const headingId = decodeURIComponent(href.slice(1))
    scrollToHeading(headingId)
  }
}

onUnmounted(() => {
  headingObserver?.disconnect()
})

watch(
  activeDoc,
  (id) => {
    void ensureDocHtml(id)
  },
  { immediate: true },
)

watch(
  () => route.params.docId,
  () => {
    window.scrollTo({ top: 0 })
  },
)
</script>

<style scoped lang="scss">
$fg: #1f2328;
$fg-muted: #656d76;
$canvas: #ffffff;
$canvas-subtle: #f6f8fa;
$border: #d0d7de;
$accent: #0969da;
$radius: 6px;
$canvas-dark-subtle: #161b22;

.portal-docs {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}

.portal-docs__nav {
  position: sticky;
  top: 72px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-height: calc(100vh - 96px);
  padding: 12px;
  overflow-y: auto;
  background: $canvas;
  border: 1px solid $border;
  border-radius: $radius;
}

.portal-docs__group-name {
  margin: 0 0 6px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $fg-muted;
}

.portal-docs__link {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  text-decoration: none;
  color: $fg;
  border-radius: $radius;
  transition: background 0.15s;

  &:hover {
    background: $canvas-subtle;
  }

  &--active {
    background: #ddf4ff;
    color: $accent;
  }
}

.portal-docs__link-title {
  font-size: 13px;
  font-weight: 600;
}

.portal-docs__link-desc {
  font-size: 12px;
  color: $fg-muted;
}

.portal-docs__link--active .portal-docs__link-desc {
  color: $accent;
}

.portal-docs__toc {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: 4px;
  border-top: 1px solid $border;
}

.portal-docs__toc-link {
  display: block;
  padding: 5px 8px;
  font-size: 12px;
  line-height: 1.45;
  color: $fg-muted;
  text-decoration: none;
  border-radius: $radius;
  border-left: 2px solid transparent;
  transition:
    color 0.15s,
    background 0.15s,
    border-color 0.15s;

  &:hover {
    color: $fg;
    background: $canvas-subtle;
  }

  &--h3 {
    padding-left: 16px;
  }

  &--h4 {
    padding-left: 24px;
    font-size: 11px;
  }

  &--active {
    color: $accent;
    font-weight: 600;
    background: #ddf4ff;
    border-left-color: $accent;
  }
}

.portal-docs__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.portal-docs__heading {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 650;
  color: $fg;
}

.portal-docs__summary {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: $fg-muted;
}

.portal-docs__repo {
  flex-shrink: 0;
  padding-top: 4px;
  font-size: 13px;
  font-weight: 600;
  color: $accent;
  text-decoration: none;
  white-space: nowrap;

  &:hover {
    text-decoration: underline;
  }
}

.portal-docs__loading {
  margin: 0;
  padding: 48px 0;
  text-align: center;
  color: $fg-muted;
}

@media (max-width: 1024px) {
  .portal-docs {
    grid-template-columns: 1fr;
  }

  .portal-docs__nav {
    position: static;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px 12px;
  }

  .portal-docs__group {
    display: contents;
  }

  .portal-docs__group-name {
    grid-column: 1 / -1;
    margin-top: 4px;
  }
}

@media (max-width: 768px) {
  .portal-docs__nav {
    grid-template-columns: 1fr;
  }

  .portal-docs__toolbar {
    flex-direction: column;
  }
}

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

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    scroll-margin-top: 88px;
  }

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

@media (max-width: 768px) {
  .portal-readme {
    padding: 18px 16px;
  }
}
</style>
