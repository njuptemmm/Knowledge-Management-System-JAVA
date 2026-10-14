<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { categoryApi } from '@/api/category'
import { knowledgeApi } from '@/api/knowledge'
import { usePagedList } from '@/composables/usePagedList'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/api/client'
import type { CategoryNode } from '@/types/api'
import AppLayout from '@/components/AppLayout.vue'
import CategoryTree from '@/components/CategoryTree.vue'
import KnowledgeCard from '@/components/KnowledgeCard.vue'
import QueryState from '@/components/QueryState.vue'

const router = useRouter()
const auth = useAuthStore()

const categoryId = ref<number | null>(null)
/** 只有登录用户能切到「我的全部状态」 */
const mineOnly = ref(false)

const tree = ref<CategoryNode[]>([])
const treeError = ref<string | null>(null)
const treeLoading = ref(false)

const list = usePagedList(
  (page, size) =>
    knowledgeApi.page({
      categoryId: categoryId.value,
      mine: mineOnly.value || undefined,
      page,
      size
    }),
  { deps: () => [categoryId.value, mineOnly.value] }
)

const scopeLabel = computed(() =>
  mineOnly.value ? '我的全部条目（含草稿与归档）' : '全部已发布条目'
)

async function loadTree() {
  treeLoading.value = true
  treeError.value = null
  try {
    tree.value = await categoryApi.tree()
  } catch (e) {
    treeError.value = describeError(e)
  } finally {
    treeLoading.value = false
  }
}

onMounted(() => {
  loadTree()
  list.reload()
})
</script>

<template>
  <AppLayout>
    <template #sidebar>
      <QueryState
        :loading="treeLoading"
        :error="treeError"
        :on-retry="loadTree"
        :empty="false"
      >
        <CategoryTree v-model="categoryId" :nodes="tree" />
      </QueryState>
    </template>

    <div class="toolbar page-section">
      <div class="scope">{{ scopeLabel }}</div>
      <n-checkbox v-if="auth.isLoggedIn" v-model:checked="mineOnly">
        只看我的（含草稿）
      </n-checkbox>
      <n-button
        v-if="auth.isLoggedIn"
        type="primary"
        size="small"
        class="create"
        @click="router.push('/editor/new')"
      >
        写一篇
      </n-button>
    </div>

    <QueryState
      :loading="list.loading.value"
      :error="list.error.value"
      :empty="list.empty.value"
      empty-text="这里还没有知识条目"
      :on-retry="list.reload"
    >
      <template #empty>
        <n-button v-if="auth.isLoggedIn" type="primary" @click="router.push('/editor/new')">
          去创建第一篇
        </n-button>
        <n-button v-else type="primary" @click="router.push('/login')">登录后创建</n-button>
      </template>

      <div class="list">
        <KnowledgeCard v-for="item in list.records.value" :key="item.id" :item="item" />
      </div>

      <div class="pager">
        <n-pagination
          v-model:page="list.page.value"
          :page-size="list.size.value"
          :item-count="list.total.value"
          :page-slot="7"
        />
      </div>
    </QueryState>
  </AppLayout>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.scope {
  font-size: 13px;
  color: var(--kms-text-secondary);
}

.create {
  margin-left: auto;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
