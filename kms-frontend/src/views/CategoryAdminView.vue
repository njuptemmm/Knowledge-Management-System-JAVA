<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import { categoryApi } from '@/api/category'
import { describeError } from '@/api/client'
import type { CategoryNode } from '@/types/api'
import AppLayout from '@/components/AppLayout.vue'
import CategoryTree from '@/components/CategoryTree.vue'
import QueryState from '@/components/QueryState.vue'

const message = useMessage()
const dialog = useDialog()

const tree = ref<CategoryNode[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const selectedId = ref<number | null>(null)

const form = ref({ name: '', sort: 0, description: '' })
const saving = ref(false)
const creatingUnder = ref<number | null | undefined>(undefined)

/** 扁平化后按 id 找节点，避免在模板里递归查找 */
const flat = computed(() => {
  const result: CategoryNode[] = []
  const walk = (nodes: CategoryNode[]) => {
    for (const n of nodes) {
      result.push(n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(tree.value)
  return result
})

const selected = computed(() => flat.value.find((n) => n.id === selectedId.value) ?? null)
const parentName = computed(() => {
  const parentId = selected.value?.parentId
  if (!parentId) return '（根分类）'
  return flat.value.find((n) => n.id === parentId)?.name ?? '未知'
})

async function load() {
  loading.value = true
  error.value = null
  try {
    tree.value = await categoryApi.tree()
    // 选中的分类可能已被删除，重新定位
    if (selectedId.value && !flat.value.some((n) => n.id === selectedId.value)) {
      selectedId.value = null
    }
    // 首次进入默认选中第一个分类：否则右侧只有一个空提示，
    // 用户会以为这个页面没有实现任何功能
    if ((!selectedId.value || !flat.value.some((n) => n.id === selectedId.value))
        && !isCreating.value && tree.value.length) {
      select(tree.value[0]!.id)
    }
  } catch (e) {
    error.value = describeError(e)
  } finally {
    loading.value = false
  }
}

function select(id: number | null) {
  selectedId.value = id
  creatingUnder.value = undefined
  const node = flat.value.find((n) => n.id === id)
  if (node) {
    form.value = {
      name: node.name,
      sort: node.sort ?? 0,
      description: node.description ?? ''
    }
  }
}

/**
 * 进入新建模式。
 *
 * <p>新建子分类时保留父节点的选中状态：树上的高亮是「父分类是谁」最直观的说明，
 * 清掉它会让人不确定新分类挂在哪里。只有新建根分类才清空选择。
 */
function startCreate(parentId: number | null) {
  creatingUnder.value = parentId
  if (parentId === null) selectedId.value = null
  form.value = { name: '', sort: 0, description: '' }
}

/** 放弃新建，回到第一个分类，避免右侧停在空表单上。 */
function cancelCreate() {
  creatingUnder.value = undefined
  selectedId.value = null
  form.value = { name: '', sort: 0, description: '' }
  if (tree.value.length) select(tree.value[0]!.id)
}

const isCreating = computed(() => creatingUnder.value !== undefined)

async function save() {
  const name = form.value.name.trim()
  if (!name) {
    message.warning('分类名称不能为空')
    return
  }
  saving.value = true
  try {
    if (isCreating.value) {
      const created = await categoryApi.create({
        name,
        parentId: creatingUnder.value ?? 0,
        sort: form.value.sort,
        description: form.value.description || undefined
      })
      message.success('已创建')
      // 创建后切到新节点：停在空白的新建表单上，用户会以为没保存成功
      creatingUnder.value = undefined
      await load()
      select(created.id)
    } else if (selectedId.value) {
      // 后端会忽略 parentId 的变更，这里也不提交它
      await categoryApi.update(selectedId.value, {
        name,
        sort: form.value.sort,
        description: form.value.description || undefined
      })
      message.success('已保存')
    }
    await load()
  } catch (e) {
    message.error(describeError(e))
  } finally {
    saving.value = false
  }
}

function confirmRemove() {
  const node = selected.value
  if (!node) return
  dialog.error({
    title: '删除分类',
    content: `确定删除「${node.name}」吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await categoryApi.remove(node.id)
        message.success('已删除')
        selectedId.value = null
        form.value = { name: '', sort: 0, description: '' }
        await load()
      } catch (e) {
        // 后端的拒绝消息本身就说明了原因与下一步（「该分类下有 3 个知识条目，无法删除」），
        // 因此完整展示，不要替换成笼统的「删除失败」
        message.error(describeError(e))
      }
    }
  })
}

onMounted(load)
</script>

<template>
  <!-- keep-sidebar：窄屏下这里是主控件，隐藏后整页无法操作 -->
  <AppLayout sidebar-title="分类" :sidebar="true" keep-sidebar>
    <template #sidebar>
      <QueryState :loading="loading" :error="error" :empty="false" :on-retry="load">
        <CategoryTree
          :nodes="tree"
          :model-value="selectedId"
          :show-all="false"
          @update:model-value="select"
        />
        <!-- 根分类与子分类分开两个入口：合并成一个按钮时，
             用户无法判断当前会建在哪一层 -->
        <div class="tree-ops">
          <n-button size="small" block @click="startCreate(null)">+ 新建根分类</n-button>
          <n-button
            size="small"
            block
            dashed
            :disabled="!selectedId && !isCreating"
            @click="startCreate(selectedId)"
          >
            + 新建子分类
          </n-button>
        </div>
      </QueryState>
    </template>

    <h2 class="heading page-section">分类管理</h2>

    <!-- 没有任何分类时给出下一步动作，而不是一句「请从左侧选择」 -->
    <n-card v-if="!selectedId && !isCreating" class="form-card" :bordered="true">
      <n-empty :description="tree.length ? '从左侧选择一个分类进行编辑' : '还没有任何分类'">
        <template #extra>
          <n-button type="primary" @click="startCreate(null)">+ 新建根分类</n-button>
        </template>
      </n-empty>
    </n-card>

    <n-card v-else class="form-card" :bordered="true">
      <div class="form-head">
        <span>
          {{ isCreating ? (creatingUnder === null ? '新建根分类' : '新建子分类') : '编辑分类' }}
        </span>
      </div>

      <n-form label-placement="top">
        <n-form-item label="分类名称" required>
          <n-input v-model:value="form.name" maxlength="50" show-count placeholder="分类名称" />
        </n-form-item>

        <n-form-item label="父分类">
          <!-- 后端不支持移动分类，因此这里只读展示而不是给一个改了没用的选择器 -->
          <n-input
            :value="isCreating ? (creatingUnder ? (flat.find((n) => n.id === creatingUnder)?.name ?? '') : '（根分类）') : parentName"
            disabled
          />
        </n-form-item>

        <n-form-item label="排序（同级内，数字小的在前）">
          <n-input-number v-model:value="form.sort" :min="0" :max="9999" />
        </n-form-item>

        <n-form-item label="描述">
          <n-input
            v-model:value="form.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-count
          />
        </n-form-item>
      </n-form>

      <div class="ops">
        <n-button type="primary" :loading="saving" @click="save">
          {{ isCreating ? '创建' : '保存' }}
        </n-button>
        <n-button v-if="isCreating" @click="cancelCreate">取消</n-button>
        <n-button v-if="!isCreating" type="error" ghost @click="confirmRemove">删除</n-button>
      </div>

      <p class="note">
        注：本版本不支持移动分类（修改父分类），后端会忽略该字段。
      </p>
    </n-card>
  </AppLayout>
</template>

<style scoped>
.heading {
  margin: 0;
  font-size: 17px;
}

.form-card {
  max-width: 520px;
  border-radius: 8px;
}

.tree-ops {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.form-head {
  font-weight: 600;
  margin-bottom: 12px;
}

.ops {
  display: flex;
  gap: 8px;
}

.note {
  margin: 14px 0 0;
  font-size: 12px;
  color: var(--kms-text-secondary);
}
</style>
