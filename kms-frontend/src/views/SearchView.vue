<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { categoryApi } from '@/api/category'
import { searchApi, SORT_OPTIONS } from '@/api/search'
import { tagApi } from '@/api/tag'
import { usePagedList } from '@/composables/usePagedList'
import { useAuthStore } from '@/stores/auth'
import type { CategoryNode, KnowledgeStatus, TagVO } from '@/types/api'
import { STATUS_LABEL } from '@/utils/format'
import AppLayout from '@/components/AppLayout.vue'
import CategoryTree from '@/components/CategoryTree.vue'
import KeywordHighlight from '@/components/KeywordHighlight.vue'
import QueryState from '@/components/QueryState.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const keyword = ref((route.query.keyword as string) ?? '')
const categoryId = ref<number | null>(null)
const tagId = ref<number | null>(null)
const status = ref<KnowledgeStatus | null>(null)
const sort = ref<'relevance' | 'latest' | 'hottest'>('relevance')
const dateRange = ref<[number, number] | null>(null)
const mineOnly = ref(false)

const tree = ref<CategoryNode[]>([])
const tags = ref<TagVO[]>([])

const list = usePagedList(
  (page, size) =>
    searchApi.search({
      keyword: keyword.value.trim() || undefined,
      categoryId: categoryId.value,
      tagId: tagId.value,
      status: status.value ?? undefined,
      sort: sort.value,
      from: dateRange.value ? toDate(dateRange.value[0]) : undefined,
      to: dateRange.value ? toDate(dateRange.value[1]) : undefined,
      mine: mineOnly.value || undefined,
      page,
      size
    }),
  {
    deps: () => [
      keyword.value,
      categoryId.value,
      tagId.value,
      status.value,
      sort.value,
      mineOnly.value,
      dateRange.value
    ]
  }
)

function toDate(ts: number) {
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const tagOptions = computed(() =>
  tags.value.map((t) => ({ label: `${t.name}（${t.itemCount}）`, value: t.id }))
)

const statusOptions = (['PUBLISHED', 'DRAFT', 'ARCHIVED'] as KnowledgeStatus[]).map((s) => ({
  label: STATUS_LABEL[s],
  value: s
}))

/** 有任何一个筛选条件生效时，空结果要提示用户可能是筛得太严 */
const hasFilter = computed(
  () =>
    !!categoryId.value ||
    !!tagId.value ||
    !!status.value ||
    !!dateRange.value ||
    mineOnly.value ||
    !!keyword.value.trim()
)

function submitSearch() {
  const trimmed = keyword.value.trim()
  router.replace({ path: '/search', query: trimmed ? { keyword: trimmed } : {} })
  list.reset()
}

function clearFilters() {
  categoryId.value = null
  tagId.value = null
  status.value = null
  dateRange.value = null
  mineOnly.value = false
  sort.value = 'relevance'
}

function openItem(id: number) {
  router.push(`/detail/${id}`)
}

onMounted(async () => {
  list.reload()
  try {
    tree.value = await categoryApi.tree()
  } catch {
    /* 分类树拉不到不影响检索 */
  }
  try {
    tags.value = await tagApi.list()
  } catch {
    /* 同上 */
  }
})
</script>

<template>
  <AppLayout>
    <template #sidebar>
      <CategoryTree v-model="categoryId" :nodes="tree" />
    </template>

    <div class="search-bar page-section">
      <n-input
        v-model:value="keyword"
        placeholder="输入关键词，回车检索"
        clearable
        size="large"
        @keyup.enter="submitSearch"
      >
        <template #prefix>
          <n-button text @click="submitSearch">搜索</n-button>
        </template>
      </n-input>
    </div>

    <n-card class="filters page-section" size="small" :bordered="true">
      <div class="filter-grid">
        <div class="field">
          <label>标签</label>
          <n-select v-model:value="tagId" :options="tagOptions" clearable placeholder="全部标签" />
        </div>
        <div class="field">
          <label>状态</label>
          <n-select
            v-model:value="status"
            :options="statusOptions"
            clearable
            placeholder="全部状态"
          />
        </div>
        <div class="field">
          <label>时间范围</label>
          <n-date-picker v-model:value="dateRange" type="daterange" clearable />
        </div>
        <div class="field">
          <label>排序</label>
          <n-select v-model:value="sort" :options="[...SORT_OPTIONS]" />
        </div>
        <div class="field checkboxes">
          <n-checkbox v-if="auth.isLoggedIn" v-model:checked="mineOnly">
            只看我的（含草稿）
          </n-checkbox>
          <n-button v-if="hasFilter" size="small" quaternary @click="clearFilters">
            清空筛选
          </n-button>
        </div>
      </div>
    </n-card>

    <QueryState
      :loading="list.loading.value"
      :error="list.error.value"
      :empty="list.empty.value"
      :on-retry="list.reload"
    >
      <template #empty>
        <p class="empty-title">没有匹配的结果</p>
        <p v-if="hasFilter" class="empty-hint">
          当前有筛选条件生效，试试放宽条件或换个关键词
        </p>
        <n-button v-if="hasFilter" size="small" @click="clearFilters">清空筛选条件</n-button>
      </template>

      <div class="result-count">共 {{ list.total.value }} 条结果</div>

      <div class="results">
        <div v-for="hit in list.records.value" :key="hit.id" class="hit" @click="openItem(hit.id)">
          <h3 class="hit-title">
            <KeywordHighlight :text="hit.title" :keyword="keyword" />
          </h3>
          <p v-if="hit.snippet" class="hit-snippet">
            <KeywordHighlight :text="hit.snippet" :keyword="keyword" />
          </p>
          <div class="hit-meta">
            <n-tag v-if="hit.categoryName" size="tiny" :bordered="false">
              {{ hit.categoryName }}
            </n-tag>
            <n-tag v-for="t in hit.tags" :key="t" size="tiny" round :bordered="false">
              {{ t }}
            </n-tag>
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
.filters {
  border-radius: 8px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px 16px;
  align-items: end;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field label {
  font-size: 12px;
  color: var(--kms-text-secondary);
}

.checkboxes {
  flex-direction: row;
  align-items: center;
  gap: 12px;
}

.result-count {
  font-size: 12px;
  color: var(--kms-text-secondary);
  margin-bottom: 10px;
}

.results {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hit {
  background: #fff;
  border: 1px solid var(--kms-border);
  border-radius: 8px;
  padding: 12px 16px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.hit:hover {
  border-color: var(--kms-primary);
}

.hit-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
}

.hit-snippet {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--kms-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hit-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.empty-title {
  margin: 0;
  font-size: 14px;
}

.empty-hint {
  margin: 4px 0 10px;
  font-size: 12px;
  color: var(--kms-text-secondary);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}
</style>
