<template>
  <div class="error-page">
    <div class="error-page__code">404</div>
    <h1 class="error-page__title">页面不存在</h1>
    <p class="error-page__desc">请检查地址是否正确，或从菜单重新进入目标页面。</p>
    <div class="error-page__actions">
      <el-button type="primary" @click="goHome">返回首页</el-button>
      <el-button @click="goBack">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HOME_DASHBOARD_PATH } from '@/constants/app'

const router = useRouter()

const goHome = () => {
  void router.replace(HOME_DASHBOARD_PATH)
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
  color: var(--el-color-info);
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
