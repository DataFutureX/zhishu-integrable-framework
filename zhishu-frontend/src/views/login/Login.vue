<template>
  <ElementPlusRoot>
  <div class="login-container">
    <div class="background" aria-hidden="true">
      <div class="bg-gradient" />
      <div class="bg-mesh" />
      <div class="orb orb-1" />
      <div class="orb orb-2" />
      <div class="orb orb-3" />
      <SkyCloud
        variant="near"
        top="12%"
        left="-8%"
        width="360px"
        duration="38s"
      />
      <SkyCloud
        variant="mid"
        flip
        top="28%"
        right="-6%"
        left="auto"
        width="400px"
        duration="46s"
        delay="-16s"
      />
      <SkyCloud
        variant="far"
        top="50%"
        left="16%"
        width="260px"
        duration="52s"
        delay="-28s"
      />
      <div
        v-for="particle in particles"
        :key="particle.id"
        class="particle"
        :style="particle.style"
      />
    </div>

    <div class="login-layout">
      <div class="brand-panel">
        <div class="brand-content">
          <div class="brand-badge">
            <img :src="systemIconUrl" alt="" class="brand-badge__icon" />
            <span>{{ systemName }}</span>
          </div>
          <p class="brand-eyebrow">{{ englishTitle }}</p>
          <h1 class="brand-title">{{ systemName }}</h1>
          <p v-if="systemIntroduction" class="brand-desc">{{ systemIntroduction }}</p>
          <p v-else class="brand-desc">
            一套面向企业数字化应用建设的模块化开发基础平台，通过统一技术架构、业务组件、AI 能力和行业扩展能力，帮助企业快速构建智能化应用系统。
          </p>
          <ul class="feature-list">
            <li v-for="feature in features" :key="feature.label" class="feature-item">
              <span class="feature-icon">
                <el-icon :size="18"><component :is="feature.icon" /></el-icon>
              </span>
              <div class="feature-text">
                <strong>{{ feature.label }}</strong>
                <span>{{ feature.desc }}</span>
              </div>
            </li>
          </ul>
          <a class="portal-entry" href="/portal" @click.prevent="goPortal">
            <el-icon :size="16"><ArrowRight /></el-icon>
            了解知枢可集成框架
          </a>
        </div>
      </div>

      <div class="form-panel">
        <div class="login-box">
          <div class="login-header">
            <p class="login-eyebrow">{{ englishTitle }}</p>
            <h2 class="title">欢迎登录</h2>
            <p class="subtitle">登录后进入工作台，构建与管理企业数字化应用</p>
          </div>

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            class="login-form"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username" class="form-field">
              <el-input
                v-model="loginForm.username"
                placeholder="用户名"
                size="large"
                clearable
              />
            </el-form-item>

            <el-form-item prop="password" class="form-field">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="密码"
                size="large"
                show-password
              />
            </el-form-item>

            <el-form-item class="submit-item">
              <el-button
                type="primary"
                size="large"
                class="login-btn"
                :loading="loading"
                @click="handleLogin"
              >
                <span v-if="!loading">登 录</span>
                <span v-else>登录中...</span>
              </el-button>
            </el-form-item>
          </el-form>

          <p class="portal-hint">
            首次访问？
            <a class="portal-hint__link" href="/portal" @click.prevent="goPortal">了解平台能力</a>
          </p>

          <el-alert
            v-if="isDemoMode"
            type="warning"
            :closable="false"
            show-icon
            class="demo-login-alert"
            title="演示模式"
            description="任意账号密码均可登录，推荐 demo / demo123。数据均为模拟，无需后端服务。"
          />

          <el-dialog
            v-model="captchaVisible"
            title="安全验证"
            width="360px"
            align-center
            destroy-on-close
            :close-on-click-modal="false"
            class="captcha-dialog"
            @closed="handleCaptchaClosed"
          >
            <SlideCaptcha @success="handleCaptchaSuccess" />
          </el-dialog>

          <div class="login-footer">
            <p v-if="copyright" class="copyright">{{ copyright }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
  </ElementPlusRoot>
</template>

<script setup lang="ts">
import ElementPlusRoot from '@/components/app/ElementPlusRoot.vue'
import { ArrowRight } from '@element-plus/icons-vue'
import SkyCloud from '@/components/common/SkyCloud.vue'
import SlideCaptcha from '@/components/auth/SlideCaptcha.vue'
import { useLogin } from '@/composables/useLogin'

const {
  isDemoMode,
  systemName,
  englishTitle,
  systemIconUrl,
  copyright,
  systemIntroduction,
  features,
  loginFormRef,
  loading,
  captchaVisible,
  loginForm,
  loginRules,
  particles,
  goPortal,
  handleLogin,
  handleCaptchaClosed,
  handleCaptchaSuccess,
} = useLogin()
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

$ink: #1f2328;
$ink-soft: #424a53;
$ink-mute: #656d76;
$accent: #0969da;
$accent-bright: #218bff;
$sky: #f6f8fa;
$foam: #ffffff;

.login-container {
  position: relative;
  width: 100%;
  min-height: 100vh;
  overflow: hidden;
  font-family: 'Noto Sans SC', 'Outfit', sans-serif;
}

.background {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;

  .bg-gradient {
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, #f6f8fa 0%, #ffffff 48%, $foam 100%);
  }

  .bg-mesh {
    position: absolute;
    inset: 0;
    opacity: 0.2;
    background-image:
      linear-gradient(rgba(9, 105, 218, 0.08) 1px, transparent 1px),
      linear-gradient(90deg, rgba(9, 105, 218, 0.08) 1px, transparent 1px);
    background-size: 48px 48px;
    mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.4) 0%, transparent 80%);
  }

  .orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(48px);
    animation: orb-float 14s ease-in-out infinite;

    &.orb-1 {
      top: -6%;
      right: 10%;
      width: 380px;
      height: 380px;
      background: rgba(9, 105, 218, 0.1);
    }

    &.orb-2 {
      bottom: 8%;
      left: -4%;
      width: 320px;
      height: 320px;
      background: rgba(31, 136, 61, 0.08);
      animation-delay: -5s;
    }

    &.orb-3 {
      top: 42%;
      left: 42%;
      width: 220px;
      height: 220px;
      background: rgba(9, 105, 218, 0.06);
      animation-delay: -9s;
    }
  }

  .particle {
    position: absolute;
    bottom: -10px;
    background: rgba(9, 105, 218, 0.28);
    border-radius: 50%;
    animation: float-up linear infinite;
  }
}

.login-layout {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: clamp(32px, 5vw, 72px);
  max-width: 1120px;
  min-height: 100vh;
  margin: 0 auto;
  padding: 48px clamp(24px, 4vw, 48px);
}

@media (min-width: 1025px) {
  .form-panel {
    justify-self: start;
    max-width: 420px;
  }

  .login-box {
    min-height: 460px;
  }
}

.brand-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fade-in-left 0.8s ease-out;

  .brand-content {
    width: 100%;
    max-width: 520px;
  }

  .brand-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 6px 14px;
    margin-bottom: 24px;
    font-size: 13px;
    font-weight: 600;
    color: $ink;
    background: rgba(255, 255, 255, 0.65);
    border: 1px solid rgba(9, 105, 218, 0.16);
    border-radius: 999px;
    backdrop-filter: blur(8px);

    &__icon {
      width: 22px;
      height: 22px;
      object-fit: contain;
      flex-shrink: 0;
      border-radius: 5px;
    }
  }

  .brand-eyebrow {
    margin: 0 0 10px;
    font-family: 'Outfit', sans-serif;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: $accent;
  }

  .brand-title {
    margin: 0 0 14px;
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: clamp(34px, 4vw, 44px);
    font-weight: 700;
    line-height: 1.15;
    letter-spacing: -0.02em;
    color: $ink;
  }

  .brand-desc {
    margin: 0 0 28px;
    font-size: 15px;
    line-height: 1.75;
    color: $ink-mute;
  }

  .feature-list {
    list-style: none;
    padding: 0;
    margin: 0;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .feature-item {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 14px 16px;
    background: rgba(255, 255, 255, 0.55);
    border: 1px solid rgba(9, 105, 218, 0.1);
    border-radius: 12px;
    backdrop-filter: blur(8px);
    transition:
      background 0.25s,
      border-color 0.25s,
      transform 0.25s;

    &:hover {
      background: rgba(255, 255, 255, 0.85);
      border-color: rgba(9, 105, 218, 0.22);
      transform: translateY(-2px);
    }
  }

  .feature-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    flex-shrink: 0;
    color: $accent;
    background: rgba(9, 105, 218, 0.1);
    border-radius: 10px;
  }

  .feature-text {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;

    strong {
      font-size: 14px;
      font-weight: 600;
      color: $ink;
    }

    span {
      font-size: 12px;
      line-height: 1.45;
      color: $ink-mute;
    }
  }

  .portal-entry {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-top: 28px;
    padding: 10px 18px;
    font-size: 14px;
    font-weight: 600;
    color: $accent;
    text-decoration: none;
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid rgba(9, 105, 218, 0.22);
    border-radius: 10px;
    transition:
      background 0.2s,
      border-color 0.2s,
      transform 0.2s;

    &:hover {
      background: #fff;
      border-color: rgba(9, 105, 218, 0.4);
      transform: translateX(4px);
    }
  }
}

.form-panel {
  width: 100%;
  animation: fade-in-right 0.8s ease-out;
}

.login-box {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 36px 36px 28px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(9, 105, 218, 0.12);
  border-radius: 16px;
  box-shadow: 0 16px 40px rgba(12, 27, 42, 0.08);
  backdrop-filter: blur(14px);
}

.login-header {
  margin-bottom: 28px;

  .login-eyebrow {
    display: none;
    margin: 0 0 12px;
    font-family: 'Outfit', sans-serif;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: $accent;
  }

  .title {
    margin: 0 0 10px;
    font-family: 'Outfit', 'Noto Sans SC', sans-serif;
    font-size: 28px;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: $ink;
  }

  .subtitle {
    margin: 0;
    font-size: 14px;
    line-height: 1.6;
    color: $ink-mute;
  }
}

.login-form {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;

  .form-field {
    margin-bottom: 16px;
  }

  :deep(.el-form-item) {
    margin-bottom: 0;
  }

  :deep(.el-input__wrapper) {
    padding: 4px 16px;
    background: $foam;
    border-radius: 10px;
    box-shadow: 0 0 0 1px rgba(12, 27, 42, 0.08);
    transition:
      box-shadow 0.2s,
      background 0.2s;

    &:hover {
      background: #fff;
      box-shadow: 0 0 0 1px rgba(9, 105, 218, 0.28);
    }

    &.is-focus {
      background: #fff;
      box-shadow:
        0 0 0 2px rgba(9, 105, 218, 0.28),
        0 4px 12px rgba(9, 105, 218, 0.12);
    }
  }

  :deep(.el-input__inner) {
    font-size: 15px;
    height: 44px;
    color: $ink;

    &::placeholder {
      color: $ink-mute;
    }
  }

  .submit-item {
    margin-bottom: 0;
    margin-top: 8px;
  }

  .login-btn {
    width: 100%;
    height: 46px;
    font-size: 16px;
    font-weight: 600;
    letter-spacing: 6px;
    text-indent: 6px;
    border-radius: 6px;
    background: #1f883d;
    border: 1px solid rgba(31, 35, 40, 0.15);
    box-shadow: none;
    transition: background-color 0.15s;

    &:hover {
      background: #1a7f37;
      box-shadow: none;
    }

    &:active {
      background: #116329;
    }
  }
}

.portal-hint {
  margin: 16px 0 0;
  text-align: center;
  font-size: 13px;
  color: $ink-mute;

  &__link {
    margin-left: 4px;
    color: $accent;
    text-decoration: none;
    font-weight: 600;

    &:hover {
      color: $accent-bright;
      text-decoration: underline;
    }
  }
}

.demo-login-alert {
  margin-top: 16px;
}

.login-footer {
  margin-top: auto;
  padding-top: 24px;
  text-align: center;
  border-top: 1px solid rgba(12, 27, 42, 0.06);

  .copyright {
    margin: 0;
    font-size: 12px;
    color: $ink-mute;
  }
}

@keyframes float-up {
  0% {
    transform: translateY(0) scale(1);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  100% {
    transform: translateY(-100vh) scale(0.5);
    opacity: 0;
  }
}

@keyframes orb-float {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  33% {
    transform: translate(24px, -16px) scale(1.04);
  }
  66% {
    transform: translate(-16px, 12px) scale(0.96);
  }
}

@keyframes fade-in-left {
  from {
    opacity: 0;
    transform: translateX(-24px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes fade-in-right {
  from {
    opacity: 0;
    transform: translateX(24px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@media (max-width: 1024px) {
  .login-layout {
    display: flex;
    justify-content: center;
    max-width: none;
    padding: 24px;
  }

  .brand-panel {
    display: none;
  }

  .form-panel {
    max-width: 420px;
  }

  .login-header .login-eyebrow {
    display: block;
  }
}

@media (max-width: 480px) {
  .login-box {
    min-height: auto;
    padding: 28px 20px 22px;
  }

  .login-header .title {
    font-size: 24px;
  }
}

:deep(.captcha-dialog) {
  .el-dialog__body {
    padding-top: 8px;
    padding-bottom: 24px;
  }
}
</style>
