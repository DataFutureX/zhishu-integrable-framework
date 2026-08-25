import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Connection, Cpu, Grid, Key } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/useUserStore'
import { useSystemConfigStore } from '@/stores/useSystemConfigStore'
import { DEMO_CREDENTIALS, isDemoMode } from '@/config/demo'

export function useLogin() {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  const systemConfigStore = useSystemConfigStore()

  const systemName = computed(() => systemConfigStore.systemName)
  const englishTitle = computed(() => systemConfigStore.displayEnglishTitle)
  const systemIconUrl = computed(() => systemConfigStore.iconUrl)
  const copyright = computed(() => systemConfigStore.copyright)
  const systemIntroduction = computed(() => systemConfigStore.systemIntroduction)

  const features = [
    {
      icon: Cpu,
      label: '统一技术架构',
      desc: 'Vue 3 + Spring Boot 模块化交付，降低应用搭建与维护成本',
    },
    {
      icon: Grid,
      label: '业务组件开箱',
      desc: '组织权限、系统配置、公告审计与运维监控即用',
    },
    {
      icon: Key,
      label: '权限安全体系',
      desc: 'RBAC、JWT、验证码与登录锁定，保障访问安全',
    },
    {
      icon: Connection,
      label: 'AI 与行业扩展',
      desc: '预留智能化与行业能力接入路径，快速构建应用系统',
    },
  ]

  const loginFormRef = ref<FormInstance>()
  const loading = ref(false)
  const captchaVisible = ref(false)

  const loginForm = reactive({
    username: '',
    password: '',
  })

  const loginRules: FormRules = {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' },
    ],
    password: [
      { required: true, message: '请输入密码', trigger: 'blur' },
      { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' },
    ],
  }

  /** 预生成粒子样式，避免重渲染时位置抖动 */
  const particles = Array.from({ length: 24 }, (_, i) => {
    const seed = (i * 7919 + 104729) % 1000
    const left = (seed / 10) % 100
    const delay = (seed % 50) / 10
    const duration = 4 + (seed % 40) / 10
    const size = 2 + (seed % 40) / 10
    return {
      id: i,
      style: {
        left: `${left}%`,
        animationDelay: `${delay}s`,
        animationDuration: `${duration}s`,
        width: `${size}px`,
        height: `${size}px`,
      },
    }
  })

  onMounted(() => {
    systemConfigStore.fetchConfig({ publicOnly: true })
    if (isDemoMode) {
      loginForm.username = DEMO_CREDENTIALS.username
      loginForm.password = DEMO_CREDENTIALS.password
    }
  })

  const goPortal = () => {
    router.push('/portal')
  }

  const handleLogin = async () => {
    if (!loginFormRef.value) return

    await loginFormRef.value.validate(async (valid) => {
      if (valid) {
        captchaVisible.value = true
      }
    })
  }

  const handleCaptchaClosed = () => {
    captchaVisible.value = false
  }

  const handleCaptchaSuccess = async (captchaToken: string) => {
    captchaVisible.value = false
    loading.value = true

    try {
      await userStore.login(loginForm.username, loginForm.password, captchaToken)
      ElMessage.success('登录成功')
      const redirect = route.query.redirect as string | undefined
      router.push(redirect && redirect !== '/login' ? redirect : '/')
    } catch (error) {
      console.error('登录失败:', error)
    } finally {
      loading.value = false
    }
  }

  return {
    isDemoMode,
    DEMO_CREDENTIALS,
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
  }
}
