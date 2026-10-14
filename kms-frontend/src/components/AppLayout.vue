<script setup lang="ts">
import { computed, ref, useSlots, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useMessage } from 'naive-ui'
import { describeError } from '@/api/client'

const props = withDefaults(
  defineProps<{
    /** 是否显示左侧侧栏。默认由是否传入 #sidebar 插槽自动决定 */
    sidebar?: boolean
    /** 侧栏标题 */
    sidebarTitle?: string
  }>(),
  { sidebar: undefined, sidebarTitle: '分类' }
)

const slots = useSlots()

/**
 * 有插槽才渲染侧栏。
 * 不用「默认开启、不用的页面自己传 false」：那样新增页面时很容易忘记，
 * 结果就是内容左侧留出一条 220px 的空白栏（实际出现过）。
 */
const showSidebar = computed(() =>
  props.sidebar === undefined ? !!slots.sidebar : props.sidebar
)

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const message = useMessage()

const keyword = ref((route.query.keyword as string) ?? '')
watch(
  () => route.query.keyword,
  (v) => {
    keyword.value = (v as string) ?? ''
  }
)

function onSearch() {
  const trimmed = keyword.value.trim()
  router.push({ path: '/search', query: trimmed ? { keyword: trimmed } : {} })
}

const menuOptions = computed(() => {
  const items = [
    { key: 'qa', label: '智能问答' },
    { key: 'mine', label: '我的知识库' },
    { key: 'categories', label: '分类管理' },
    { key: 'logout', label: '退出登录' }
  ]
  return items
})

async function onMenuSelect(key: string) {
  if (key === 'logout') {
    try {
      await auth.logout()
      message.success('已退出登录')
    } catch (e) {
      message.warning(describeError(e))
    }
    router.push('/')
    return
  }
  router.push('/' + key)
}

function goLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}
</script>

<template>
  <div class="layout">
    <header class="topbar">
      <div class="brand" @click="router.push('/')">知识管理系统</div>

      <div class="search">
        <n-input
          v-model:value="keyword"
          placeholder="搜索知识库，回车确认"
          clearable
          @keyup.enter="onSearch"
        />
      </div>

      <div class="actions">
        <n-button quaternary size="small" @click="router.push('/qa')">智能问答</n-button>
        <template v-if="auth.isLoggedIn">
          <n-dropdown :options="menuOptions" @select="onMenuSelect">
            <n-button quaternary size="small">{{ auth.displayName }}</n-button>
          </n-dropdown>
        </template>
        <n-button v-else size="small" type="primary" @click="goLogin">登录</n-button>
      </div>
    </header>

    <div class="body">
      <aside v-if="showSidebar" class="sidebar">
        <div class="sidebar-title">{{ props.sidebarTitle }}</div>
        <slot name="sidebar" />
      </aside>
      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 56px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--kms-border);
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.search {
  flex: 1;
  max-width: 460px;
}

.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.body {
  display: flex;
  flex: 1;
  align-items: flex-start;
}

.sidebar {
  width: 220px;
  flex: 0 0 220px;
  background: #fff;
  border-right: 1px solid var(--kms-border);
  padding: 12px;
  position: sticky;
  top: 56px;
  max-height: calc(100vh - 56px);
  overflow-y: auto;
}

.sidebar-title {
  font-size: 12px;
  color: var(--kms-text-secondary);
  padding: 4px 8px 8px;
}

.content {
  flex: 1;
  min-width: 0;
  padding: 16px 20px 40px;
}

/* 窄屏收起侧栏：分类树是辅助导航，内容优先 */
@media (max-width: 1024px) {
  .sidebar {
    display: none;
  }
  .content {
    padding: 12px;
  }
}
</style>
