<template>
  <div class="system-config-page">
    <section v-loading="loading" class="content-panel">
      <header class="page-header">
        <div class="page-header__main">
          <h1 class="page-header__title">系统设置</h1>
          <p class="page-header__desc">
            配置品牌标识、展示文案、项目地坐标与登录安全策略
          </p>
        </div>
        <p v-if="configData.updateTime" class="page-header__meta">
          最近更新 {{ formatDateTime(configData.updateTime) }}
        </p>
      </header>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
        class="settings-form"
      >
        <div class="settings-layout">
          <aside class="settings-nav" aria-label="设置分类导航">
            <div class="settings-nav__title">设置分类</div>
            <nav class="settings-nav__list">
              <button
                v-for="item in settingSections"
                :key="item.id"
                type="button"
                class="settings-nav__item"
                :class="{ 'is-active': activeSectionId === item.id }"
                @click="scrollToSection(item.id)"
              >
                <span class="settings-nav__icon" :class="`settings-nav__icon--${item.id}`">
                  <el-icon :size="16"><component :is="item.icon" /></el-icon>
                </span>
                <span class="settings-nav__text">
                  <span class="settings-nav__label">{{ item.title }}</span>
                  <span class="settings-nav__hint">{{ item.hint }}</span>
                </span>
              </button>
            </nav>
          </aside>

          <div ref="settingsMainRef" class="settings-main">
            <!-- 品牌标识 -->
            <div id="setting-section-brand" class="settings-section">
            <div class="settings-section__head">
              <div class="settings-section__icon settings-section__icon--brand">
                <el-icon :size="18"><OfficeBuilding /></el-icon>
              </div>
              <div>
                <div class="settings-section__title">品牌标识</div>
                <div class="settings-section__desc">设置平台名称、英文标题与系统图标，将应用于浏览器标题与导航栏</div>
              </div>
            </div>

            <div class="settings-section__body">
              <el-form-item label="系统名称" prop="systemName">
                <el-input
                  v-model="formData.systemName"
                  placeholder="请输入系统名称"
                  maxlength="100"
                  show-word-limit
                  class="settings-input"
                />
              </el-form-item>

              <el-form-item label="英文标题" prop="englishTitle">
                <el-input
                  v-model="formData.englishTitle"
                  placeholder="请输入英文标题，如 YunQi Application Platform"
                  maxlength="100"
                  show-word-limit
                  class="settings-input"
                />
              </el-form-item>

              <el-form-item label="系统图标">
                <div class="icon-upload-area">
                  <div class="icon-preview" :class="{ 'has-image': !!iconPreviewUrl }">
                    <img
                      v-if="iconPreviewUrl"
                      :src="iconPreviewUrl"
                      alt="系统图标"
                      class="icon-image"
                    />
                    <div v-else class="icon-placeholder">
                      <el-icon :size="32"><Picture /></el-icon>
                      <span>暂无图标</span>
                    </div>
                  </div>
                  <div class="icon-actions">
                    <el-upload
                      :show-file-list="false"
                      :http-request="handleIconUpload"
                      accept=".png,.jpg,.jpeg,.gif,.svg,.ico,.webp"
                    >
                      <el-button type="primary" :icon="Upload" :loading="iconUploading">
                        上传图标
                      </el-button>
                    </el-upload>
                    <p class="icon-tip">支持 png / jpg / jpeg / gif / svg / ico / webp，最大 2MB</p>
                    <el-input
                      v-model="formData.systemIcon"
                      placeholder="或直接输入图标 URL"
                      clearable
                      class="settings-input settings-input--url"
                    />
                  </div>
                </div>
              </el-form-item>
            </div>
          </div>

          <!-- 展示文案 -->
          <div id="setting-section-content" class="settings-section">
            <div class="settings-section__head">
              <div class="settings-section__icon settings-section__icon--content">
                <el-icon :size="18"><Document /></el-icon>
              </div>
              <div>
                <div class="settings-section__title">展示文案</div>
                <div class="settings-section__desc">版权信息与系统介绍将展示在登录页与仪表盘欢迎区</div>
              </div>
            </div>

            <div class="settings-section__body">
              <el-form-item label="版权信息">
                <el-input
                  v-model="formData.copyright"
                  type="textarea"
                  :rows="3"
                  placeholder="请输入版权信息，如 © 2026 云起应用平台 · MIT 开源"
                  maxlength="200"
                  show-word-limit
                  class="settings-input"
                />
              </el-form-item>

              <el-form-item label="系统介绍" prop="systemIntroduction">
                <el-input
                  v-model="formData.systemIntroduction"
                  type="textarea"
                  :rows="5"
                  placeholder="请输入系统介绍，将展示在登录页与仪表盘欢迎区"
                  maxlength="5000"
                  show-word-limit
                  class="settings-input"
                />
              </el-form-item>
            </div>
          </div>

          <!-- 安全配置 -->
          <div id="setting-section-security" class="settings-section">
            <div class="settings-section__head">
              <div class="settings-section__icon settings-section__icon--security">
                <el-icon :size="18"><Lock /></el-icon>
              </div>
              <div>
                <div class="settings-section__title">安全配置</div>
                <div class="settings-section__desc">
                  控制登录失败重试次数与账号锁定时长，降低暴力破解风险
                </div>
              </div>
            </div>

            <div class="settings-section__body">
              <el-form-item label="重试限制" prop="loginRetryLimitEnabled">
                <div class="security-switch-row">
                  <el-switch
                    v-model="formData.loginRetryLimitEnabled"
                    active-text="启用"
                    inactive-text="关闭"
                  />
                  <span class="security-switch-tip">
                    开启后，连续登录失败达到上限将临时锁定账号
                  </span>
                </div>
              </el-form-item>

              <el-form-item label="最大重试" prop="loginMaxRetryAttempts">
                <el-input-number
                  v-model="formData.loginMaxRetryAttempts"
                  :min="1"
                  :max="20"
                  :disabled="!formData.loginRetryLimitEnabled"
                  controls-position="right"
                  class="settings-number"
                />
                <span class="field-tip">次，范围 1-20</span>
              </el-form-item>

              <el-form-item label="锁定时长" prop="loginLockMinutes">
                <el-input-number
                  v-model="formData.loginLockMinutes"
                  :min="1"
                  :max="1440"
                  :disabled="!formData.loginRetryLimitEnabled"
                  controls-position="right"
                  class="settings-number"
                />
                <span class="field-tip">分钟，范围 1-1440</span>
              </el-form-item>
            </div>
          </div>
          </div>
        </div>

        <div class="settings-footer">
          <el-button
            v-permission="PERMISSIONS.SYSTEM_CONFIG_EDIT"
            type="primary"
            :icon="Check"
            :loading="submitLoading"
            @click="handleSubmit"
          >
            保存设置
          </el-button>
          <el-button
            v-permission="PERMISSIONS.SYSTEM_CONFIG_EDIT"
            :icon="RefreshLeft"
            @click="handleReset"
          >
            重置
          </el-button>
          <el-button :icon="Refresh" :loading="loading" @click="fetchConfig">刷新</el-button>
        </div>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, type Component } from 'vue'
import { ElMessage, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import {
  Picture,
  Check,
  Refresh,
  RefreshLeft,
  Upload,
  OfficeBuilding,
  Document,
  Lock,
} from '@element-plus/icons-vue'
import {
  getSystemConfigApi,
  updateSystemConfigApi,
  uploadSystemIconApi,
} from '@/api/systemConfig'
import type { SystemConfigVO, SystemConfigUpdateDTO } from '@/types/systemConfig'
import { formatDateTime } from '@/utils/format'
import { resolveAssetUrl } from '@/utils/asset'
import {
  useSystemConfigStore,
  DEFAULT_LOGIN_LOCK_MINUTES,
  DEFAULT_LOGIN_MAX_RETRY_ATTEMPTS,
  DEFAULT_LOGIN_RETRY_LIMIT_ENABLED,
} from '@/stores/useSystemConfigStore'
import { PERMISSIONS } from '@/constants/permissions'

interface SettingSection {
  id: string
  title: string
  hint: string
  icon: Component
}

const settingSections: SettingSection[] = [
  { id: 'brand', title: '品牌标识', hint: '名称与图标', icon: OfficeBuilding },
  { id: 'content', title: '展示文案', hint: '版权与介绍', icon: Document },
  { id: 'security', title: '安全配置', hint: '登录策略', icon: Lock },
]

const loading = ref(false)
const submitLoading = ref(false)
const iconUploading = ref(false)
const formRef = ref<FormInstance>()
const settingsMainRef = ref<HTMLElement | null>(null)
const systemConfigStore = useSystemConfigStore()
const activeSectionId = ref(settingSections[0].id)

let sectionObserver: IntersectionObserver | null = null

const configData = ref<SystemConfigVO>({
  systemName: '',
  englishTitle: '',
  systemIcon: '',
  copyright: '',
  systemIntroduction: '',
  projectSite: '',
})

const formData = reactive<SystemConfigUpdateDTO>({
  systemName: '',
  englishTitle: '',
  systemIcon: '',
  copyright: '',
  systemIntroduction: '',
  projectSite: '',
  loginRetryLimitEnabled: DEFAULT_LOGIN_RETRY_LIMIT_ENABLED,
  loginMaxRetryAttempts: DEFAULT_LOGIN_MAX_RETRY_ATTEMPTS,
  loginLockMinutes: DEFAULT_LOGIN_LOCK_MINUTES,
})

const formRules: FormRules = {
  systemName: [
    { required: true, message: '请输入系统名称', trigger: 'blur' },
    { max: 100, message: '系统名称不能超过 100 个字符', trigger: 'blur' },
  ],
  englishTitle: [
    { max: 100, message: '英文标题不能超过 100 个字符', trigger: 'blur' },
  ],
  systemIntroduction: [
    { max: 5000, message: '系统介绍不能超过 5000 个字符', trigger: 'blur' },
  ],
  loginMaxRetryAttempts: [
    { required: true, message: '请设置最大重试次数', trigger: 'change' },
    { type: 'number', min: 1, max: 20, message: '最大重试次数范围为 1-20', trigger: 'change' },
  ],
  loginLockMinutes: [
    { required: true, message: '请设置锁定时长', trigger: 'change' },
    { type: 'number', min: 1, max: 1440, message: '锁定时长范围为 1-1440 分钟', trigger: 'change' },
  ],
}

const iconPreviewUrl = computed(
  () => resolveAssetUrl(formData.systemIcon) || systemConfigStore.iconUrl,
)

const applyConfigToForm = (config: SystemConfigVO) => {
  configData.value = config
  formData.systemName = config.systemName || ''
  formData.englishTitle = config.englishTitle || ''
  formData.systemIcon = config.systemIcon || ''
  formData.copyright = config.copyright || ''
  formData.systemIntroduction = config.systemIntroduction || ''
  formData.projectSite = config.projectSite || ''
  formData.loginRetryLimitEnabled = config.loginRetryLimitEnabled ?? DEFAULT_LOGIN_RETRY_LIMIT_ENABLED
  formData.loginMaxRetryAttempts = config.loginMaxRetryAttempts ?? DEFAULT_LOGIN_MAX_RETRY_ATTEMPTS
  formData.loginLockMinutes = config.loginLockMinutes ?? DEFAULT_LOGIN_LOCK_MINUTES
}

const fetchConfig = async () => {
  loading.value = true
  try {
    const config = await getSystemConfigApi()
    applyConfigToForm(config)
  } catch (error) {
    console.error('获取系统配置失败:', error)
  } finally {
    loading.value = false
  }
}

const handleIconUpload = async (options: UploadRequestOptions) => {
  const file = options.file as File
  const maxSize = 2 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.warning('图标文件不能超过 2MB')
    options.onError?.(new Error('file too large') as never)
    return
  }

  iconUploading.value = true
  try {
    const iconUrl = await uploadSystemIconApi(file)
    formData.systemIcon = iconUrl
    ElMessage.success('图标上传成功')
    options.onSuccess?.(iconUrl as never)
  } catch (error) {
    console.error('上传系统图标失败:', error)
    options.onError?.(error as never)
  } finally {
    iconUploading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      const payload: SystemConfigUpdateDTO = {
        systemName: formData.systemName.trim(),
        englishTitle: formData.englishTitle?.trim() || undefined,
        systemIcon: formData.systemIcon?.trim() || undefined,
        copyright: formData.copyright?.trim() || undefined,
        systemIntroduction: formData.systemIntroduction?.trim() || undefined,
        projectSite: formData.projectSite?.trim() || undefined,
        loginRetryLimitEnabled: formData.loginRetryLimitEnabled,
        loginMaxRetryAttempts: formData.loginMaxRetryAttempts,
        loginLockMinutes: formData.loginLockMinutes,
      }
      const config = await updateSystemConfigApi(payload)
      applyConfigToForm(config)
      systemConfigStore.applyConfig(config)
      document.title = config.systemName
      ElMessage.success('系统设置保存成功')
    } catch (error) {
      console.error('保存系统配置失败:', error)
    } finally {
      submitLoading.value = false
    }
  })
}

const handleReset = () => {
  applyConfigToForm(configData.value)
  formRef.value?.clearValidate()
}

const scrollToSection = (sectionId: string) => {
  const target = document.getElementById(`setting-section-${sectionId}`)
  if (!target) return

  activeSectionId.value = sectionId
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const setupSectionObserver = () => {
  const scrollRoot = settingsMainRef.value
  if (!scrollRoot) return

  sectionObserver?.disconnect()
  sectionObserver = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)

      if (!visible.length) return

      const sectionId = visible[0].target.id.replace('setting-section-', '')
      if (settingSections.some((item) => item.id === sectionId)) {
        activeSectionId.value = sectionId
      }
    },
    {
      root: scrollRoot,
      rootMargin: '-72px 0px -58% 0px',
      threshold: [0, 0.15, 0.35, 0.6],
    },
  )

  settingSections.forEach((item) => {
    const element = document.getElementById(`setting-section-${item.id}`)
    if (element) {
      sectionObserver?.observe(element)
    }
  })
}

onMounted(async () => {
  await fetchConfig()
  await nextTick()
  setupSectionObserver()
})

onUnmounted(() => {
  sectionObserver?.disconnect()
  sectionObserver = null
})
</script>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.system-config-page {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: $border-radius-md;
  background: $bg-white;
  border: 1px solid $border-lighter;
  overflow: hidden;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  flex-shrink: 0;
  padding: 16px 20px;
  border-bottom: 1px solid $border-lighter;
  background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);

  &__main {
    min-width: 0;
  }

  &__title {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.35;
  }

  &__desc {
    margin: 6px 0 0;
    font-size: 13px;
    color: $text-secondary;
    line-height: 1.55;
    max-width: 720px;
  }

  &__meta {
    flex-shrink: 0;
    margin: 4px 0 0;
    font-size: 12px;
    color: $text-placeholder;
    white-space: nowrap;
  }
}

.settings-form {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.settings-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  align-items: stretch;
  overflow: hidden;
}

.settings-nav {
  min-height: 0;
  overflow-y: auto;
  padding: 16px 12px 20px 16px;
  border-right: 1px solid $border-lighter;
  background: #fafbfc;

  &__title {
    margin-bottom: 10px;
    padding: 0 10px;
    font-size: 12px;
    font-weight: 600;
    color: $text-secondary;
    letter-spacing: 0.04em;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 10px 10px;
    border: 1px solid transparent;
    border-radius: $border-radius-md;
    background: transparent;
    cursor: pointer;
    text-align: left;
    transition:
      background 0.15s ease,
      border-color 0.15s ease,
      box-shadow 0.15s ease;

    &:hover {
      background: rgba(9, 105, 218, 0.06);
      border-color: rgba(9, 105, 218, 0.12);
    }

    &.is-active {
      background: rgba(9, 105, 218, 0.1);
      border-color: rgba(9, 105, 218, 0.22);
      box-shadow: inset 3px 0 0 $primary-color;

      .settings-nav__label {
        color: $primary-color;
      }

      .settings-nav__hint {
        color: rgba(9, 105, 218, 0.75);
      }
    }
  }

  &__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 8px;
    flex-shrink: 0;

    &--brand {
      background: rgba(9, 105, 218, 0.12);
      color: $primary-color;
    }

    &--content {
      background: rgba(103, 194, 58, 0.12);
      color: $success-color;
    }

    &--security {
      background: rgba(245, 108, 108, 0.12);
      color: $danger-color;
    }
  }

  &__text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  &__label {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.3;
  }

  &__hint {
    font-size: 11px;
    color: $text-secondary;
    line-height: 1.3;
  }
}

.settings-main {
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px 24px;
}

.settings-main .settings-section {
  max-width: 860px;
}

.settings-section {
  margin-bottom: 20px;
  border: 1px solid $border-lighter;
  border-radius: $border-radius-md;
  overflow: hidden;
  background: #fff;
  scroll-margin-top: 12px;

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 14px 16px;
    border-bottom: 1px solid $border-lighter;
    background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: $border-radius-md;
    flex-shrink: 0;

    &--brand {
      background: rgba(9, 105, 218, 0.12);
      color: $primary-color;
    }

    &--content {
      background: rgba(103, 194, 58, 0.12);
      color: $success-color;
    }

    &--security {
      background: rgba(245, 108, 108, 0.12);
      color: $danger-color;
    }
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    line-height: 1.4;
  }

  &__desc {
    margin-top: 2px;
    font-size: 12px;
    color: $text-secondary;
    line-height: 1.5;
  }

  &__body {
    padding: 20px 16px 8px;

    :deep(.el-form-item) {
      margin-bottom: 20px;
    }

    :deep(.el-form-item__label) {
      color: $text-regular;
      font-weight: 500;
    }
  }
}

.settings-input {
  max-width: 560px;
  width: 100%;

  &--url {
    max-width: 400px;
  }
}

.icon-upload-area {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  flex-wrap: wrap;
}

.icon-preview {
  width: 96px;
  height: 96px;
  border: 1px dashed $border-color;
  border-radius: $border-radius-md;
  overflow: hidden;
  background: transparent;
  flex-shrink: 0;

  &.has-image {
    border: none;
    border-radius: 0;
    box-shadow: none;
  }
}

.icon-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 0;
  box-sizing: border-box;
  background: transparent;
}

.icon-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: $text-placeholder;
  font-size: 12px;
}

.icon-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  min-width: 200px;
}

.icon-tip {
  margin: 0;
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.5;
}

.security-switch-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.security-switch-tip,
.field-tip {
  font-size: 12px;
  color: $text-secondary;
  line-height: 1.5;
}

.settings-number {
  width: 140px;
}

.settings-footer {
  flex-shrink: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 24px 12px 244px;
  border-top: 1px solid $border-lighter;
  background: #fff;
}

@media (max-width: 960px) {
  .settings-layout {
    grid-template-columns: 1fr;
    grid-template-rows: auto minmax(0, 1fr);
  }

  .settings-nav {
    padding: 12px 16px;
    border-right: none;
    border-bottom: 1px solid $border-lighter;
    overflow-x: auto;
    overflow-y: hidden;

    &__list {
      flex-direction: row;
      gap: 8px;
      overflow-x: auto;
      padding-bottom: 2px;
      scrollbar-width: thin;
    }

    &__item {
      width: auto;
      flex-shrink: 0;
      box-shadow: none;

      &.is-active {
        box-shadow: inset 0 -3px 0 $primary-color;
      }
    }

    &__hint {
      display: none;
    }
  }

  .settings-main {
    padding: 16px;
  }

  .settings-footer {
    padding-inline: 16px;
  }
}
</style>
