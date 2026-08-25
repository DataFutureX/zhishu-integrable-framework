import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())

  return {
    plugins: [
      vue(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        dts: 'src/types/auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/types/components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_PORT) || 3000,
      // E2E（Playwright webServer）设置 PW_TEST=1，避免自动打开浏览器
      open: process.env.PW_TEST !== '1',
      // 独立文档页 /docs 引用仓库根 README 与 docs/*.md
      fs: {
        allow: [fileURLToPath(new URL('..', import.meta.url))],
      },
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8180',
          changeOrigin: true,
        },
        '/swagger-ui': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8180',
          changeOrigin: true,
        },
        '/v3/api-docs': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8180',
          changeOrigin: true,
        },
        '/webjars': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8180',
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      modulePreload: {
        resolveDependencies(_filename, deps) {
          return deps.filter(
            (dep) =>
              !dep.includes('element-plus') &&
              !dep.includes('portal-markdown') &&
              !dep.includes('portal-doc-routes'),
          )
        },
      },
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('vite/preload-helper') || id.includes('vite/modulepreload-polyfill')) {
              return 'vite-preload'
            }
            if (!id.includes('node_modules')) {
              if (id.includes('portalDocRoutes')) return 'portal-doc-routes'
              if (id.includes('markdown-it')) return 'markdown-it'
              if (id.includes('portalMarkdown')) return 'portal-markdown'
              return
            }
            if (id.includes('nprogress')) return 'nprogress'
            if (id.includes('element-plus') || id.includes('@element-plus')) {
              return 'element-plus'
            }
            if (
              id.includes('/vue/') ||
              id.includes('/vue-router/') ||
              id.includes('/pinia/') ||
              id.includes('/@vue/')
            ) {
              return 'vue-vendor'
            }
            if (id.includes('axios')) {
              return 'axios'
            }
          },
        },
      },
    },
  }
})
