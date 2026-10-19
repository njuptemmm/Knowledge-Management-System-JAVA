<script setup lang="ts">
import { computed, ref, useSlots, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { isDark, toggleTheme } from '@/stores/theme'
import { useMessage } from 'naive-ui'
import { describeError } from '@/api/client'

const props = withDefaults(
  defineProps<{
    /** 是否显示左侧侧栏。默认由是否传入 #sidebar 插槽自动决定 */
    sidebar?: boolean
    /** 侧栏标题 */
    sidebarTitle?: string
    /**
     * 窄屏（≤1024px）是否保留侧栏。
     *
     * <p>默认隐藏，给内容让位。但分类管理页的侧栏就是主控件——
     * 隐藏后既看不到分类树，也点不到「新建」，整页变成死路。
     */
    keepSidebar?: boolean
  }>(),
  { sidebar: undefined, sidebarTitle: '分类', keepSidebar: false }
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

/**
 * 搜索页自己带一个搜索框（和筛选条件排在一起），顶栏若同时保留输入框，
 * 页面上就有两个功能重复的框，用户不知道用哪个。
 * 只保留页面内的那个：搜索页隐藏顶栏搜索，其它页面照常显示。
 */
const showTopSearch = computed(() => route.name !== 'search')

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

      <div v-if="showTopSearch" class="search">
        <n-input
          v-model:value="keyword"
          placeholder="搜索知识库，回车确认"
          clearable
          @keyup.enter="onSearch"
        />
      </div>

      <div class="actions">
        <!-- 主题切换放在最左，是全局操作而非账号操作 -->
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button
              quaternary
              circle
              class="theme-btn"
              :aria-label="isDark ? '切换到浅色模式' : '切换到夜间模式'"
              @click="toggleTheme"
            >
              <span class="theme-icon">{{ isDark ? '☀️' : '🌙' }}</span>
            </n-button>
          </template>
          {{ isDark ? '切换到浅色模式' : '切换到夜间模式' }}
        </n-tooltip>
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
      <aside v-if="showSidebar" class="sidebar" :class="{ 'sidebar-keep': keepSidebar }">
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
  background: var(--kms-surface);
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

.theme-btn {
  font-size: 15px;
}

/* emoji 在部分系统里偏小，手动拉大到与相邻按钮的视觉重量相当 */
.theme-icon {
  display: inline-block;
  font-size: 15px;
  line-height: 1;
}

.body {
  display: flex;
  flex: 1;
  align-items: flex-start;
}

.sidebar {
  width: 220px;
  flex: 0 0 220px;
  background: var(--kms-surface);
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

/* 窄屏收起侧栏：分类树是辅助导航，内容优先。
   分类管理页例外——那里侧栏是主控件，用 sidebar-keep 保住。 */
@media (max-width: 1024px) {
  .sidebar:not(.sidebar-keep) {
    display: none;
  }
  .sidebar-keep {
    width: 180px;
    flex: 0 0 180px;
  }
  .content {
    padding: 12px;
  }
}
</style>
