import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Connection, Cpu, Link, Search } from '@element-plus/icons-vue'
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

  const capabilityTags = ['Agent 智能体', 'RAG', '知识图谱', 'MCP', '混合检索']

  const features = [
    {
      icon: Cpu,
      label: 'Agent 智能体',
      desc: '多 Agent 编排与会话，工具调用、工作流与运行轨迹可观测',
    },
    {
      icon: Search,
      label: 'RAG 混合检索',
      desc: '向量 + 关键词混合加强检索，知识库问答可追溯片段来源',
    },
    {
      icon: Connection,
      label: '知识图谱',
      desc: '实体关系可视化，GraphRAG 补全关联路径与影响面',
    },
    {
      icon: Link,
      label: 'MCP Hub',
      desc: '接入上游 MCP、对外签发 Client，工具目录统一编排',
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
    capabilityTags,
    features,
    loginFormRef,
    loading,
    captchaVisible,
    loginForm,
    loginRules,
    goPortal,
    handleLogin,
    handleCaptchaClosed,
    handleCaptchaSuccess,
  }
}
