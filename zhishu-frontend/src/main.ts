import { createApp } from 'vue'
import { createPinia } from 'pinia'
import axios from 'axios'

import App from './App.vue'
import router from './router'
import { setAppInstance } from './plugins/appRef'
import { setupPageTransition } from './plugins/pageTransition'
import { setupPermissionDirective } from './directives/permission'
import { showErrorMessage } from './utils/uiMessage'
import { HttpError } from './utils/request'
import { updateBrowserFavicon } from './utils/favicon'
import defaultSystemIcon from './assets/logo.svg'
import './styles/index.scss'
import 'nprogress/nprogress.css'

void import('./plugins/elementPlus').then(({ ensureElementPlus }) => ensureElementPlus())

const app = createApp(App)

setAppInstance(app)

app.use(createPinia())
setupPermissionDirective(app)
app.use(router)
setupPageTransition(router)

// 全局错误兜底：避免渲染异常导致白屏无提示（后续可在此接错误上报）
app.config.errorHandler = (error, _instance, info) => {
  console.error(`[app error] ${info}`, error)
  // 网络/业务失败已由 request 拦截器统一提示，这里只兜底真正的运行时异常
  if (error instanceof HttpError || axios.isAxiosError(error)) return
  showErrorMessage('页面发生异常，请刷新重试')
}

router.onError((error) => {
  console.error('[router error]', error)
  showErrorMessage('页面加载失败，请检查网络后重试')
})

void updateBrowserFavicon(defaultSystemIcon)

app.mount('#app')
