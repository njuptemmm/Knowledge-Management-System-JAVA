<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDialog, useMessage } from 'naive-ui'
import { knowledgeApi } from '@/api/knowledge'
import { describeError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { KnowledgeDetailVO } from '@/types/api'
import { STATUS_LABEL, STATUS_TYPE, formatDateTime } from '@/utils/format'
import AppLayout from '@/components/AppLayout.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import QueryState from '@/components/QueryState.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const message = useMessage()
const dialog = useDialog()

const item = ref<KnowledgeDetailVO | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const acting = ref(false)

const id = computed(() => Number(route.params.id))

/** 仅作者本人或管理员可写。与后端 assertWritable 的规则一致 */
const canWrite = computed(() => {
  if (!item.value || !auth.user) return false
  return auth.isAdmin || item.value.authorId === auth.user.id
})

/** 按当前状态决定可用操作，避免把非法操作呈现给用户 */
const actions = computed(() => {
  if (!item.value) return []
  const list: { key: string; label: string; type?: 'primary' | 'warning' }[] = []
  if (item.value.status === 'DRAFT') {
    list.push({ key: 'publish', label: '发布', type: 'primary' })
    list.push({ key: 'archive', label: '归档' })
  } else if (item.value.status === 'PUBLISHED') {
    list.push({ key: 'archive', label: '归档', type: 'warning' })
  } else {
    list.push({ key: 'restore', label: '恢复到草稿', type: 'primary' })
  }
  return list
})

async function load() {
  loading.value = true
  error.value = null
  item.value = null
  try {
    item.value = await knowledgeApi.detail(id.value)
  } catch (e) {
    error.value = describeError(e)
  } finally {
    loading.value = false
  }
}

async function act(key: string) {
  if (!item.value) return
  acting.value = true
  try {
    const updated =
      key === 'publish'
        ? await knowledgeApi.publish(item.value.id)
        : key === 'archive'
          ? await knowledgeApi.archive(item.value.id)
          : await knowledgeApi.restore(item.value.id)
    item.value = updated
    message.success('操作成功')
  } catch (e) {
    message.error(describeError(e))
  } finally {
    acting.value = false
  }
}

function confirmRemove() {
  if (!item.value) return
  dialog.error({
    title: '删除知识条目',
    content: `确定删除「${item.value.title}」吗？该操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await knowledgeApi.remove(item.value!.id)
        message.success('已删除')
        router.push('/')
      } catch (e) {
        message.error(describeError(e))
      }
    }
  })
}

onMounted(load)
watch(id, load)
</script>

<template>
  <AppLayout>
    <QueryState :loading="loading" :error="error" :empty="false" :on-retry="load">
      <template v-if="item">
        <div class="article page-section">
          <h1 class="title">{{ item.title }}</h1>

          <div class="meta">
            <n-tag :type="STATUS_TYPE[item.status]" size="small" :bordered="false">
              {{ STATUS_LABEL[item.status] }}
            </n-tag>
            <n-tag v-if="item.categoryName" size="small" :bordered="false">
              {{ item.categoryName }}
            </n-tag>
            <span v-if="item.authorName">{{ item.authorName }}</span>
            <span>浏览 {{ item.viewCount }}</span>
            <span>更新于 {{ formatDateTime(item.updatedAt) }}</span>
          </div>

          <div v-if="item.tags.length" class="tags">
            <n-tag
              v-for="tag in item.tags"
              :key="tag"
              size="small"
              round
              :bordered="false"
              class="tag"
              @click="router.push({ path: '/search', query: { keyword: tag } })"
            >
              {{ tag }}
            </n-tag>
          </div>

          <div v-if="canWrite" class="ops">
            <n-button size="small" @click="router.push(`/editor/${item.id}`)">编辑</n-button>
            <n-button
              v-for="a in actions"
              :key="a.key"
              size="small"
              :type="a.type"
              :loading="acting"
              @click="act(a.key)"
            >
              {{ a.label }}
            </n-button>
            <n-button size="small" type="error" ghost @click="confirmRemove">删除</n-button>
          </div>

          <n-divider />

          <MarkdownView :content="item.content" />
        </div>

        <AttachmentPanel :item-id="item.id" :editable="canWrite" />
      </template>
    </QueryState>
  </AppLayout>
</template>

<style scoped>
.article {
  background: #fff;
  border: 1px solid var(--kms-border);
  border-radius: 8px;
  padding: 20px 24px;
}

.title {
  margin: 0 0 10px;
  font-size: 22px;
  line-height: 1.4;
}

.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--kms-text-secondary);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.tag {
  cursor: pointer;
}

.ops {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}
</style>
