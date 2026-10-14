<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CategoryNode } from '@/types/api'

const props = withDefaults(
  defineProps<{
    /** 后端返回的分类树 */
    nodes: CategoryNode[]
    /** 当前选中的分类 id；null 表示「全部」 */
    modelValue?: number | null
    /** 是否显示「全部」节点 */
    showAll?: boolean
    /** 是否显示「新建子分类」入口 */
    allowCreate?: boolean
  }>(),
  { modelValue: null, showAll: true, allowCreate: false }
)

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  create: [parentId: number | null]
}>()

interface TreeOption {
  key: number | string
  label: string
  children?: TreeOption[]
}

const ALL_KEY = '__all__'

/**
 * 后端返回嵌套结构，naive-ui 也要嵌套结构，但 key 字段名不同，
 * 且「全部」节点要插在最前。转换保持原顺序（后端已按 sort 排好）。
 */
const options = computed<TreeOption[]>(() => {
  const convert = (nodes: CategoryNode[]): TreeOption[] =>
    nodes.map((n) => ({
      key: n.id,
      label: n.name,
      children: n.children?.length ? convert(n.children) : undefined
    }))

  const result = convert(props.nodes ?? [])
  return props.showAll ? [{ key: ALL_KEY, label: '全部' }, ...result] : result
})

/** 默认展开根层级，让用户一眼看到结构 */
const expandedKeys = ref<Array<number | string>>((props.nodes ?? []).map((n) => n.id))

const selectedKeys = computed<Array<number | string>>(() => [
  props.modelValue === null ? ALL_KEY : props.modelValue
])

function handleSelect(keys: Array<number | string>) {
  const key = keys[0]
  // 点到空白处或再点一次已选中的分类，都视为取消筛选
  if (key === undefined || key === selectedKeys.value[0]) {
    emit('update:modelValue', null)
    return
  }
  emit('update:modelValue', key === ALL_KEY ? null : Number(key))
}

// 分类刷新后，新增的根节点也应当展开
watch(
  () => props.nodes,
  (nodes) => {
    const known = new Set(expandedKeys.value)
    for (const n of nodes ?? []) known.add(n.id)
    expandedKeys.value = [...known]
  },
  { deep: true }
)
</script>

<template>
  <div class="category-tree">
    <n-tree
      block-line
      :data="options"
      :selected-keys="selectedKeys"
      :expanded-keys="expandedKeys"
      selectable
      :cancelable="false"
      @update:selected-keys="handleSelect"
      @update:expanded-keys="(keys: Array<number | string>) => (expandedKeys = keys)"
    />
    <n-button
      v-if="allowCreate"
      quaternary
      size="small"
      class="create-btn"
      @click="emit('create', modelValue ?? null)"
    >
      + 新建子分类
    </n-button>
  </div>
</template>

<style scoped>
.category-tree {
  padding: 4px 0;
}

.create-btn {
  margin-top: 8px;
  width: 100%;
  justify-content: flex-start;
}
</style>
