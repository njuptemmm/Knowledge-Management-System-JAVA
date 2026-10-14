<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { KnowledgeListVO } from '@/types/api'
import { STATUS_LABEL, STATUS_TYPE, formatDateTime } from '@/utils/format'
import KeywordHighlight from './KeywordHighlight.vue'

const props = defineProps<{
  item: KnowledgeListVO
  /** 有关键词时对标题做高亮 */
  keyword?: string | null
}>()

const router = useRouter()

function open() {
  router.push(`/detail/${props.item.id}`)
}
</script>

<template>
  <div class="card" @click="open">
    <div class="head">
      <h3 class="title">
        <KeywordHighlight :text="item.title" :keyword="keyword" />
      </h3>
      <n-tag :type="STATUS_TYPE[item.status]" size="small" :bordered="false">
        {{ STATUS_LABEL[item.status] }}
      </n-tag>
    </div>

    <p v-if="item.summary" class="summary">{{ item.summary }}</p>

    <div class="meta">
      <n-tag v-if="item.categoryName" size="tiny" :bordered="false">
        {{ item.categoryName }}
      </n-tag>
      <span v-if="item.authorName">{{ item.authorName }}</span>
      <span>浏览 {{ item.viewCount }}</span>
      <span>{{ formatDateTime(item.updatedAt) }}</span>
    </div>
  </div>
</template>

<style scoped>
.card {
  background: #fff;
  border: 1px solid var(--kms-border);
  border-radius: 8px;
  padding: 14px 16px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.card:hover {
  border-color: var(--kms-primary);
  box-shadow: 0 2px 10px rgba(32, 128, 240, 0.1);
}

.head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.title {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  /* 长标题截断到两行，避免卡片高度参差 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.summary {
  margin: 6px 0 8px;
  color: var(--kms-text-secondary);
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--kms-text-secondary);
}
</style>
