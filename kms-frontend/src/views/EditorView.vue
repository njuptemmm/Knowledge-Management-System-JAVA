<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { categoryApi } from '@/api/category'
import { knowledgeApi } from '@/api/knowledge'
import { describeError } from '@/api/client'
import type { CategoryNode, KnowledgeSaveRequest } from '@/types/api'
import AppLayout from '@/components/AppLayout.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import TagInput from '@/components/TagInput.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const editId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => editId.value !== null)

const title = ref('')
const summary = ref('')
const content = ref('')
const categoryId = ref<number | null>(null)
const tagNames = ref<string[]>([])

const loading = ref(false)
const saving = ref(false)
const loadError = ref<string | null>(null)

/** 用于判断是否有未保存修改 */
const pristine = ref('')
let dirty = false

function snapshot() {
  return JSON.stringify({
    title: title.value,
    summary: summary.value,
    content: content.value,
    categoryId: categoryId.value,
    tagNames: [...tagNames.value].sort()
  })
}

function markPristine() {
  pristine.value = snapshot()
  dirty = false
}

/** 分类树转成 n-tree-select 需要的结构 */
const categoryOptions = ref<{ key: number; label: string; children?: unknown[] }[]>([])

function toOptions(nodes: CategoryNode[]): { key: number; label: string; children?: unknown[] }[] {
  return nodes.map((n) => ({
    key: n.id,
    label: n.name,
    children: n.children?.length ? toOptions(n.children) : undefined
  }))
}

async function loadCategories() {
  try {
    const tree = await categoryApi.tree()
    categoryOptions.value = toOptions(tree)
  } catch {
    // 分类拉取失败不阻塞编辑，用户可以先不选分类
  }
}

async function loadItem() {
  if (!editId.value) {
    markPristine()
    return
  }
  loading.value = true
  loadError.value = null
  try {
    const detail = await knowledgeApi.detail(editId.value)
    title.value = detail.title
    summary.value = detail.summary ?? ''
    content.value = detail.content ?? ''
    categoryId.value = detail.categoryId
    tagNames.value = [...detail.tags]
    markPristine()
  } catch (e) {
    loadError.value = describeError(e)
  } finally {
    loading.value = false
  }
}

async function save(publish: boolean) {
  if (!title.value.trim()) {
    message.warning('标题不能为空')
    return
  }
  saving.value = true
  try {
    const body: KnowledgeSaveRequest = {
      title: title.value.trim(),
      summary: summary.value || undefined,
      content: content.value,
      contentType: 'MARKDOWN',
      categoryId: categoryId.value,
      // 用 tagNames 而不是 tagIds：新标签没有 id，只能按名称提交
      tagNames: tagNames.value
    }
    const saved = editId.value
      ? await knowledgeApi.update(editId.value, body)
      : await knowledgeApi.create(body)

    markPristine()

    if (publish && saved.status === 'DRAFT') {
      await knowledgeApi.publish(saved.id)
      message.success('已保存并发布')
    } else {
      message.success('已保存')
    }
    router.replace(`/detail/${saved.id}`)
  } catch (e) {
    message.error(describeError(e))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadCategories()
  loadItem()
})

// 有未保存修改时离开要确认，否则用户误触返回会丢掉整篇内容
onBeforeRouteLeave(() => {
  dirty = snapshot() !== pristine.value
  if (!dirty) return true
  return window.confirm('有未保存的修改，确定离开吗？')
})
</script>

<template>
  <AppLayout>
    <div class="editor-head page-section">
      <h2 class="heading">{{ isEdit ? '编辑知识条目' : '新建知识条目' }}</h2>
      <div class="head-ops">
        <n-button :loading="saving" @click="save(false)">保存草稿</n-button>
        <n-button type="primary" :loading="saving" @click="save(true)">保存并发布</n-button>
      </div>
    </div>

    <n-alert v-if="loadError" type="error" class="page-section" :show-icon="true">
      {{ loadError }}
    </n-alert>

    <n-spin :show="loading">
      <div class="meta-form page-section">
        <n-form label-placement="top">
          <n-form-item label="标题" required>
            <n-input v-model:value="title" placeholder="给这篇知识起个名字" maxlength="200" show-count />
          </n-form-item>
          <n-form-item label="摘要">
            <n-input
              v-model:value="summary"
              type="textarea"
              :rows="2"
              placeholder="一句话概括，会显示在列表与检索结果里"
              maxlength="500"
              show-count
            />
          </n-form-item>
          <div class="two-col">
            <n-form-item label="分类">
              <n-tree-select
                v-model:value="categoryId"
                :options="categoryOptions"
                clearable
                placeholder="选择分类"
                key-field="key"
                label-field="label"
                children-field="children"
              />
            </n-form-item>
            <n-form-item label="标签">
              <TagInput v-model="tagNames" />
            </n-form-item>
          </div>
        </n-form>
      </div>

      <div class="split">
        <div class="pane">
          <div class="pane-title">Markdown 正文</div>
          <n-input
            v-model:value="content"
            type="textarea"
            placeholder="# 标题&#10;&#10;支持 Markdown 语法，右侧实时预览"
            :autosize="{ minRows: 20 }"
            class="content-input"
          />
        </div>
        <div class="pane">
          <div class="pane-title">预览</div>
          <div class="preview">
            <MarkdownView :content="content" />
            <n-empty v-if="!content" description="左侧输入后这里会实时预览" size="small" />
          </div>
        </div>
      </div>
    </n-spin>
  </AppLayout>
</template>

<style scoped>
.editor-head {
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

.meta-form {
  background: var(--kms-surface);
  border: 1px solid var(--kms-border);
  border-radius: 8px;
  padding: 14px 16px 0;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.pane {
  background: var(--kms-surface);
  border: 1px solid var(--kms-border);
  border-radius: 8px;
  padding: 12px;
  min-width: 0;
}

.pane-title {
  font-size: 12px;
  color: var(--kms-text-secondary);
  margin-bottom: 8px;
}

.content-input :deep(textarea) {
  font-family: 'Cascadia Code', Consolas, Monaco, monospace;
  font-size: 13px;
  line-height: 1.7;
}

.preview {
  min-height: 320px;
  max-height: 70vh;
  overflow: auto;
  padding-right: 4px;
}

/* 窄屏改为上下布局，左右分栏在手机上无法使用 */
@media (max-width: 1024px) {
  .split,
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
