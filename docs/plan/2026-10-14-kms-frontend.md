# 知识管理系统 前端实现计划（v0.0.1 + v0.1.0）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一套可演示的知识管理系统 Web 界面，覆盖后端已实现的全部能力，并在答辩时能直观展示。

**Architecture:** Vue 3 SPA（M4 前端交互层）。所有后端调用收口在 `src/api/`，组件不直接发请求；统一由一个 fetch 封装处理 token 注入与 `code` 判定。页面按 spec §3.2 的 P1–P7 清单实现，另加一个 RAG 问答页。

**Tech Stack:** Vue 3.4 · TypeScript · Vite 6 · naive-ui · Pinia · vue-router 4 · marked + DOMPurify · highlight.js · @heroicons/vue · @vueuse/core · SCSS

**Spec:** `docs/spec/kms-design-v0.0.1.md`（页面清单见 §3.2 M4）
**接口契约:** `kms-backend/docs/API.md` —— **本计划唯一的接口依据，字段名以它为准**

---

## Global Constraints

- **后端地址固定 `http://localhost:8090`**（8080 被占用，见后端 plan）。开发服务器用 Vite 默认的 5173
- 后端 CORS 已放行 `http://localhost:*`，**不需要配代理**
- 所有请求必须走 `src/api/client.ts` 的封装，组件内**不得出现裸 `fetch`**
- `Authorization` 头**不加 `Bearer` 前缀**（后端不接受，见 API.md §0）
- **统一按 `code` 判断成败**，不判断 HTTP 状态（唯二例外是 404 与 500）
- `code === 1002` 时由客户端封装统一清 token 并跳登录页，各页面不重复处理
- **检索结果的 `snippet` 是纯文本，必须用 Vue 模板渲染高亮，禁止 `v-html`**（后端刻意不拼 HTML 以防 XSS，前端再用 `v-html` 等于把防线拆了）
- 用户内容（标题、正文、标签、文件名）一律作为文本渲染；Markdown 正文经 DOMPurify 过滤后才允许注入
- 问答接口必须用 `fetch` + `ReadableStream` 消费，**不能用 `EventSource`**（后者无法带 Authorization 头）

## 技术栈说明：与参考项目的关系

参考项目 `software/ai-study-helper/iheima_vue3-main` 的技术栈可直接复用，但有一处必须说清：

| 项 | 参考项目 | 本计划 |
|---|---|---|
| Vue / Vite / Pinia / vue-router | ✅ 实际使用 | 沿用 |
| marked + DOMPurify + highlight.js | ✅ 实际使用 | 沿用 |
| @heroicons/vue、@vueuse/core | ✅ 实际使用 | 沿用 |
| **naive-ui** | ⚠️ **在 dependencies 里但 `src/` 中一处未用**，页面全是手写 SCSS | **采用**，但视为新引入的库 |
| HTTP 客户端 | 裸 `fetch`，无封装 | 加一层薄封装（token、code、1002 处理） |

**naive-ui 的选择理由**：本系统有分类树、表单、上传、分页、弹窗、消息提示等大量标准组件，
手写它们会吃掉大半工期。naive-ui 原生支持 Vue 3 与 TS，自带 `n-tree`（分类树）、`n-upload`、
`n-data-table`，中文文档完善。

**但要如实说明两点**：它在参考项目里没被用过，所以**它没有任何"已验证"的背书**；
参考项目手写 SCSS 的路子也能走通，只是 7 个页面全手写组件不现实。
若你更熟 Element Plus，换它同样可行——本计划中 naive-ui 的使用集中在 `n-tree`、`n-form`、
`n-upload`、`n-modal`、`n-message` 这几处，替换成本可控。

---

## 文件结构

```
kms-frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json / tsconfig.app.json / tsconfig.node.json
├── env.d.ts
├── index.html
└── src/
    ├── main.ts                     # 挂载 + naive-ui 按需注册
    ├── App.vue                     # 只有 RouterView 与全局消息容器
    ├── router/index.ts             # 路由 + 登录守卫
    ├── types/api.ts                # 与 API.md 一一对应的类型
    ├── api/
    │   ├── client.ts               # fetch 封装（唯一出口）
    │   ├── auth.ts  category.ts  knowledge.ts
    │   ├── tag.ts   attachment.ts  search.ts
    │   └── qa.ts                   # 含 SSE 流消费
    ├── stores/
    │   └── auth.ts                 # token + 当前用户（持久化到 localStorage）
    ├── composables/
    │   └── usePagedList.ts         # 分页列表的加载/错误/空态统一处理
    ├── components/
    │   ├── AppLayout.vue           # 侧栏 + 顶栏外壳
    │   ├── CategoryTree.vue        # 分类树（n-tree，含"全部"根节点）
    │   ├── MarkdownView.vue        # marked + DOMPurify + highlight.js
    │   ├── KeywordHighlight.vue    # 关键词高亮（纯模板渲染，无 v-html）
    │   ├── AttachmentPanel.vue     # 上传 + 解析进度轮询
    │   ├── KnowledgeCard.vue
    │   ├── QueryState.vue          # 加载 / 空 / 错误的统一呈现
    │   └── TagInput.vue
    └── views/
        ├── LoginView.vue           # P1
        ├── BrowseView.vue          # P2 知识浏览首页
        ├── DetailView.vue          # P3 知识详情
        ├── EditorView.vue          # P4 知识编辑器
        ├── SearchView.vue          # P5 检索结果
        ├── MineView.vue            # P6 我的知识库
        ├── CategoryAdminView.vue   # P7 分类管理
        └── QaView.vue              # 智能问答（v0.1.0）
```

---

### Task F1: 工程骨架、API 客户端与登录页

**Files:**
- Create: `kms-frontend/package.json`、`vite.config.ts`、`tsconfig*.json`、`index.html`、`env.d.ts`
- Create: `src/main.ts`、`src/App.vue`、`src/router/index.ts`
- Create: `src/types/api.ts`、`src/api/client.ts`、`src/api/auth.ts`
- Create: `src/stores/auth.ts`、`src/views/LoginView.vue`
- Test: 手动验证（前端不引入测试框架，见「验证方式」）

**Interfaces:**
- Consumes: 无（首个任务）
- Produces:
  - `client.get/post/put/del<T>(path, options)` → `Promise<T>`，失败时抛 `ApiError`
  - `ApiError { code: number; message: string }`
  - `useAuthStore()`：`token`、`user`、`isLoggedIn`、`login()`、`logout()`、`isAdmin`
  - 路由守卫：未登录访问写操作页面时跳 `/login?redirect=...`

- [ ] **Step 1: 创建 `package.json`**

```json
{
  "name": "kms-frontend",
  "version": "0.0.1",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "type-check": "vue-tsc --build",
    "preview": "vite preview"
  },
  "dependencies": {
    "@heroicons/vue": "^2.2.0",
    "@vueuse/core": "^12.7.0",
    "dompurify": "^3.2.4",
    "highlight.js": "^11.11.1",
    "marked": "^15.0.7",
    "naive-ui": "^2.41.0",
    "pinia": "^3.0.1",
    "vue": "^3.4.15",
    "vue-router": "^4.2.5"
  },
  "devDependencies": {
    "@tsconfig/node22": "^22.0.0",
    "@types/node": "^22.13.4",
    "@vitejs/plugin-vue": "^5.2.1",
    "@vue/tsconfig": "^0.7.0",
    "sass": "^1.85.1",
    "typescript": "~5.7.3",
    "vite": "^6.1.0",
    "vue-tsc": "^2.2.2"
  }
}
```

> **注意 `build` 不跑 `vue-tsc`**（参考项目用 `run-p` 并行跑类型检查与构建）。
> 类型检查独立成 `npm run type-check`：编辑期由 IDE 实时反馈，构建不被类型错误卡住。
> 工期紧的时候，这是有意的取舍。

- [ ] **Step 2: 创建 `vite.config.ts` 与 `tsconfig.json`**

```ts
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: { port: 5173 }
})
```

`tsconfig.json` 沿用 `@vue/tsconfig` 的组合（app + node 两份），
与参考项目一致，`paths` 里配置 `"@/*": ["./src/*"]`。

- [ ] **Step 3: 创建 `src/types/api.ts`**

按 API.md 逐字段对齐。**只列实际用到的字段，不凭空补字段**：

```ts
/** 统一响应体。data 可能不存在。 */
export interface ApiResult<T> {
  code: number
  message: string
  data?: T
}

export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface UserVO {
  id: number
  username: string
  nickname: string | null
  role: 'ADMIN' | 'USER'
}

export interface LoginResult {
  token: string
  user: UserVO
}

export interface CategoryNode {
  id: number
  name: string
  parentId: number
  level: number
  sort: number
  description: string | null
  children: CategoryNode[]
}

export type KnowledgeStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface KnowledgeListVO {
  id: number
  title: string
  summary: string | null
  status: KnowledgeStatus
  categoryId: number | null
  categoryName: string | null
  authorName: string | null
  viewCount: number
  updatedAt: string
}

export interface KnowledgeDetailVO {
  id: number
  title: string
  summary: string | null
  content: string | null
  contentType: string
  status: KnowledgeStatus
  categoryId: number | null
  categoryName: string | null
  authorId: number
  authorName: string | null
  viewCount: number
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface KnowledgeSaveRequest {
  title: string
  summary?: string
  content?: string
  contentType?: string
  categoryId?: number | null
  tagIds?: number[]
}

export interface TagVO {
  id: number
  name: string
  itemCount: number
}

export interface SearchHit {
  id: number
  title: string
  snippet: string | null
  categoryId: number | null
  categoryName: string | null
  tags: string[]
  status: KnowledgeStatus
  updatedAt: string
  score: number
}

export type ParseStatus = 'PENDING' | 'PARSING' | 'DONE' | 'FAILED'

export interface AttachmentVO {
  id: number
  itemId: number
  originalName: string
  mimeType: string | null
  sizeBytes: number
  parseStatus: ParseStatus
  parseError: string | null
  textPreview: string | null
  createdAt: string
}

export interface SourceRef {
  itemId: number
  title: string
  headingPath: string
  score: number
}

export interface QaMessage {
  role: 'user' | 'assistant'
  content: string
  sources: SourceRef[]
  at: string
}

export interface QaStatusVO {
  enabled: boolean
  indexedItems: number
  indexedChunks: number
  lastRebuildAt: string | null
}
```

- [ ] **Step 4: 创建 `src/api/client.ts`（唯一网络出口）**

```ts
import type { ApiResult } from '@/types/api'

export const BASE_URL = 'http://localhost:8090'

/** 业务失败。code 保留下来供调用方区分处理。 */
export class ApiError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

const TOKEN_KEY = 'kms.token'
export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY)
}

/** 由 main.ts 注入，避免 client 直接依赖 router 造成循环引用。 */
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

/** 查询参数。值可以是任意类型，序列化时统一转字符串并跳过空值。 */
export type QueryParams = Record<string, unknown>

interface RequestOptions {
  params?: QueryParams
  body?: unknown
}

function buildUrl(path: string, params?: QueryParams): string {
  const url = new URL(BASE_URL + path)
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      // 空值不传：后端据此走默认逻辑（如 status 不传表示按 mine 决定可见范围）
      if (value === undefined || value === null || value === '') continue
      url.searchParams.set(key, String(value))
    }
  }
  return url.toString()
}

/**
 * 解析统一响应体。上传等需要手工走 fetch 的场景复用它，
 * 保证 code 判定逻辑只有一处。
 */
export async function unwrap<T>(response: Response): Promise<T> {
  if (response.status === 404) throw new ApiError(1004, '接口不存在')
  if (response.status === 500) throw new ApiError(5000, '系统繁忙，请稍后重试')

  let payload: ApiResult<T>
  try {
    payload = await response.json()
  } catch {
    throw new ApiError(5000, '服务返回了无法解析的内容')
  }
  if (payload.code === 1002) {
    tokenStorage.clear()
    onUnauthorized()
    throw new ApiError(1002, payload.message || '登录已过期，请重新登录')
  }
  if (payload.code !== 0) {
    throw new ApiError(payload.code, payload.message || '操作失败')
  }
  return payload.data as T
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  const token = tokenStorage.get()
  // 不加 Bearer 前缀：后端不接受
  if (token) headers['Authorization'] = token
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(buildUrl(path, options.params), {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  })

  // 404 与 500 是仅有的两个按 HTTP 状态判断的情形
  if (response.status === 404) throw new ApiError(1004, '接口不存在')
  if (response.status === 500) throw new ApiError(5000, '系统繁忙，请稍后重试')

  return unwrap<T>(response)
}

export const http = {
  get: <T>(path: string, options?: RequestOptions) => request<T>('GET', path, options),
  post: <T>(path: string, options?: RequestOptions) => request<T>('POST', path, options),
  put: <T>(path: string, options?: RequestOptions) => request<T>('PUT', path, options),
  del: <T>(path: string, options?: RequestOptions) => request<T>('DELETE', path, options),
  /** 下载与 SSE 需要原始 Response */
  raw: (path: string, init?: RequestInit) =>
    fetch(buildUrl(path), {
      ...init,
      headers: { ...(init?.headers ?? {}), ...(tokenStorage.get() ? { Authorization: tokenStorage.get()! } : {}) }
    })
}

/** 统一错误提示。返回可读文案，避免各页面重复写分支。 */
export function describeError(e: unknown): string {
  if (e instanceof ApiError) return e.message
  if (e instanceof TypeError) return '无法连接服务器，请确认后端已启动'
  return '操作失败，请重试'
}
```

- [ ] **Step 5: 创建 `src/api/auth.ts`**

```ts
import { http } from './client'
import type { LoginResult, UserVO } from '@/types/api'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<LoginResult>('/api/auth/login', { body: { username, password } }),
  logout: () => http.post<void>('/api/auth/logout'),
  me: () => http.get<UserVO>('/api/auth/me')
}
```

- [ ] **Step 6: 创建 `src/stores/auth.ts`**

```ts
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import { tokenStorage } from '@/api/client'
import type { UserVO } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const token = ref<string | null>(tokenStorage.get())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')

  async function login(username: string, password: string) {
    const result = await authApi.login(username, password)
    token.value = result.token
    tokenStorage.set(result.token)
    user.value = result.user
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      // 后端失败也要清本地状态，否则用户会卡在"已登录但 token 无效"
      clear()
    }
  }

  /** 刷新页面后 token 还在，但 user 丢了，用它补回来。 */
  async function restore() {
    if (!token.value) return
    try {
      user.value = await authApi.me()
    } catch {
      clear()
    }
  }

  function clear() {
    token.value = null
    user.value = null
    tokenStorage.clear()
  }

  return { user, token, isLoggedIn, isAdmin, displayName, login, logout, restore, clear }
})
```

- [ ] **Step 7: 创建 `src/router/index.ts` 与 `src/main.ts`**

路由表（`meta.requiresAuth` 标记写操作页面）：

```
/login          LoginView        公开
/               BrowseView       公开（浏览）
/detail/:id     DetailView       公开
/search         SearchView       公开
/qa             QaView           公开（后端决定能否匿名提问）
/editor/new     EditorView       requiresAuth
/editor/:id     EditorView       requiresAuth
/mine           MineView         requiresAuth
/categories     CategoryAdminView requiresAuth
```

守卫逻辑：

```ts
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})
```

`main.ts` 里注入未授权处理，**避免 client.ts 直接 import router 造成循环依赖**：

```ts
setUnauthorizedHandler(() => {
  auth.clear()
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
})
```

- [ ] **Step 8: 创建 `src/views/LoginView.vue`**

- 用户名 / 密码表单，回车提交
- 顶部给出测试账号提示（`admin/admin123`、`zhangsan/user123`）——**演示时省事，也提醒这是课程项目**
- 登录成功后跳 `route.query.redirect` 或首页
- 失败时用 `n-message` 展示 `describeError(e)`

- [ ] **Step 9: 验证**

```bash
cd kms-frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`：
- 能打开登录页，用 admin/admin123 登录成功
- 故意输错密码，看到「用户名或密码错误」
- 后端未启动时，看到「无法连接服务器，请确认后端已启动」
- 刷新页面后仍是登录态（token 持久化 + `restore()`）

- [ ] **Step 10: 提交**

```bash
git add kms-frontend
git commit -m "feat(frontend): scaffold vue app with api client, auth store and login page"
```

---

### Task F2: 布局外壳与分类树组件

**Files:**
- Create: `src/components/AppLayout.vue`、`src/components/CategoryTree.vue`、`src/components/QueryState.vue`
- Create: `src/api/category.ts`
- Modify: `src/App.vue`、`src/router/index.ts`（页面套用布局）

**Interfaces:**
- Consumes: `http`（F1）、`useAuthStore`（F1）
- Produces:
  - `AppLayout.vue`：插槽 `sidebar` / default；顶栏含搜索框、当前用户、登录/登出
  - `CategoryTree.vue`：props `modelValue`（当前选中 id）、`showAll`（是否显示"全部"节点）；emit `update:modelValue`
  - `QueryState.vue`：props `loading` / `error` / `empty`；插槽 `empty`；默认插槽放正常内容
  - `categoryApi.tree()` → `CategoryNode[]`

- [ ] **Step 1: 创建 `src/api/category.ts`**

```ts
import { http } from './client'
import type { CategoryNode } from '@/types/api'

export interface CategorySaveRequest {
  name: string
  parentId?: number | null
  sort?: number
  description?: string
}

export const categoryApi = {
  tree: () => http.get<CategoryNode[]>('/api/categories/tree'),
  create: (body: CategorySaveRequest) => http.post<CategoryNode>('/api/categories', { body }),
  update: (id: number, body: CategorySaveRequest) =>
    http.put<CategoryNode>(`/api/categories/${id}`, { body }),
  remove: (id: number) => http.del<void>(`/api/categories/${id}`)
}
```

- [ ] **Step 2: 创建 `QueryState.vue`**

三种状态统一呈现，避免每个页面各写一套：

```vue
<script setup lang="ts">
defineProps<{
  loading?: boolean
  error?: string | null
  empty?: boolean
  emptyText?: string
  /** 出错时提供重试入口，而不是只显示一句话 */
  onRetry?: () => void
}>()
</script>

<template>
  <div v-if="loading" class="state-block">
    <n-spin size="medium" />
    <p class="hint">加载中…</p>
  </div>
  <div v-else-if="error" class="state-block">
    <n-empty :description="error">
      <template #extra>
        <n-button v-if="onRetry" size="small" @click="onRetry">重试</n-button>
      </template>
    </n-empty>
  </div>
  <div v-else-if="empty" class="state-block">
    <n-empty :description="emptyText || '暂无数据'">
      <template #extra><slot name="empty" /></template>
    </n-empty>
  </div>
  <slot v-else />
</template>
```

> **空态要给出口**：空列表页面必须通过 `#empty` 插槽给出下一步动作
> （如「还没有知识条目，去创建第一篇」），只显示"暂无数据"等于把用户丢在死路上。

- [ ] **Step 3: 创建 `CategoryTree.vue`**

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CategoryNode } from '@/types/api'

const props = withDefaults(defineProps<{
  /** 后端返回的分类树 */
  nodes: CategoryNode[]
  /** 当前选中的分类 id；null 表示"全部" */
  modelValue?: number | null
  /** 是否显示"全部"节点 */
  showAll?: boolean
  /** 是否显示"新建分类"入口 */
  allowCreate?: boolean
}>(), { modelValue: null, showAll: true, allowCreate: false })

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  create: [parentId: number | null]
  edit: [node: CategoryNode]
}>()

/** naive-ui 的 TreeOption 结构 */
interface TreeOption {
  key: number | string
  label: string
  children?: TreeOption[]
}

const ALL_KEY = '__all__'

/**
 * 后端返回的是嵌套结构，n-tree 也需要嵌套结构，但要求 key 字段名不同，
 * 且"全部"节点要插在最前面。转换必须保持顺序（后端已按 sort 排好）。
 */
const options = computed<TreeOption[]>(() => {
  const convert = (nodes: CategoryNode[]): TreeOption[] =>
    nodes.map((n) => ({
      key: n.id,
      label: n.name,
      children: n.children?.length ? convert(n.children) : undefined
    }))

  const result = convert(props.nodes ?? [])
  return props.showAll ? [{ key: ALL_KEY, label: '全部' }, ...result] : result
})

/** 展开的节点：默认展开根层级，让用户一眼看到结构 */
const expandedKeys = ref<Array<number | string>>(
  (props.nodes ?? []).map((n) => n.id)
)

const selectedKeys = computed(() => [
  props.modelValue === null ? ALL_KEY : props.modelValue
])

function handleSelect(keys: Array<number | string>) {
  const key = keys[0]
  if (key === undefined) {
    // 取消选择等同于回到"全部"
    emit('update:modelValue', null)
    return
  }
  // 重复点击已选中的分类则取消筛选，避免用户找不到"取消选择"的入口
  if (key === selectedKeys.value[0]) {
    emit('update:modelValue', null)
    return
  }
  emit('update:modelValue', key === ALL_KEY ? null : Number(key))
}

// 分类树刷新后，新增的根节点也应当展开
watch(() => props.nodes, (nodes) => {
  const known = new Set(expandedKeys.value)
  for (const n of nodes ?? []) known.add(n.id)
  expandedKeys.value = [...known]
}, { deep: true })
</script>

<template>
  <div class="category-tree">
    <n-tree
      block-line
      :data="options"
      :selected-keys="selectedKeys"
      :expanded-keys="expandedKeys"
      selectable
      :cancelable="false"
      @update:selected-keys="handleSelect"
      @update:expanded-keys="(keys: Array<number | string>) => (expandedKeys = keys)"
    />
    <n-button v-if="allowCreate" text size="small" class="create-btn"
              @click="emit('create', modelValue ?? null)">
      + 新建子分类
    </n-button>
  </div>
</template>
```

> **`cancelable` 必须显式设为 `false`**，取消选择的语义由 `handleSelect` 自己处理
> （重复点击取消、点空白回"全部"）。若交给 n-tree 默认行为，会出现
> "点一下选中、再点一下全部消失但没有回到全部"的混乱状态。

- [ ] **Step 4: 创建 `AppLayout.vue`**

- 左侧固定宽度侧栏：`<slot name="sidebar" />`，默认放 `CategoryTree`
- 顶栏：系统名（点击回首页）、全局搜索输入框（回车跳 `/search?keyword=`）、
  当前用户名与下拉菜单（我的知识库 / 分类管理 / 问答 / 登出）
- 未登录时顶栏显示"登录"按钮
- 内容区放默认插槽

- [ ] **Step 5: 改造 `App.vue` 与路由**

`App.vue` 只保留 `<n-message-provider>`、`<n-dialog-provider>` 与 `<router-view>`。
各业务页面自己在模板里套 `<AppLayout>`——**这样登录页可以不带侧栏**。

- [ ] **Step 6: 验证**

- 首页出现侧栏分类树，展开层级与后端返回一致（编程语言 > Java > Spring/JVM）
- 点击分类节点，选中态正确
- 顶栏搜索框回车跳到搜索页并带上 keyword
- 未登录时顶栏显示登录入口，登录后显示用户名与菜单

- [ ] **Step 7: 提交**

---

### Task F3: 知识浏览首页（P2）

**Files:**
- Create: `src/api/knowledge.ts`、`src/composables/usePagedList.ts`
- Create: `src/components/KnowledgeCard.vue`、`src/views/BrowseView.vue`

**Interfaces:**
- Consumes: `http`、`CategoryTree`（F2）、`QueryState`（F2）
- Produces:
  - `knowledgeApi.page(query)` → `Page<KnowledgeListVO>`
  - `knowledgeApi.detail(id)`、`create(req)`、`update(id, req)`、`remove(id)`
  - `knowledgeApi.publish(id)`、`archive(id)`、`restore(id)`
  - `usePagedList(fetcher)` → `{ records, total, page, size, loading, error, empty, reload }`

- [ ] **Step 1: 创建 `src/api/knowledge.ts`**

```ts
import { http } from './client'
import type {
  KnowledgeDetailVO, KnowledgeListVO, KnowledgeSaveRequest, Page
} from '@/types/api'

export interface KnowledgeQuery {
  categoryId?: number | null
  tagId?: number | null
  status?: string | null
  mine?: boolean
  page?: number
  size?: number
}

export const knowledgeApi = {
  page: (query: KnowledgeQuery) => http.get<Page<KnowledgeListVO>>('/api/knowledge', { params: { ...query } }),
  detail: (id: number) => http.get<KnowledgeDetailVO>(`/api/knowledge/${id}`),
  create: (body: KnowledgeSaveRequest) => http.post<KnowledgeDetailVO>('/api/knowledge', { body }),
  update: (id: number, body: KnowledgeSaveRequest) =>
    http.put<KnowledgeDetailVO>(`/api/knowledge/${id}`, { body }),
  remove: (id: number) => http.del<void>(`/api/knowledge/${id}`),
  publish: (id: number) => http.post<KnowledgeDetailVO>(`/api/knowledge/${id}/publish`),
  archive: (id: number) => http.post<KnowledgeDetailVO>(`/api/knowledge/${id}/archive`),
  restore: (id: number) => http.post<KnowledgeDetailVO>(`/api/knowledge/${id}/restore`)
}
```

- [ ] **Step 2: 创建 `usePagedList.ts`**

分页列表的加载、错误、空态、切页统一处理，避免每个列表页重复。
**并发保护**用请求序号实现：快速切页时先发的请求可能后返回，
不加序号会用旧结果覆盖新结果。

```ts
import { computed, ref, watch, type Ref } from 'vue'
import { describeError } from '@/api/client'
import type { Page } from '@/types/api'

export interface PagedListOptions {
  size?: number
  /** 额外筛选条件，变化时自动回到第一页并重新加载 */
  deps?: () => unknown[]
}

export function usePagedList<T>(
  fetcher: (page: number, size: number) => Promise<Page<T>>,
  options: PagedListOptions = {}
) {
  const records: Ref<T[]> = ref([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(options.size ?? 10)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const empty = computed(() => !loading.value && !error.value && records.value.length === 0)

  /** 请求序号：只有最后一次发出的请求才允许写回结果 */
  let requestId = 0

  async function reload() {
    const current = ++requestId
    loading.value = true
    error.value = null
    try {
      const result = await fetcher(page.value, size.value)
      if (current !== requestId) return          // 已被更新的请求取代，丢弃
      records.value = result.records ?? []
      total.value = result.total ?? 0
    } catch (e) {
      if (current !== requestId) return
      error.value = describeError(e)
      records.value = []
      total.value = 0
    } finally {
      if (current === requestId) loading.value = false
    }
  }

  /** 筛选条件变化时回到第一页再查，否则会停在一个空的第 3 页 */
  async function reset() {
    if (page.value !== 1) {
      page.value = 1            // 触发下面 watch 的 reload
    } else {
      await reload()
    }
  }

  if (options.deps) {
    watch(options.deps, reset, { deep: true })
  }
  watch(page, reload)

  return { records, total, page, size, loading, error, empty, reload, reset }
}
```

- [ ] **Step 3: 创建 `KnowledgeCard.vue`**

列表项：标题、摘要（两行截断）、分类标签、状态徽标（草稿/已发布/已归档用不同颜色）、
作者、浏览数、更新时间。整卡可点击跳详情。

- [ ] **Step 4: 创建 `BrowseView.vue`**

- 套 `AppLayout`
- 左侧分类树筛选，右侧条目列表 + 分页
- 顶部工具栏：状态筛选（已发布 / 我的全部）、排序不提供（列表接口无排序，**不要造后端没有的参数**）
- 空态给出「去创建第一篇」按钮

> **不要造接口没有的能力**：列表接口 `/api/knowledge` 没有 `sort` 参数，排序只在检索接口有。
> 前端加一个排序下拉而后端忽略它，是欺骗用户。

- [ ] **Step 5: 验证**

- 首页展示 17 条已发布条目
- 点击「Java」分类，列表只剩该子树的条目（含 Spring、JVM）
- 未登录时不显示"我的"筛选
- 分页切换正常

- [ ] **Step 6: 提交**

---

### Task F4: 知识详情页（P3）与附件面板

**Files:**
- Create: `src/components/MarkdownView.vue`、`src/components/AttachmentPanel.vue`
- Create: `src/api/attachment.ts`
- Create: `src/views/DetailView.vue`

**Interfaces:**
- Consumes: `knowledgeApi.detail`（F3）
- Produces:
  - `MarkdownView.vue`：props `content: string | null`
  - `AttachmentPanel.vue`：props `itemId`、`editable`
  - `attachmentApi.listByItem`、`upload`、`text`、`downloadUrl`、`remove`

- [ ] **Step 1: 创建 `src/api/attachment.ts`**

```ts
export const attachmentApi = {
  listByItem: (itemId: number) => http.get<AttachmentVO[]>(`/api/attachments/item/${itemId}`),
  /**
   * 上传走 FormData。
   * 不能复用 http.post：它会对 body 做 JSON.stringify，而 FormData 必须原样交给浏览器，
   * 由浏览器自己生成带 boundary 的 Content-Type —— 手工设置反而会让后端解析失败。
   */
  upload: async (itemId: number, file: File) => {
    const form = new FormData()
    form.append('itemId', String(itemId))
    form.append('file', file)
    const response = await http.raw('/api/attachments/upload', { method: 'POST', body: form })
    return unwrap<AttachmentVO>(response)
  },
  text: (id: number) => http.get<AttachmentVO>(`/api/attachments/${id}/text`),
  remove: (id: number) => http.del<void>(`/api/attachments/${id}`),
  /** 下载需要带 Authorization 头，因此不能用 <a href> 直链 */
  download: (id: number) => http.raw(`/api/attachments/${id}/download`)
}
```

> **下载必须走 `fetch` + `blob`**：`<a href>` 无法携带 Authorization 头，
> 直接指向接口会拿到 401。下载后 `URL.createObjectURL` 触发保存，用完 `revokeObjectURL`。
> 或者提示用户新窗口打开（新窗口同样带不上头）——**因此只能在 JS 里做**。

- [ ] **Step 2: 创建 `MarkdownView.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

const props = defineProps<{ content: string | null }>()

const html = computed(() => {
  if (!props.content) return ''
  const raw = marked.parse(props.content, { async: false }) as string
  // 必须过滤：正文由用户提交，未过滤等于给每个读者装了一个 XSS 入口
  return DOMPurify.sanitize(raw, { ADD_ATTR: ['target'] })
})

function highlightAll(event: Event) {
  const root = event.target as HTMLElement
  root.querySelectorAll('pre code').forEach((el) => hljs.highlightElement(el as HTMLElement))
}
</script>

<template>
  <div class="markdown-body" v-html="html" @vue:mounted="highlightAll" />
</template>
```

> 这是**唯一允许 `v-html` 的地方**，且内容必须先过 `DOMPurify`。
> 检索结果的 snippet 是纯文本，走 `KeywordHighlight` 组件，不用 `v-html`。

- [ ] **Step 3: 创建 `DetailView.vue`**

- 标题、元信息行（分类 / 作者 / 浏览数 / 更新时间）
- 标签列表（可点击跳检索）
- Markdown 正文
- 附件面板
- 作者本人或管理员可见的操作区：编辑 / 发布或归档或恢复 / 删除
- **按当前状态禁用不可用的操作**（如已发布条目不给"发布"按钮），而不是等点击后报错

- [ ] **Step 4: 创建 `AttachmentPanel.vue`**

- 列表显示文件名、大小、解析状态徽标
- `editable` 时显示上传按钮（`n-upload`，限制 20MB，接受 pdf/doc/docx/md/txt/图片）
- **解析中要轮询**：`PENDING`/`PARSING` 时每 2 秒调一次 `text(id)`，`DONE`/`FAILED` 后停止
- `FAILED` 显示 `parseError`（例如「不支持的文件类型：doc」）
- `DONE` 时可展开看 `textPreview`
- 下载按钮走 blob 方式

> **轮询要有上限**：最多轮询 60 次（约 2 分钟）后停止并提示"解析超时，请重新上传"，
> 否则一个卡住的任务会让页面永远转圈。

- [ ] **Step 5: 验证**

- 打开《MySQL 索引原理》，Markdown 标题与段落渲染正确
- 正文里若有代码块，语法高亮生效
- 给条目上传 `files/课程考试要求.pdf`，状态从 PENDING → DONE，能展开看抽取文本
- 上传 `.doc` 文件，状态变为 FAILED 且显示「不支持的文件类型：doc」
- 下载附件，文件与上传的一致

- [ ] **Step 6: 提交**

---

### Task F5: 知识编辑器（P4）

**Files:**
- Create: `src/components/TagInput.vue`
- Create: `src/views/EditorView.vue`

**Interfaces:**
- Consumes: `knowledgeApi.create/update/detail`、`categoryApi.tree`、`tagApi.list`
- Produces: `tagApi.list()` → `TagVO[]`

- [ ] **Step 1: 创建 `src/api/tag.ts`**

```ts
export const tagApi = {
  list: () => http.get<TagVO[]>('/api/tags')
}
```

> **编辑器按名称提交标签**：请求体用 `tagNames` 而不是 `tagIds`，
> 后端会自动创建不存在的标签。这样用户可以在编辑器里直接敲一个新标签名，
> 不必先去别的页面建好。标签没有独立的增删接口，这是有意的——
> 标签由条目使用者自然产生，单独管理反而增加负担。

- [ ] **Step 2: 创建 `EditorView.vue`**

- 左右分栏：左侧 Markdown 文本域，右侧实时预览（复用 `MarkdownView`）
- 标题、摘要单行输入
- 分类选择（`n-tree-select` 或 `n-cascader`，数据来自分类树）
- 标签输入（`TagInput` 组件：可从已有标签里选，也可直接输入新名称；提交时组装为 `tagNames`）
- 底部：保存草稿 / 保存并发布
- 新建走 `create`，编辑走 `update`
- **离开前有未保存修改时弹确认**（`onBeforeRouteLeave`）

- [ ] **Step 3: 验证**

- 新建条目 → 保存 → 在「我的知识库」能看到草稿
- 编辑已有条目，修改后保存，详情页内容已更新
- 未保存时点返回，弹出确认
- 标题为空时提交，看到「标题不能为空」（后端校验消息）

- [ ] **Step 4: 提交**

---

### Task F6: 检索结果页（P5）

**Files:**
- Create: `src/components/KeywordHighlight.vue`、`src/api/search.ts`
- Create: `src/views/SearchView.vue`

**Interfaces:**
- Consumes: `http`、`KnowledgeCard`（F3）
- Produces: `searchApi.search(query)` → `Page<SearchHit>`

- [ ] **Step 1: 创建 `src/api/search.ts`**

```ts
export interface SearchQuery {
  keyword?: string
  categoryId?: number | null
  tagId?: number | null
  contentType?: string
  status?: string
  from?: string
  to?: string
  sort?: 'relevance' | 'latest' | 'hottest'
  mine?: boolean
  page?: number
  size?: number
}

export const searchApi = {
  search: (query: SearchQuery) => http.get<Page<SearchHit>>('/api/search', { params: { ...query } })
}
```

- [ ] **Step 2: 创建 `KeywordHighlight.vue`（安全的高亮）**

```vue
<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ text: string | null; keyword: string | null }>()

/**
 * 把文本按关键词切成若干段，用模板渲染 <mark>。
 * **不使用 v-html**：后端刻意返回纯文本以防 XSS，前端再用 v-html 注入
 * 等于把这道防线拆掉。Vue 模板渲染会自动转义，无需额外处理。
 */
const parts = computed(() => {
  const text = props.text ?? ''
  const keyword = props.keyword?.trim()
  if (!keyword) return [{ text, hit: false }]

  const result: { text: string; hit: boolean }[] = []
  const lowerText = text.toLowerCase()
  const lowerKeyword = keyword.toLowerCase()
  let cursor = 0
  for (;;) {
    const index = lowerText.indexOf(lowerKeyword, cursor)
    if (index < 0) break
    if (index > cursor) result.push({ text: text.slice(cursor, index), hit: false })
    result.push({ text: text.slice(index, index + keyword.length), hit: true })
    cursor = index + keyword.length
  }
  if (cursor < text.length) result.push({ text: text.slice(cursor), hit: false })
  return result
})
</script>

<template>
  <span>
    <template v-for="(part, i) in parts" :key="i">
      <mark v-if="part.hit">{{ part.text }}</mark>
      <template v-else>{{ part.text }}</template>
    </template>
  </span>
</template>
```

- [ ] **Step 3: 创建 `SearchView.vue`**

- 顶部搜索框（同步 URL 的 `keyword`，**刷新后结果还在**）
- 过滤器：分类、标签、内容类型、状态、时间范围、排序（相关度 / 最新 / 最热）
- 结果项展示：标题（高亮）、snippet（高亮）、分类、标签、相关度
- 空结果给出建议：「换个关键词试试」+ 展示当前生效的过滤器，便于用户发现是过滤器太严

> **`score` 不要展示为数值**：API.md 说明它的量纲随关键词而变（中文是 TF-IDF，英文是固定权重），
> 前端展示"相关度 8.07"会让用户困惑。**只按后端返回的顺序展示**。

- [ ] **Step 4: 验证**

- 搜「索引」，结果中关键词被高亮，且**页面源码里没有 `v-html` 注入的痕迹**
- 搜 `<script>alert(1)</script>`，页面上原样显示这串字符而**不弹窗**
- 切换排序，结果顺序变化
- 加过滤器后结果减少，URL 带上参数且刷新后保持

- [ ] **Step 5: 提交**

---

### Task F7: 我的知识库（P6）与分类管理（P7）

**Files:**
- Create: `src/views/MineView.vue`、`src/views/CategoryAdminView.vue`

**Interfaces:**
- Consumes: `knowledgeApi`、`categoryApi`、`QueryState`

- [ ] **Step 1: 创建 `MineView.vue`**

- 三个标签页：草稿 / 已发布 / 已归档（对应 `status` 参数 + `mine: true`）
- 每项提供状态流转操作，**按当前状态只显示合法操作**：
  - 草稿 → 发布、归档、编辑、删除
  - 已发布 → 归档、编辑、删除
  - 已归档 → 恢复、删除
- 操作成功后刷新列表并提示

> 状态机在后端是强约束（非法跃迁返回 2000）。前端**不应把非法操作呈现给用户**，
> 让用户点了才报错是糟糕的交互。

- [ ] **Step 2: 创建 `CategoryAdminView.vue`**

- 左侧分类树，右侧选中节点的编辑表单（名称、排序、描述）
- 新建根分类 / 新建子分类
- 删除按钮，**失败时完整展示后端消息**（如「该分类下有 3 个知识条目，无法删除」）——
> 这条消息直接告诉用户下一步该做什么，是好的反馈设计，不要在界面上截断或替换成"删除失败"
- **父分类在编辑时只读**：后端会忽略 `parentId` 变更，界面上应提示「不支持移动分类」

- [ ] **Step 3: 验证**

- 草稿页能看到 F5 创建的条目，点发布后出现在已发布页
- 删除一个有子分类的分类，看到后端的完整原因
- 新建子分类后，树立即刷新

- [ ] **Step 4: 提交**

---

### Task F8: 智能问答页（RAG）

**Files:**
- Create: `src/api/qa.ts`、`src/views/QaView.vue`

**Interfaces:**
- Consumes: `http`、`BASE_URL`
- Produces:
  - `qaApi.status()` → `QaStatusVO`
  - `qaApi.reindex()` → `QaStatusVO`
  - `qaApi.history(chatId)` → `QaMessage[]`
  - `qaApi.ask({ question, chatId, onSources, onDelta, onError, onDone })`

- [ ] **Step 1: 创建 `src/api/qa.ts`（SSE 消费）**

```ts
/**
 * 流式提问。
 *
 * **必须用 fetch 而不是 EventSource**：EventSource 不能自定义请求头，
 * 无法携带 Authorization；而且它只支持 GET，而本接口是 POST（问题可能很长，
 * 放 URL 里会超长）。
 */
export async function ask(params: {
  question: string
  chatId?: string | null
  onSources: (sources: SourceRef[]) => void
  onDelta: (text: string) => void
  onDone: (chatId: string) => void
  onError: (message: string) => void
  signal?: AbortSignal
}) {
  const response = await http.raw('/api/qa/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ question: params.question, chatId: params.chatId }),
    signal: params.signal
  })
  if (!response.ok || !response.body) {
    params.onError('问答服务暂时不可用')
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // SSE 以空行分隔事件
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() ?? ''
    for (const chunk of chunks) {
      let event = ''
      const dataLines: string[] = []
      for (const line of chunk.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      }
      if (!event || !dataLines.length) continue
      const raw = dataLines.join('\n')
      try {
        const parsed = JSON.parse(raw)
        if (event === 'sources') params.onSources(parsed)
        else if (event === 'delta') params.onDelta(typeof parsed === 'string' ? parsed : String(parsed))
        else if (event === 'done') params.onDone(parsed.chatId)
        else if (event === 'error') params.onError(parsed.message)
      } catch {
        // 非 JSON 的 data 直接当文本片段
        if (event === 'delta') params.onDelta(raw)
      }
    }
  }
}
```

> **注意**：`decoder.decode(value, { stream: true })` 的 `stream: true` 不能省。
> 多字节 UTF-8 字符（中文）可能被切在两次 `read()` 之间，不加这个标志会解出乱码——
> 而乱码在中文界面里几乎必然出现。

- [ ] **Step 2: 创建 `QaView.vue`**

- 对话式布局：用户气泡 / 助手气泡
- 助手回答流式追加（`onDelta` 累加到当前消息）
- **引用卡片**：`onSources` 拿到后立即渲染，先于答案出现——用户先看到"找到了什么"
- 引用卡片可点击跳 `/detail/{itemId}`
- 答案里的 `[1]` `[2]` 渲染成上标角标，点击滚动/跳转到对应引用
- 停止生成按钮（用 `AbortController` 中断）
- 未配置问答（`status.enabled === false`）时：**显示说明而不是空白**
  ——「问答功能未配置，请在 application-local.yaml 中填写密钥后重启后端」
- 管理员可见"重建索引"按钮，点击后给出等待反馈（耗时十余秒）

> **重建索引必须给进度感**：十几秒的等待如果只有一个转圈，用户会以为卡死。
> 至少要有「正在重建索引，预计十余秒」的文案。

- [ ] **Step 3: 验证**

- 提问「MySQL 索引用了什么数据结构？」，先出现引用卡片，再逐字出现答案
- 答案里的 `[1]` 可点击，跳到对应知识条目
- 提问知识库外的问题，看到「知识库中没有相关内容」
- 生成中点"停止"，流中断且界面不卡死
- 停掉后端密钥配置重启后，页面显示"未配置"说明而非报错堆栈

- [ ] **Step 4: 提交**

---

### Task F9: 状态打磨与响应式

**Files:**
- Modify: 全部视图

- [ ] **Step 1: 逐页检查四种状态**

对每个页面确认：**加载中**（骨架屏或 spin，不要白屏）、**空**（给出下一步动作）、
**错误**（可读文案 + 重试按钮）、**超长内容**（长标题截断、长摘要限行）。

- [ ] **Step 2: 响应式**

- 宽度 < 1024px 时侧栏收起为抽屉
- 列表在窄屏从卡片变为更紧凑的布局
- 编辑器分栏在窄屏变为上下布局

- [ ] **Step 3: 一致性检查**

- 状态徽标颜色统一（草稿=灰、已发布=绿、已归档=橙）
- 危险操作（删除）二次确认，确认文案说明后果
- 所有异步操作有成功/失败提示

- [ ] **Step 4: 提交**

---

### Task F10: 构建与端到端联调

- [ ] **Step 1: 类型检查与构建**

```bash
cd kms-frontend
npm run type-check
npm run build
```

Expected: 均无错误，`dist/` 产出

- [ ] **Step 2: 对着后端跑一遍完整演示脚本**

按答辩的演示顺序走一遍（这一步的结果直接作为答辩演练）：

1. 打开首页 → 分类树展开 → 点分类筛选
2. 搜索「索引」→ 高亮 → 切排序
3. 打开一条详情 → 看 Markdown 渲染
4. 登录 → 新建一篇（含 Markdown 与标签）→ 发布
5. 上传 `files/课程考试要求.pdf` → 观察解析状态流转
6. 去问答页提问 → 引用与流式答案
7. 我的知识库切三个状态页 → 状态流转
8. 分类管理 → 演示删除保护
9. 用 zhangsan 登录，确认看不到 admin 的草稿

- [ ] **Step 3: 记录演示脚本**

把上述步骤写成 `docs/report/demo-script.md`，答辩时照着走。

- [ ] **Step 4: 提交**

---

## 完成标准

- [ ] `npm run build` 成功产出
- [ ] 全部 7 个页面 + 问答页可用，覆盖后端全部功能
- [ ] 未登录 / 普通用户 / 管理员三种身份的界面差异正确
- [ ] 检索高亮生效且**未使用 `v-html` 渲染 snippet**
- [ ] Markdown 正文经 DOMPurify 过滤
- [ ] 附件上传后解析状态能流转到 DONE 或 FAILED 并给出原因
- [ ] 问答流式输出正常，引用可跳转
- [ ] 每个列表页都有加载 / 空 / 错误三态
- [ ] `git status` 中无 `node_modules`、无 `dist`

## 已知缺口（记入报告第七部分）

1. **附件内容不进 RAG 索引**。v0.1.0 只索引条目正文，上传的 PDF 内容参与不了问答。
   扩展点是 `ItemContentLoader`，加一处即可。
2. **问答必然依赖网络**。生成阶段要调用大模型，断网无法演示。答辩前需预热。
3. **`score` 不做数值展示**。它的量纲随关键词变化，展示具体数值会误导用户。
