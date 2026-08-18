import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { setupPageTransition } from './plugins/pageTransition'
import { setupPermissionDirective } from './directives/permission'
import { updateBrowserFavicon } from './utils/favicon'
import defaultSystemIcon from './assets/yunqi-icon.png'
import './styles/index.scss'
import 'nprogress/nprogress.css'

const app = createApp(App)

// 菜单动态 icon 名依赖全局注册；按需页面仍可显式 import 图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
setupPermissionDirective(app)
app.use(router)
setupPageTransition(router)

void updateBrowserFavicon(defaultSystemIcon)

app.mount('#app')
