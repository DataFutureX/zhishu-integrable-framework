<template>
  <ElementPlusRoot>
  <div class="error-page">
    <div class="error-page__code">403</div>
    <h1 class="error-page__title">无权访问</h1>
    <p class="error-page__desc">当前账号没有访问该页面的权限，请联系管理员开通后重试。</p>
    <div class="error-page__actions">
      <el-button type="primary" @click="goHome">返回首页</el-button>
      <el-button @click="goBack">返回上一页</el-button>
    </div>
  </div>
  </ElementPlusRoot>
</template>

<script setup lang="ts">
import ElementPlusRoot from '@/components/app/ElementPlusRoot.vue'
import { useMenuStore } from '@/stores/useMenuStore'
import { useRouter } from 'vue-router'

const router = useRouter()
const menuStore = useMenuStore()

const goHome = () => {
  void router.replace(menuStore.defaultPath)
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  goHome()
}
</script>

<style scoped lang="scss">
.error-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 48px 24px;
  text-align: center;
}

.error-page__code {
  font-size: 72px;
  font-weight: 700;
  line-height: 1;
  color: var(--el-color-warning);
  letter-spacing: 0.04em;
}

.error-page__title {
  margin: 16px 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.error-page__desc {
  margin: 0;
  max-width: 420px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.error-page__actions {
  display: flex;
  gap: 12px;
  margin-top: 28px;
}
</style>
