<template>
  <PortalPublicLayout variant="page" show-sky>
    <main class="docs-page">
      <div class="docs-page__inner">
        <header class="docs-page__head">
          <p class="docs-page__eyebrow">Docs</p>
          <h1 class="docs-page__title">文档</h1>
          <p class="docs-page__desc">
            从本地演示与联调上手，到伙伴 Ticket 换票（RS256 / 国密
            SM2）。快速开始与单点登录说明均在此查阅。
          </p>
        </header>
        <PortalDocs />
      </div>
    </main>
  </PortalPublicLayout>
</template>

<script setup lang="ts">
import { defineAsyncComponent, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'
import { DEFAULT_SYSTEM_NAME } from '@/stores/useSystemConfigStore'
import PortalPublicLayout from './PortalPublicLayout.vue'
import { isPortalDocId } from '@/utils/portalDocRoutes'

const PortalDocs = defineAsyncComponent(() => import('./PortalDocs.vue'))

const route = useRoute()
const router = useRouter()
const systemConfigStore = useSystemConfigStore()

watch(
  () => route.params.docId,
  (docId) => {
    if (typeof docId !== 'string' || !isPortalDocId(docId)) {
      void router.replace('/docs/quickstart')
    }
  },
  { immediate: true },
)

onMounted(() => {
  const name = systemConfigStore.systemName || DEFAULT_SYSTEM_NAME
  document.title = `${name} · 文档`
})
</script>

<style scoped lang="scss">
$fg: #1f2328;
$fg-muted: #656d76;
$accent: #0969da;
$max: 1120px;

.docs-page {
  position: relative;
  z-index: 1;
  padding: 96px 24px 64px;
}

.docs-page__inner {
  max-width: $max;
  margin: 0 auto;
}

.docs-page__head {
  max-width: 680px;
  margin: 0 0 32px;
}

.docs-page__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: $accent;
}

.docs-page__title {
  margin: 0 0 12px;
  font-size: clamp(28px, 3.4vw, 36px);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.25;
  color: $fg;
}

.docs-page__desc {
  margin: 0;
  font-size: 16px;
  line-height: 1.6;
  color: $fg-muted;
}
</style>
