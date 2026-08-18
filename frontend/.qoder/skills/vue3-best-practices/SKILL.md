---
name: vue3-best-practices
description: Vue3前端开发最佳实践指南，包含Composition API规范、TypeScript类型安全、Pinia状态管理、Element Plus组件库集成、性能优化和工程化配置。当用户开发Vue3项目、创建组件、配置项目或询问Vue3相关问题时使用此技能。
---

# Vue3 前端开发最佳实践

## 核心技术栈

- **框架**: Vue 3.4+ (Composition API + `<script setup>`)
- **语言**: TypeScript 5.x (严格模式)
- **构建工具**: Vite 5.x
- **状态管理**: Pinia 2.x
- **路由**: Vue Router 4.x
- **UI框架**: Element Plus 2.x
- **HTTP客户端**: Axios 1.x
- **样式**: SCSS + CSS Modules / UnoCSS

## 项目结构规范

```
src/
├── api/              # API接口封装（按模块划分）
├── assets/           # 静态资源
├── components/       # 通用组件（PascalCase命名）
├── composables/      # 组合式函数（useXxx.ts）
├── layouts/          # 布局组件
├── router/           # 路由配置
├── stores/           # Pinia状态管理
├── styles/           # 全局样式
├── types/            # TypeScript类型定义
├── utils/            # 工具函数
├── views/            # 页面组件（按业务模块划分）
├── App.vue
└── main.ts
```

## Composition API 强制规范

### 1. 必须使用 `<script setup lang="ts">`

禁止使用 Options API (`data`, `methods`, `computed` 等)。所有组件必须使用 `<script setup lang="ts">` 语法。

**详细示例**: 参见 [examples.md](examples.md#基础组件模板)

### 2. 响应式数据选择

- **基础类型**: 使用 `ref()` (string, number, boolean)
- **对象类型**: 优先使用 `reactive()`,需要解构时配合 `toRefs()`
- **计算属性**: 使用 `computed()` 缓存复杂逻辑
- **侦听器**: 使用 `watch()` 或 `watchEffect()`

### 3. Props 和 Emits

- Props 必须定义 TypeScript 接口,使用 `withDefaults` 设置默认值
- Emits 使用类型化定义,明确参数类型
- Props 是只读的,禁止直接修改,通过 emits 通知父组件

### 4. 生命周期钩子

使用 Composition API 的生命周期:`onMounted`, `onUnmounted`, `onBeforeMount` 等。必须在 `onUnmounted` 中清理资源(定时器、事件监听器、WebSocket等)。

**详细说明**: 参见 [examples.md](examples.md)

## TypeScript 类型规范

### 核心原则

- **禁止 any 类型**: 使用具体类型或 `unknown`
- **接口定义**: 优先使用 `interface` 定义对象类型,`type` 定义联合类型
- **API 响应**: 所有请求/响应必须定义接口类型
- **泛型使用**: 封装通用组件/工具时合理使用泛型
- **枚举**: 使用 `enum` 或常量对象定义枚举值

**类型定义示例**: 参见 [examples.md](examples.md)

## Pinia 状态管理

### Store 定义要点

- 使用 setup 语法定义 store
- State 使用 `ref/reactive`
- Getters 使用 `computed`
- Actions 包含完整的错误处理
- 返回所有需要的属性和方法

**完整示例**: 参见 [examples.md](examples.md#pinia-store-定义)

## API 请求封装

### 核心要求

- 统一的 Axios 实例(包含请求/响应拦截器)
- 按模块封装 API 函数
- 完整的错误处理和 Loading 状态
- 所有请求/响应定义 TypeScript 类型
- 组件销毁时取消未完成的请求

**Axios配置和API封装示例**: 参见 [examples.md](examples.md#api-请求封装)

## Element Plus 集成规范

### 按需导入配置

使用 `unplugin-auto-import` 和 `unplugin-vue-components` 实现自动按需导入。

### 常用场景

- **表单验证**: 使用 `FormInstance` 和 `FormRules` 类型
- **表格分页**: 结合 `v-loading` 和分页组件
- **消息提示**: 使用 `ElMessage`, `ElMessageBox`
- **图标**: 安装 `@element-plus/icons-vue`

**完整示例**: 参见 [examples.md](examples.md#element-plus-集成)

## 组合式函数 (Composables)

### 设计规范

- 文件名以 `use` 开头 (如 `useRequest.ts`)
- 返回响应式数据和操作方法
- 支持可选配置项和回调函数
- 完整的 TypeScript 类型定义

**常用Composables**: 参见 [examples.md](examples.md#composables-示例)

## 性能优化规范

### 关键优化点

1. **路由懒加载**: 使用动态导入 `() => import('./Xxx.vue')`
2. **计算属性缓存**: 依赖响应式数据的复杂逻辑使用 `computed`
3. **防抖节流**: 高频事件使用 debounce/throttle
4. **组件销毁清理**: 在 `onUnmounted` 中清理资源
5. **避免 v-for 与 v-if 同用**: 使用 computed 预先过滤
6. **代码分割**: Vite 配置 manualChunks 分包
7. **图片优化**: 使用压缩和懒加载

**详细配置**: 参见 [engineering.md](engineering.md#性能优化配置)

## 样式规范

### 作用域样式

- 所有组件样式必须使用 `scoped` 或 CSS Modules
- 修改子组件样式使用 `:deep()` 选择器
- CSS 类名使用 kebab-case
- 避免内联样式,使用动态 class 绑定

**示例**: 参见 [examples.md](examples.md)

## 常见陷阱和反模式

### 必须避免的错误

1. **直接修改 props**: Props 是只读的,通过 emits 通知父组件
2. **v-for 和 v-if 同用**: 使用 computed 预先过滤数据
3. **使用索引作为 key**: 必须使用唯一 ID
4. **未处理的异步错误**: 所有 async/await 必须包含 try-catch
5. **硬编码魔法字符串**: 提取为常量或枚举
6. **忽略 ESLint 警告**: 必须解决问题而非忽略

**详细对比**: 参见 [examples.md](examples.md#常见陷阱和反模式)

## 工具链配置

### 必需的配置

- **ESLint**: 代码质量检查 (@typescript-eslint, eslint-plugin-vue)
- **Prettier**: 代码格式化
- **Husky + lint-staged**: Git Hooks 预提交检查
- **TypeScript**: 严格模式,路径别名配置
- **Vite**: 构建优化,代理配置

**完整配置**: 参见 [engineering.md](engineering.md#工具链配置)

## 快速检查清单

### 创建组件时

- [ ] 使用 `<script setup lang="ts">`
- [ ] Props有明确的TypeScript类型定义
- [ ] 没有使用 `any` 类型
- [ ] 响应式数据正确使用 ref/reactive
- [ ] 异步操作有错误处理
- [ ] 组件销毁时清理资源
- [ ] 样式使用 scoped 或 CSS Modules
- [ ] 列表渲染使用唯一key

### 创建API接口时

- [ ] 定义了请求和响应的TypeScript类型
- [ ] 使用了统一的axios实例
- [ ] 有适当的错误处理
- [ ] 函数命名清晰(getUserList, createUser等)

### 创建Pinia Store时

- [ ] 使用setup语法定义store
- [ ] state使用ref/reactive
- [ ] getters使用computed
- [ ] actions有错误处理
- [ ] 返回所有需要的属性和方法

## 参考资源

- [Vue 3 官方文档](https://cn.vuejs.org/)
- [TypeScript 手册](https://www.typescriptlang.org/docs/)
- [Pinia 文档](https://pinia.vuejs.org/zh/)
- [Element Plus 文档](https://element-plus.org/zh-CN/)
- [Vite 官方文档](https://cn.vitejs.dev/)
- [工程化配置详解](engineering.md)
- [常用代码示例](examples.md)
