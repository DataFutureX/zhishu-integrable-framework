<template>
  <div class="portal" :class="{ 'portal--page': variant === 'page' }">
    <div v-if="showSky" class="portal__sky" aria-hidden="true">
      <div class="portal__sky-wash" />
      <AgentNetworkBackdrop variant="wide" />
      <slot name="sky" />
    </div>

    <header
      class="portal-header"
      :class="{ 'portal-header--scrolled': headerSolid, 'portal-header--menu-open': menuOpen }"
    >
      <div class="portal-header__inner">
        <a class="portal-brand" href="/portal" @click.prevent="goHome">
          <img :src="systemIconUrl" alt="" class="portal-brand__icon" />
          <span class="portal-brand__text">
            <span class="portal-brand__name">{{ systemName }}</span>
            <span class="portal-brand__en">{{ englishTitle }}</span>
          </span>
        </a>
        <nav class="portal-nav" aria-label="页面导航">
          <a
            v-for="item in PORTAL_SECTION_NAV"
            :key="item.id"
            :href="`/portal#${item.id}`"
            class="portal-nav__link"
            :class="{ 'portal-nav__link--active': !docsActive && activeSection === item.id }"
            @click.prevent="goSection(item.id)"
          >
            {{ item.label }}
          </a>
          <a
            href="/docs"
            class="portal-nav__link"
            :class="{ 'portal-nav__link--active': docsActive }"
            @click.prevent="goDocs()"
          >
            文档
          </a>
        </nav>
        <div class="portal-header__actions">
          <a
            class="portal-header__studio"
            :href="STUDIO_PORTAL.url"
            target="_blank"
            rel="noopener noreferrer"
            :title="STUDIO_PORTAL.label"
          >
            {{ STUDIO_PORTAL.label }}
          </a>
          <a
            v-for="repo in SOURCE_REPOS"
            :key="repo.key"
            class="portal-header__repo"
            :href="repo.url"
            target="_blank"
            rel="noopener noreferrer"
            :aria-label="repo.label"
            :title="repo.label"
          >
            <svg class="portal-header__repo-icon" :viewBox="repo.iconViewBox" aria-hidden="true">
              <path fill="currentColor" :d="repo.iconPath" />
            </svg>
          </a>
          <button type="button" class="portal-header__cta" @click="goLogin">
            在线体验
          </button>
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
          v-for="item in PORTAL_SECTION_NAV"
          :key="`m-${item.id}`"
          :href="`/portal#${item.id}`"
          class="portal-nav-mobile__link"
          :class="{ 'portal-nav-mobile__link--active': !docsActive && activeSection === item.id }"
          @click.prevent="goSection(item.id)"
        >
          {{ item.label }}
        </a>
        <a
          href="/docs"
          class="portal-nav-mobile__link"
          :class="{ 'portal-nav-mobile__link--active': docsActive }"
          @click.prevent="goDocs()"
        >
          文档
        </a>
        <a
          class="portal-nav-mobile__link portal-nav-mobile__link--external"
          :href="STUDIO_PORTAL.url"
          target="_blank"
          rel="noopener noreferrer"
          @click="menuOpen = false"
        >
          {{ STUDIO_PORTAL.label }}
        </a>
      </nav>
    </header>

    <slot />

    <footer class="portal-footer">
      <div class="portal-footer__inner">
        <div class="portal-footer__brand">
          <img :src="systemIconUrl" alt="" class="portal-footer__icon" />
          <div>
            <span class="portal-footer__name">{{ systemName }}</span>
            <span class="portal-footer__license">ZhiShu Integrable Framework · MIT</span>
          </div>
        </div>
        <div class="portal-footer__links">
          <a :href="STUDIO_PORTAL.url" target="_blank" rel="noopener noreferrer">
            {{ STUDIO_PORTAL.label }}
          </a>
          <a
            v-for="repo in SOURCE_REPOS"
            :key="`ft-${repo.key}`"
            :href="repo.url"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ repo.label }}
          </a>
          <a href="/docs" @click.prevent="goDocs()">文档</a>
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
import { useRoute, useRouter } from 'vue-router'
import AgentNetworkBackdrop from '@/components/login/AgentNetworkBackdrop.vue'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'
import { DEFAULT_COPYRIGHT, DEFAULT_SYSTEM_NAME } from '@/stores/useSystemConfigStore'
import { PORTAL_SECTION_NAV, SOURCE_REPOS, STUDIO_PORTAL } from './portalMeta'
import { portalDocPath, type PortalDocId } from '@/utils/portalDocRoutes'

const props = withDefaults(
  defineProps<{
    variant?: 'landing' | 'page'
    activeSection?: string
    showSky?: boolean
  }>(),
  {
    variant: 'landing',
    activeSection: '',
    showSky: true,
  },
)

const router = useRouter()
const route = useRoute()
const systemConfigStore = useSystemConfigStore()

const scrolled = ref(false)
const menuOpen = ref(false)

const docsActive = computed(() => route.path.startsWith('/docs'))
const headerSolid = computed(() => props.variant === 'page' || scrolled.value || menuOpen.value)
const systemName = computed(() => systemConfigStore.systemName || DEFAULT_SYSTEM_NAME)
const systemIconUrl = computed(() => systemConfigStore.iconUrl)
const englishTitle = computed(() => systemConfigStore.displayEnglishTitle)
const copyright = computed(() => systemConfigStore.copyright || DEFAULT_COPYRIGHT)

function goHome() {
  menuOpen.value = false
  void router.push('/portal')
}

function goSection(id: string) {
  menuOpen.value = false
  if (route.path === '/portal') {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  void router.push({ path: '/portal', hash: `#${id}` })
}

function goDocs(id: PortalDocId = 'quickstart') {
  menuOpen.value = false
  const path = portalDocPath(id)
  if (route.path === path) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  void router.push(path)
}

function goLogin() {
  menuOpen.value = false
  void router.push('/login')
}

function handleScroll() {
  scrolled.value = window.scrollY > 24
}

onMounted(() => {
  systemConfigStore.fetchConfig({ publicOnly: true })
  window.addEventListener('scroll', handleScroll, { passive: true })
  handleScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped lang="scss">
$fg: #1f2328;
$fg-muted: #656d76;
$canvas: #ffffff;
$canvas-subtle: #f6f8fa;
$border: #d0d7de;
$accent: #0969da;
$success: #1f883d;
$success-hover: #1a7f37;
$canvas-dark: #0d1117;
$radius: 6px;
$max: 1120px;

.portal {
  position: relative;
  min-height: 100vh;
  color: $fg;
  background: $canvas;
  overflow-x: hidden;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans SC', Helvetica, Arial, sans-serif;
  font-size: 14px;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;

  &--page {
    background: $canvas-subtle;
  }
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
    border: 1px solid rgba(31, 35, 40, 0.15);
    border-radius: $radius;
    font-size: 14px;
    font-weight: 600;
    font-family: inherit;
    color: #fff;
    background: $success;
    cursor: pointer;
    appearance: none;

    &:hover,
    &:focus {
      background: $success-hover;
      border-color: rgba(31, 35, 40, 0.15);
      color: #fff;
      outline: none;
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
  gap: 10px;
  flex-shrink: 0;
  min-width: 0;
  text-decoration: none;
  color: $fg;

  &__icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    object-fit: contain;
    flex-shrink: 0;
  }

  &__text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  &__name {
    font-size: 14px;
    font-weight: 650;
    line-height: 1.2;
    white-space: nowrap;
  }

  &__en {
    font-size: 11px;
    font-weight: 500;
    letter-spacing: 0.02em;
    line-height: 1.2;
    color: $fg-muted;
    white-space: nowrap;
  }
}

.portal-nav {
  display: flex;
  gap: 2px;
  min-width: 0;
  flex: 1;

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
}

@media (prefers-reduced-motion: reduce) {
  .portal__sky-glow,
  :deep(.sky-cloud) {
    animation: none !important;
  }
}
</style>
