<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import { attachmentApi } from '@/api/attachment'
import { describeError } from '@/api/client'
import type { AttachmentVO } from '@/types/api'
import { PARSE_LABEL, PARSE_TYPE, formatSize } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    itemId: number
    /** 是否允许上传与删除（仅条目作者或管理员） */
    editable?: boolean
  }>(),
  { editable: false }
)

const message = useMessage()
const dialog = useDialog()

const list = ref<AttachmentVO[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const uploading = ref(false)

/** 轮询计数。上限约 2 分钟，避免卡住的任务让页面永远转圈 */
const pollTicks = new Map<number, number>()
const MAX_POLL_TICKS = 60
let timer: number | null = null

const ALLOWED_HINT = '支持 PDF、Word（docx）、Markdown、文本、图片、视频，单个不超过 20MB'

async function load() {
  loading.value = true
  error.value = null
  try {
    list.value = await attachmentApi.listByItem(props.itemId)
    ensurePolling()
  } catch (e) {
    error.value = describeError(e)
  } finally {
    loading.value = false
  }
}

/** 有处于解析中的附件时才启动轮询，全部结束就停掉 */
function ensurePolling() {
  const busy = list.value.some((a) => a.parseStatus === 'PENDING' || a.parseStatus === 'PARSING')
  if (busy && timer === null) {
    timer = window.setInterval(poll, 2000)
  } else if (!busy && timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

async function poll() {
  const targets = list.value.filter(
    (a) => a.parseStatus === 'PENDING' || a.parseStatus === 'PARSING'
  )
  for (const target of targets) {
    const ticks = (pollTicks.get(target.id) ?? 0) + 1
    pollTicks.set(target.id, ticks)
    if (ticks > MAX_POLL_TICKS) {
      target.parseStatus = 'FAILED'
      target.parseError = '解析超时，请重新上传'
      continue
    }
    try {
      const latest = await attachmentApi.text(target.id)
      Object.assign(target, latest)
    } catch {
      // 单次轮询失败不打断整体，下一轮继续
    }
  }
  ensurePolling()
}

async function onUpload(options: { file: { file?: File } }) {
  const file = options.file.file
  if (!file) return
  uploading.value = true
  try {
    const created = await attachmentApi.upload(props.itemId, file)
    list.value = [created, ...list.value]
    pollTicks.set(created.id, 0)
    ensurePolling()
    message.success('上传成功，正在解析')
  } catch (e) {
    message.error(describeError(e))
  } finally {
    uploading.value = false
  }
}

function confirmRemove(attachment: AttachmentVO) {
  dialog.warning({
    title: '删除附件',
    content: `确定删除「${attachment.originalName}」吗？文件与解析结果都会被移除，且不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await attachmentApi.remove(attachment.id)
        list.value = list.value.filter((a) => a.id !== attachment.id)
        message.success('已删除')
      } catch (e) {
        message.error(describeError(e))
      }
    }
  })
}

async function download(attachment: AttachmentVO) {
  try {
    await attachmentApi.download(attachment.id, attachment.originalName)
  } catch (e) {
    message.error(describeError(e))
  }
}

onMounted(load)
onBeforeUnmount(() => {
  if (timer !== null) window.clearInterval(timer)
})
watch(() => props.itemId, load)
</script>

<template>
  <div class="panel">
    <div class="head">
      <span class="title">附件</span>
      <n-upload
        v-if="editable"
        :show-file-list="false"
        :custom-request="onUpload as never"
        :disabled="uploading"
        accept=".pdf,.doc,.docx,.md,.markdown,.txt,.png,.jpg,.jpeg,.gif,.webp,.mp4"
      >
        <n-button size="small" :loading="uploading">上传附件</n-button>
      </n-upload>
    </div>

    <p v-if="editable" class="hint">{{ ALLOWED_HINT }}</p>

    <n-spin :show="loading">
      <n-empty v-if="error" :description="error" size="small">
        <template #extra>
          <n-button size="tiny" @click="load">重试</n-button>
        </template>
      </n-empty>
      <n-empty v-else-if="!list.length" description="暂无附件" size="small" />
      <div v-else class="list">
        <div v-for="a in list" :key="a.id" class="row">
          <div class="info">
            <div class="name" :title="a.originalName">{{ a.originalName }}</div>
            <div class="sub">
              {{ formatSize(a.sizeBytes) }}
              <n-tag :type="PARSE_TYPE[a.parseStatus]" size="tiny" :bordered="false">
                {{ PARSE_LABEL[a.parseStatus] }}
              </n-tag>
            </div>
            <!-- 解析失败时给出后端的原始原因，它本身就说明了该怎么办 -->
            <div v-if="a.parseStatus === 'FAILED' && a.parseError" class="err">
              {{ a.parseError }}
            </div>
            <details v-if="a.parseStatus === 'DONE' && a.textPreview" class="preview">
              <summary>查看抽取的文本</summary>
              <pre>{{ a.textPreview }}</pre>
            </details>
          </div>
          <div class="ops">
            <n-button size="tiny" quaternary @click="download(a)">下载</n-button>
            <n-button v-if="editable" size="tiny" quaternary @click="confirmRemove(a)">
              删除
            </n-button>
          </div>
        </div>
      </div>
    </n-spin>
  </div>
</template>

<style scoped>
.panel {
  background: var(--kms-surface);
  border: 1px solid var(--kms-border);
  border-radius: 8px;
  padding: 14px 16px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.title {
  font-weight: 600;
  font-size: 14px;
}

.hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--kms-text-secondary);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 0;
  border-top: 1px solid var(--kms-border);
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--kms-text-secondary);
  margin-top: 2px;
}

.err {
  margin-top: 4px;
  font-size: 12px;
  color: var(--kms-danger);
}

.preview {
  margin-top: 6px;
  font-size: 12px;
}

.preview summary {
  cursor: pointer;
  color: var(--kms-primary);
}

.preview pre {
  margin: 6px 0 0;
  padding: 8px;
  background: var(--kms-surface-subtle);
  border-radius: 4px;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
}

.ops {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
</style>
