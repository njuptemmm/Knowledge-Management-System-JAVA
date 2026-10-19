<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { InputInst } from 'naive-ui'
import { qaApi } from '@/api/qa'
import { describeError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { QaMessage, QaSession, QaStatusVO } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import AppLayout from '@/components/AppLayout.vue'
import QaAnswerView from '@/components/QaAnswerView.vue'

const router = useRouter()
const message = useMessage()
const auth = useAuthStore()

interface ChatItem extends QaMessage {
  /** 正在流式接收中 */
  streaming?: boolean
}

const question = ref('')
const chatId = ref<string | null>(null)
const messages = ref<ChatItem[]>([])
const sessions = ref<QaSession[]>([])
const asking = ref(false)
const status = ref<QaStatusVO | null>(null)
const reindexing = ref(false)
const scroller = ref<HTMLElement | null>(null)
const inputRef = ref<InputInst | null>(null)

/**
 * 侧栏在 ≤1024px 被 AppLayout 的媒体查询整块隐藏。
 * 会话入口不能跟着消失，否则窄屏下既不能新建也不能切换会话。
 */
const narrow = ref(false)
let mediaQuery: MediaQueryList | null = null

let controller: AbortController | null = null

const showHeaderNewChat = computed(
  () => (narrow.value && auth.isLoggedIn) || (!auth.isLoggedIn && messages.value.length > 0)
)

/** 同名会话很常见（都取首句前 24 字），下拉里必须带时间才分得清。 */
const sessionOptions = computed(() =>
  sessions.value.map((s) => ({
    key: s.chatId,
    label: `${s.title} · ${shortTime(s.updatedAt)}`
  }))
)

async function loadStatus() {
  try {
    status.value = await qaApi.status()
  } catch (e) {
    message.error(describeError(e))
  }
}

async function scrollToBottom() {
  await nextTick()
  scroller.value?.scrollTo({ top: scroller.value.scrollHeight, behavior: 'smooth' })
}

async function submit() {
  const text = question.value.trim()
  if (!text) {
    message.warning('请输入问题')
    return
  }
  if (!status.value?.enabled) {
    message.warning('问答功能未配置')
    return
  }

  messages.value.push({ role: 'user', content: text, sources: [], at: new Date().toISOString() })
  messages.value.push({
    role: 'assistant',
    content: '',
    sources: [],
    at: new Date().toISOString(),
    streaming: true
  })

  // **必须取回响应式代理再改，不能拿着 push 进去的原始对象改。**
  // 数组代理存的是原始对象，模板读取时才会包成 proxy 并建立依赖；
  // 直接改原始对象绕过了 proxy 的 set，trigger 不会触发，
  // 界面就一直不更新，直到别的状态变化顺带刷新一次——
  // 表现就是「后端在流式输出，前端一次性蹦出全文」。实测踩过。
  const assistant = messages.value[messages.value.length - 1]!

  question.value = ''
  asking.value = true
  controller = new AbortController()
  await scrollToBottom()

  try {
    await qaApi.ask({
      question: text,
      chatId: chatId.value,
      signal: controller.signal,
      // 引用先于答案到达，用户立刻看到「系统找到了什么」
      onSources: (sources) => {
        assistant.sources = sources
      },
      onDelta: (delta) => {
        assistant.content += delta
        void scrollToBottom()
      },
      onDone: (id) => {
        if (id) {
          chatId.value = id
          // 记住会话 id，刷新页面后还能接上同一段对话
          localStorage.setItem('kms.qa.chatId', id)
          // 后端在第一句提问时建档并生成标题，这里刷新列表才能看到
          void loadSessions()
        }
        assistant.streaming = false
      },
      onError: (msg) => {
        assistant.streaming = false
        if (msg) message.error(msg)
      }
    })
  } catch (e) {
    assistant.streaming = false
    message.error(describeError(e))
  } finally {
    assistant.streaming = false
    asking.value = false
    controller = null
  }
}

function stop() {
  controller?.abort()
  controller = null
  asking.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.streaming) last.streaming = false
}

/** 会话列表只在登录后才有；失败不该打断提问，静默处理。 */
async function loadSessions() {
  if (!auth.isLoggedIn) {
    sessions.value = []
    return
  }
  try {
    sessions.value = await qaApi.sessions()
  } catch {
    // 列表只是辅助导航，拉不到就留空
  }
}

/**
 * 开一段新对话。
 *
 * <p>刻意不在点击时就调后端建会话：用户点了「新对话」却没提问的话，
 * 列表里会堆一串空会话。真正的会话由第一次提问时后端自动创建，
 * 标题也取自那句问题。
 */
function newChat(notify = true) {
  if (asking.value) stop()
  messages.value = []
  chatId.value = null
  localStorage.removeItem('kms.qa.chatId')
  // 已经在空白页时，点了按钮什么都看不见，会以为功能没实现。
  // 给一条明确反馈，并把焦点放回输入框——下一步就是提问。
  if (notify) {
    message.success(auth.isLoggedIn ? '已开始新会话，提问后自动保存' : '已开始新会话')
  }
  void nextTick(() => inputRef.value?.focus())
}

function openSessionById(chatId: string) {
  const target = sessions.value.find((s) => s.chatId === chatId)
  if (target) void openSession(target)
}

async function openSession(session: QaSession) {
  if (session.chatId === chatId.value) return
  if (asking.value) stop()
  chatId.value = session.chatId
  messages.value = []
  try {
    const history = await qaApi.history(session.chatId)
    messages.value = history.map((m) => ({ ...m }))
    localStorage.setItem('kms.qa.chatId', session.chatId)
    await scrollToBottom()
  } catch (e) {
    message.error(describeError(e))
  }
}

async function removeSession(session: QaSession) {
  try {
    await qaApi.deleteSession(session.chatId)
    // 删掉的是当前会话：回到空白页，但不要弹「已开始新会话」——用户删的是会话，不是想开新的
    if (chatId.value === session.chatId) newChat(false)
    await loadSessions()
  } catch (e) {
    message.error(describeError(e))
  }
}

/** 侧栏空间小，时间只留「月-日 时:分」。 */
function shortTime(value: string) {
  return formatDateTime(value).replace(/^\d{4}-/, '')
}

async function reindex() {
  reindexing.value = true
  try {
    status.value = await qaApi.reindex()
    message.success('索引重建完成')
  } catch (e) {
    message.error(describeError(e))
  } finally {
    reindexing.value = false
  }
}

function goSource(itemId: number) {
  router.push(`/detail/${itemId}`)
}

function onViewportChange(event: MediaQueryListEvent) {
  narrow.value = event.matches
}

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 1024px)')
  narrow.value = mediaQuery.matches
  mediaQuery.addEventListener('change', onViewportChange)
})

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener('change', onViewportChange)
})

onMounted(async () => {
  await Promise.all([loadStatus(), loadSessions()])
  const saved = localStorage.getItem('kms.qa.chatId')
  if (saved) {
    try {
      const history = await qaApi.history(saved)
      if (history.length) {
        chatId.value = saved
        messages.value = history.map((m) => ({ ...m }))
        await scrollToBottom()
      }
    } catch {
      // 会话属于别的账号或已过期：清掉本地记录，不打扰用户
      localStorage.removeItem('kms.qa.chatId')
    }
  }
})
</script>

<template>
  <!-- 会话列表需要归属，匿名用户不显示侧栏：他们仍可提问，只是不保存列表 -->
  <AppLayout :sidebar="auth.isLoggedIn" sidebar-title="历史会话">
    <template #sidebar>
      <n-button block size="small" dashed class="new-chat" @click="newChat()">+ 新建会话</n-button>
      <div v-if="sessions.length" class="session-list">
        <div
          v-for="s in sessions"
          :key="s.chatId"
          class="session-item"
          :class="{ active: s.chatId === chatId }"
          @click="openSession(s)"
        >
          <div class="session-title">{{ s.title }}</div>
          <div class="session-meta">{{ s.messageCount }} 条 · {{ shortTime(s.updatedAt) }}</div>
          <n-button class="session-del" text size="tiny" @click.stop="removeSession(s)">
            删除
          </n-button>
        </div>
      </div>
      <div v-else class="session-empty">还没有历史会话</div>
    </template>

    <div class="qa-head page-section">
      <h2 class="heading">智能问答</h2>
      <div class="head-ops">
        <n-button v-if="auth.isAdmin" size="small" :loading="reindexing" @click="reindex">
          重建索引
        </n-button>
        <!-- 窄屏侧栏被隐藏，这里必须补一个会话切换入口 -->
        <n-dropdown
          v-if="narrow && auth.isLoggedIn && sessions.length"
          :options="sessionOptions"
          @select="openSessionById"
        >
          <n-button size="small">历史会话（{{ sessions.length }}）</n-button>
        </n-dropdown>
        <n-button v-if="showHeaderNewChat" size="small" type="primary" @click="newChat()">
          + 新建会话
        </n-button>
      </div>
    </div>

    <!-- 未配置时给出可执行的说明，而不是一个空白页 -->
    <n-alert v-if="status && !status.enabled" type="warning" class="page-section" :show-icon="true">
      <template #header>问答功能未配置</template>
      后端未配置向量服务与生成模型。请在
      <code>application-local.yaml</code> 中填写 <code>app.rag.embedding.*</code> 与
      Kimi 密钥，并把 <code>app.rag.enabled</code> 改为 <code>true</code> 后重启后端。
    </n-alert>

    <n-alert v-else-if="status" type="default" class="page-section status-bar" :show-icon="false">
      已索引 {{ status.indexedItems }} 个条目 / {{ status.indexedChunks }} 个文本块。
      问答基于知识库中已发布的内容。
    </n-alert>

    <div ref="scroller" class="chat">
      <div v-if="!messages.length" class="placeholder">
        <n-empty description="还没有对话">
          <template #extra>
            <div class="samples">
              <n-button
                v-for="s in ['MySQL 索引用了什么数据结构？', 'ArrayList 和 LinkedList 有什么不同？', '缓存穿透和缓存雪崩怎么解决？']"
                :key="s"
                size="small"
                quaternary
                @click="((question = s), submit())"
              >
                {{ s }}
              </n-button>
            </div>
          </template>
        </n-empty>
      </div>

      <div v-for="(msg, i) in messages" :key="i" class="msg" :class="msg.role">
        <div class="avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
        <div class="bubble-wrap">
          <!-- 引用先渲染，用户在答案出来之前就知道系统找到了什么 -->
          <div v-if="msg.role === 'assistant' && msg.sources.length" class="sources">
            <div class="sources-title">引用来源</div>
            <div class="source-list">
              <div
                v-for="(s, idx) in msg.sources"
                :key="`${s.itemId}-${idx}`"
                class="source"
                @click="goSource(s.itemId)"
              >
                <span class="ref-index">[{{ idx + 1 }}]</span>
                <span class="ref-title">{{ s.title }}</span>
                <span v-if="s.headingPath" class="ref-path">{{ s.headingPath }}</span>
              </div>
            </div>
          </div>

          <div class="bubble">
            <QaAnswerView
              v-if="msg.content"
              :content="msg.content"
              :source-count="msg.sources.length"
              @cite="(n) => msg.sources[n - 1] && goSource(msg.sources[n - 1]!.itemId)"
            />
            <span v-if="msg.streaming" class="caret">▍</span>
            <!-- 分两段提示：模型思考期间有 5 秒左右不出字，
                 只说「正在生成」用户会以为卡住了，说清楚它找到了什么才有底 -->
            <span v-if="!msg.content && msg.streaming" class="thinking">
              {{
                msg.sources.length
                  ? `已找到 ${msg.sources.length} 条相关资料，正在生成答案…`
                  : '正在检索知识库…'
              }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="composer">
      <n-input
        ref="inputRef"
        v-model:value="question"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 4 }"
        placeholder="输入你的问题，回车发送（Shift + 回车换行）"
        :disabled="asking || !status?.enabled"
        @keydown.enter.exact.prevent="submit"
      />
      <n-button
        v-if="asking"
        type="warning"
        class="send"
        @click="stop"
      >
        停止
      </n-button>
      <n-button
        v-else
        type="primary"
        class="send"
        :disabled="!status?.enabled"
        @click="submit"
      >
        发送
      </n-button>
    </div>
  </AppLayout>
</template>

<style scoped>
.new-chat {
  margin-bottom: 8px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  position: relative;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  border-left: 3px solid transparent;
}

.session-item:hover {
  background: #f2f5fa;
}

/* 当前会话用左侧色条标记，比整块变色更安静，也不影响文字对比度 */
.session-item.active {
  background: #eef4ff;
  border-left-color: var(--kms-primary);
}

.session-title {
  font-size: 13px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 34px;
}

.session-meta {
  font-size: 11px;
  color: var(--kms-text-secondary);
  margin-top: 2px;
}

/* 删除按钮默认隐身，悬停才出现：列表项的主体动作是「打开」，不是「删除」 */
.session-del {
  position: absolute;
  top: 6px;
  right: 4px;
  opacity: 0;
  font-size: 11px;
}

.session-item:hover .session-del {
  opacity: 1;
}

.session-empty {
  font-size: 12px;
  color: var(--kms-text-secondary);
  padding: 8px 10px;
}

.qa-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.heading {
  margin: 0;
  font-size: 17px;
}

.head-ops {
  display: flex;
  gap: 8px;
}

.status-bar {
  font-size: 12px;
}

.chat {
  min-height: 300px;
  max-height: calc(100vh - 320px);
  overflow-y: auto;
  padding: 8px 0;
}

.placeholder {
  padding: 40px 0;
}

.samples {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.msg.user {
  flex-direction: row-reverse;
}

.avatar {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  background: #e8eef7;
  color: #2a4d7a;
}

.msg.user .avatar {
  background: var(--kms-primary);
  color: #fff;
}

.bubble-wrap {
  max-width: min(760px, 80%);
}

.sources {
  background: #f6f9ff;
  border: 1px solid #dbe7fb;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 6px;
}

.sources-title {
  font-size: 12px;
  color: var(--kms-text-secondary);
  margin-bottom: 6px;
}

.source-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.source {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
}

.source:hover {
  background: #eaf1ff;
}

.ref-index {
  color: var(--kms-primary);
  font-weight: 600;
  flex-shrink: 0;
}

.ref-title {
  font-weight: 500;
}

.ref-path {
  font-size: 12px;
  color: var(--kms-text-secondary);
}

.bubble {
  background: #fff;
  border: 1px solid var(--kms-border);
  border-radius: 8px;
  padding: 10px 14px;
  word-break: break-word;
  line-height: 1.7;
}

.msg.user .bubble {
  background: var(--kms-primary);
  border-color: var(--kms-primary);
  color: #fff;
}

.cite {
  color: var(--kms-primary);
  cursor: pointer;
  font-size: 0.9em;
}

.cite:hover {
  text-decoration: underline;
}

.caret {
  animation: blink 1s step-start infinite;
  color: var(--kms-primary);
}

.thinking {
  color: var(--kms-text-secondary);
  font-size: 13px;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  position: sticky;
  bottom: 0;
  background: var(--kms-bg);
  padding: 12px 0 4px;
}

.send {
  flex-shrink: 0;
}
</style>
