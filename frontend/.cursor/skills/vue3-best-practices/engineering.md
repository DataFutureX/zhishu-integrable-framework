# 工程化配置参考

## 完整的项目初始化流程

### 1. 创建Vite项目

```bash
npm create vite@latest my-vue-app -- --template vue-ts
cd my-vue-app
npm install
```

### 2. 安装核心依赖

```bash
# 路由和状态管理
npm install vue-router@4 pinia

# Element Plus
npm install element-plus
npm install @element-plus/icons-vue

# Axios
npm install axios

# 开发依赖
npm install -D unplugin-auto-import unplugin-vue-components
npm install -D sass
npm install -D @types/node
```

### 3. 安装代码质量工具

```bash
# ESLint
npm install -D eslint @typescript-eslint/parser @typescript-eslint/eslint-plugin
npm install -D eslint-plugin-vue @vue/eslint-config-typescript

# Prettier
npm install -D prettier eslint-config-prettier eslint-plugin-prettier

# Husky + lint-staged (Git Hooks)
npm install -D husky lint-staged
```

### 4. 初始化Husky

```bash
npx husky init
```

在 `.husky/pre-commit` 中添加:

```bash
#!/usr/bin/env sh
. "$(dirname -- "$0")/_/husky.sh"

npx lint-staged
```

在 `package.json` 中添加:

```json
{
  "lint-staged": {
    "*.{js,jsx,ts,tsx,vue}": [
      "eslint --fix",
      "prettier --write"
    ]
  }
}
```

## 环境变量配置

### .env.development

```env
NODE_ENV=development
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_TITLE=开发环境
```

### .env.production

```env
NODE_ENV=production
VITE_API_BASE_URL=https://api.example.com
VITE_APP_TITLE=生产环境
```

### 使用环境变量

```typescript
// 在代码中使用
const apiUrl = import.meta.env.VITE_API_BASE_URL
const appTitle = import.meta.env.VITE_APP_TITLE
```

## Docker 部署配置

### Dockerfile

```dockerfile
# 构建阶段
FROM node:18-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# 生产阶段
FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### nginx.conf

```nginx
server {
    listen 80;
    server_name localhost;
    
    root /usr/share/nginx/html;
    index index.html;
    
    # Gzip压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;
    
    # Vue Router History模式支持
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # API代理
    location /api/ {
        proxy_pass http://backend:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  frontend:
    build: .
    ports:
      - "80:80"
    environment:
      - NODE_ENV=production
    restart: unless-stopped
```

## CI/CD 配置

### GitHub Actions

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
          cache: 'npm'
      
      - name: Install dependencies
        run: npm ci
      
      - name: Lint
        run: npm run lint
      
      - name: Build
        run: npm run build
        env:
          VITE_API_BASE_URL: ${{ secrets.API_BASE_URL }}
      
      - name: Deploy to server
        uses: appleboy/scp-action@master
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          source: "dist/*"
          target: "/var/www/html"
```

## VSCode 配置

### .vscode/settings.json

```json
{
  "editor.formatOnSave": true,
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": true
  },
  "files.associations": {
    "*.vue": "vue"
  },
  "emmet.includeLanguages": {
    "vue": "html"
  },
  "[vue]": {
    "editor.defaultFormatter": "esbenp.prettier-vscode"
  },
  "[typescript]": {
    "editor.defaultFormatter": "esbenp.prettier-vscode"
  }
}
```

### .vscode/extensions.json

```json
{
  "recommendations": [
    "Vue.volar",
    "dbaeumer.vscode-eslint",
    "esbenp.prettier-vscode",
    "bradlc.vscode-tailwindcss",
    "stylelint.vscode-stylelint"
  ]
}
```

## 性能优化配置

### Vite构建优化

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import compression from 'vite-plugin-compression'

export default defineConfig({
  plugins: [
    vue(),
    // Gzip压缩
    compression({
      algorithm: 'gzip',
      threshold: 10240
    })
  ],
  build: {
    // 代码分割
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus'],
          'utils': ['axios', 'dayjs']
        }
      }
    },
    // 分包大小警告限制
    chunkSizeWarningLimit: 1000,
    // 启用sourcemap（生产环境可关闭）
    sourcemap: false,
    // 压缩
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    }
  }
})
```

### 图片优化

```typescript
// vite.config.ts
import imagemin from 'vite-plugin-imagemin'

export default defineConfig({
  plugins: [
    imagemin({
      gifsicle: {
        optimizationLevel: 7,
        interlaced: false
      },
      optipng: {
        optimizationLevel: 7
      },
      mozjpeg: {
        quality: 80
      },
      pngquant: {
        quality: [0.8, 0.9]
      },
      svgo: {
        plugins: [
          { removeViewBox: false },
          { removeEmptyAttrs: false }
        ]
      }
    })
  ]
})
```

## 监控和错误追踪

### Sentry集成

```bash
npm install @sentry/vue @sentry/tracing
```

```typescript
// main.ts
import * as Sentry from '@sentry/vue'
import { BrowserTracing } from '@sentry/tracing'

Sentry.init({
  app,
  dsn: import.meta.env.VITE_SENTRY_DSN,
  integrations: [
    new BrowserTracing({
      routingInstrumentation: Sentry.vueRouterInstrumentation(router),
      tracingOrigins: ['localhost', /^\//]
    })
  ],
  tracesSampleRate: 1.0,
  release: import.meta.env.VITE_APP_VERSION
})
```

### 性能监控

```typescript
// composables/usePerformance.ts
export function usePerformance() {
  function reportWebVitals() {
    if ('performance' in window) {
      const perfEntries = performance.getEntriesByType('navigation')
      
      if (perfEntries.length > 0) {
        const [entry] = perfEntries
        
        console.log('FP:', entry.fetchStart)
        console.log('FCP:', entry.responseEnd - entry.requestStart)
        console.log('Load:', entry.loadEventEnd - entry.startTime)
        
        // 上报到监控平台
        // analytics.track('web_vitals', {
        //   fcp: entry.responseEnd - entry.requestStart,
        //   load: entry.loadEventEnd - entry.startTime
        // })
      }
    }
  }

  return {
    reportWebVitals
  }
}
```

## 测试配置

### Vitest + Vue Test Utils

```bash
npm install -D vitest @vue/test-utils jsdom
```

### vitest.config.ts

```typescript
/// <reference types="vitest" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts']
  }
})
```

### 组件测试示例

```typescript
// tests/components/Button.spec.ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseButton from '@/components/BaseButton.vue'

describe('BaseButton', () => {
  it('renders correctly', () => {
    const wrapper = mount(BaseButton, {
      props: {
        label: 'Click me'
      }
    })
    
    expect(wrapper.text()).toContain('Click me')
  })

  it('emits click event', async () => {
    const wrapper = mount(BaseButton)
    
    await wrapper.trigger('click')
    
    expect(wrapper.emitted('click')).toBeTruthy()
  })
})
```

## 常用脚本

### package.json scripts

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc && vite build",
    "preview": "vite preview",
    "lint": "eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts --fix",
    "format": "prettier --write src/",
    "test": "vitest",
    "test:coverage": "vitest --coverage",
    "prepare": "husky install"
  }
}
```

## 常见问题解决

### 1. TypeScript类型检查慢

```json
// tsconfig.json
{
  "compilerOptions": {
    "skipLibCheck": true,
    "incremental": true
  }
}
```

### 2. 热更新慢

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    hmr: {
      overlay: true
    },
    watch: {
      usePolling: false,
      ignored: ['**/node_modules/**', '**/.git/**']
    }
  }
})
```

### 3. 大文件加载慢

```typescript
// 使用动态导入
const HeavyComponent = defineAsyncComponent(() => 
  import('@/components/HeavyComponent.vue')
)

// 预加载
function preloadRoute(route: string) {
  import(`@/views/${route}.vue`)
}
```
