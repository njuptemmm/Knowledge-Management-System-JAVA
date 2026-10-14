<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDialog, useMessage } from 'naive-ui'
import { knowledgeApi } from '@/api/knowledge'
import { describeError } from '@/api/client'
import { usePagedList } from '@/composables/usePagedList'
import type { KnowledgeListVO, KnowledgeStatus } from '@/types/api'
import { STATUS_LABEL, STATUS_TYPE, formatDateTime } from '@/utils/format'
import AppLayout from '@/components/AppLayout.vue'
import QueryState from '@/components/QueryState.vue'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const tab = ref<KnowledgeStatus>('DRAFT')

const list = usePagedList(
  (page, size) => knowledgeApi.page({ status: tab.value, mine: true, page, size }),
  { deps: () => [tab.value] }
)

const tabs: { key: KnowledgeStatus; label: string }[] = [
  { key: 'DRAFT', label: '草稿' },
  { key: 'PUBLISHED', label: '已发布' },
  { key: 'ARCHIVED', label: '已归档' }
]

/**
 * 按当前状态列出合法操作。
 * 后端对非法跃迁会拒绝，前端不应把非法操作摆给用户点。
 */
const OPERATIONS: Record<KnowledgeStatus, { key: string; label: string }[]> = {
  DRAFT: [
    { key: 'publish', label: '发布' },
    { key: 'archive', label: '归档' },
    { key: 'edit', label: '编辑' },
    { key: 'remove', label: '删除' }
  ],
  PUBLISHED: [
    { key: 'archive', label: '归档' },
    { key: 'edit', label: '编辑' },
    { key: 'remove', label: '删除' }
  ],
  ARCHIVED: [
    { key: 'restore', label: '恢复' },
    { key: 'remove', label: '删除' }
  ]
}

const operations = computed(() => OPERATIONS[tab.value])

async function run(item: KnowledgeListVO, key: string) {
  if (key === 'edit') {
    router.push(`/editor/${item.id}`)
    return
  }
  if (key === 'remove') {
    dialog.error({
      title: '删除知识条目',
      content: `确定删除「${item.title}」吗？该操作不可恢复。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          await knowledgeApi.remove(item.id)
          message.success('已删除')
          list.reload()
        } catch (e) {
          message.error(describeError(e))
        }
      }
    })
    return
  }
  try {
    if (key === 'publish') await knowledgeApi.publish(item.id)
    else if (key === 'archive') await knowledgeApi.archive(item.id)
    else if (key === 'restore') await knowledgeApi.restore(item.id)
    message.success('操作成功')
    list.reload()
  } catch (e) {
    message.error(describeError(e))
  }
}

onMounted(list.reload)
</script>

<template>
  <AppLayout>
    <h2 class="heading page-section">我的知识库</h2>

    <n-tabs v-model:value="tab" type="line" class="page-section">
      <n-tab v-for="t in tabs" :key="t.key" :name="t.key">{{ t.label }}</n-tab>
    </n-tabs>

    <QueryState
      :loading="list.loading.value"
      :error="list.error.value"
      :empty="list.empty.value"
      :empty-text="`${tab === 'DRAFT' ? '草稿箱是空的' : '这里还没有条目'}`"
      :on-retry="list.reload"
    >
      <template #empty>
        <n-button type="primary" @click="router.push('/editor/new')">去写一篇</n-button>
      </template>

      <div class="rows">
        <div v-for="item in list.records.value" :key="item.id" class="row">
          <div class="main" @click="router.push(`/detail/${item.id}`)">
            <div class="title-line">
              <span class="title">{{ item.title }}</span>
              <n-tag :type="STATUS_TYPE[item.status]" size="tiny" :bordered="false">
                {{ STATUS_LABEL[item.status] }}
              </n-tag>
            </div>
            <div class="sub">
              <span v-if="item.categoryName">{{ item.categoryName }}</span>
              <span>浏览 {{ item.viewCount }}</span>
              <span>{{ formatDateTime(item.updatedAt) }}</span>
            </div>
          </div>
          <div class="ops">
            <n-button
              v-for="op in operations"
              :key="op.key"
              size="tiny"
              quaternary
              :type="op.key === 'remove' ? 'error' : 'default'"
              @click="run(item, op.key)"
            >
              {{ op.label }}
            </n-button>
          </div>
        </div>
      </div>

      <div class="pager">
        <n-pagination
          v-model:page="list.page.value"
          :page-size="list.size.value"
          :item-count="list.total.value"
        />
      </div>
    </QueryState>
  </AppLayout>
</template>

<style scoped>
.heading {
  margin: 0;
  font-size: 17px;
}

.rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid var(--kms-border);
  border-radius: 8px;
  padding: 12px 16px;
}

.main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--kms-text-secondary);
  margin-top: 2px;
}

.ops {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

@media (max-width: 1024px) {
  .row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
