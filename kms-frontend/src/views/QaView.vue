<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { qaApi } from '@/api/qa'
import { describeError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { QaMessage, QaStatusVO } from '@/types/api'
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
const asking = ref(false)
const status = ref<QaStatusVO | null>(null)
const reindexing = ref(false)
const scroller = ref<HTMLElement | null>(null)

let controller: AbortController | null = null

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

function clearChat() {
  messages.value = []
  chatId.value = null
  localStorage.removeItem('kms.qa.chatId')
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

onMounted(async () => {
  await loadStatus()
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
      localStorage.removeItem('kms.qa.chatId')
    }
  }
})
</script>

<template>
  <AppLayout>
    <div class="qa-head page-section">
      <h2 class="heading">智能问答</h2>
      <div class="head-ops">
        <n-button v-if="auth.isAdmin" size="small" :loading="reindexing" @click="reindex">
          重建索引
        </n-button>
        <n-button v-if="messages.length" size="small" quaternary @click="clearChat">
          清空对话
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
