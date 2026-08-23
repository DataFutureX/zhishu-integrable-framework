<template>
  <ElementPlusRoot>
  <div class="sso-callback">
    <div class="sso-callback__card">
      <template v-if="phase === 'loading'">
        <el-icon class="sso-callback__spinner is-loading" :size="36">
          <Loading />
        </el-icon>
        <h1 class="sso-callback__title">正在登录</h1>
        <p class="sso-callback__desc">正在校验单点登录票据，请稍候…</p>
      </template>

      <template v-else-if="phase === 'error'">
        <div class="sso-callback__code">SSO</div>
        <h1 class="sso-callback__title">单点登录失败</h1>
        <p class="sso-callback__desc">{{ errorMessage }}</p>
        <div class="sso-callback__actions">
          <el-button type="primary" @click="goLogin">返回登录页</el-button>
        </div>
      </template>
    </div>
  </div>
  </ElementPlusRoot>
</template>

<script setup lang="ts">
import ElementPlusRoot from '@/components/app/ElementPlusRoot.vue'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { exchangeSsoTicketApi } from '@/api/auth'
import { useLayoutStore } from '@/stores/useLayoutStore'
import { useMenuStore } from '@/stores/useMenuStore'
import { useUserStore } from '@/stores/useUserStore'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const menuStore = useMenuStore()
const layoutStore = useLayoutStore()

const phase = ref<'loading' | 'error'>('loading')
const errorMessage = ref('单点登录失败，请返回登录页重试')

/** 校验外部传入的 redirect URL 是否安全，不安全时返回 null */
function resolveSafeRedirect(raw: unknown): string | null {
  if (typeof raw !== 'string' || !raw.trim()) return null
  const value = raw.trim()
  if (
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('://') ||
    value.includes('\\')
  ) {
    return null
  }
  return value
}

function clearTicketFromUrl() {
  const query = { ...route.query }
  delete query.ticket
  void router.replace({ path: route.path, query })
}

function goLogin() {
  void router.replace({ name: 'Login' })
}

onMounted(async () => {
  const ticket = typeof route.query.ticket === 'string' ? route.query.ticket.trim() : ''
  const queryRedirect = resolveSafeRedirect(route.query.redirect)

  // 尽快去掉地址栏中的 ticket，避免残留
  clearTicketFromUrl()

  if (!ticket) {
    phase.value = 'error'
    errorMessage.value = '缺少单点登录票据，请从伙伴系统重新进入'
    return
  }

  try {
    const data = await exchangeSsoTicketApi({ ticket, redirect: queryRedirect })
    if (!data?.token) {
      throw new Error('换票响应异常')
    }

    userStore.setToken(data.token, data.expiration)
    await userStore.fetchUserInfo()
    layoutStore.applyLoginDefaultLayout()
    menuStore.reset(router)
    await menuStore.fetchAndRegisterRoutes(router)

    // 菜单已加载，优先后端指定 → 外部 query → 首个可访问菜单
    const target = resolveSafeRedirect(data.redirect) || queryRedirect || menuStore.defaultPath
    await router.replace(target)
  } catch (error: unknown) {
    console.error('SSO 换票失败:', error)
    phase.value = 'error'
    if (error && typeof error === 'object' && 'message' in error && typeof (error as { message: unknown }).message === 'string') {
      errorMessage.value = (error as { message: string }).message || errorMessage.value
    } else {
      errorMessage.value = '单点登录失败，请返回登录页重试'
    }
  }
})
</script>

<style scoped lang="scss">
.sso-callback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: linear-gradient(160deg, #f5f8ff 0%, #eef2f8 45%, #f8fafc 100%);
}

.sso-callback__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 420px;
  padding: 40px 32px;
  text-align: center;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
}

.sso-callback__spinner {
  color: var(--el-color-primary);
  margin-bottom: 16px;
}

.sso-callback__code {
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
  color: var(--el-color-danger);
  letter-spacing: 0.06em;
}

.sso-callback__title {
  margin: 12px 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.sso-callback__desc {
  margin: 0;
  max-width: 360px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.sso-callback__actions {
  margin-top: 24px;
}
</style>
