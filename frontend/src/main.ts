import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setAppInstance } from './plugins/appRef'
import { setupPageTransition } from './plugins/pageTransition'
import { setupPermissionDirective } from './directives/permission'
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

void updateBrowserFavicon(defaultSystemIcon)

app.mount('#app')
