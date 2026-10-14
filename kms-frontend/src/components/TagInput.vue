<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import type { VNodeChild } from 'vue'
import { tagApi } from '@/api/tag'
import { describeError } from '@/api/client'
import type { TagVO } from '@/types/api'

/**
 * 标签输入。
 *
 * 用 `tag` 模式允许直接敲新标签名——后端 `tagNames` 会自动创建不存在的标签。
 * 如果不这样做，用户就只能从已有标签里挑，无法建立新标签。
 */
const model = defineModel<string[]>({ default: () => [] })

const options = ref<{ label: string; value: string }[]>([])
const loadError = ref<string | null>(null)

/** 引用次数只在候选列表里显示，帮助用户判断哪些标签常用 */
const counts = ref<Record<string, number>>({})

onMounted(async () => {
  try {
    const tags: TagVO[] = await tagApi.list()
    // label 用纯名称：naive-ui 会把 label 复用到已选标签上，
    // 带上计数的话选中后会显示成「入门（6）」，那是噪音
    options.value = tags.map((t) => ({ label: t.name, value: t.name }))
    counts.value = Object.fromEntries(tags.map((t) => [t.name, t.itemCount]))
  } catch (e) {
    loadError.value = describeError(e)
  }
})

defineProps<{ max?: number }>()

function renderLabel(option: { label?: string; value: string }): VNodeChild {
  const name = String(option.label ?? option.value)
  const count = counts.value[name]
  if (count === undefined) {
    return name
  }
  return h('span', { class: 'tag-option' }, [
    h('span', name),
    h('span', { class: 'tag-count' }, `${count} 次`)
  ])
}
</script>

<template>
  <n-select
    class="tag-input"
    v-model:value="model"
    multiple
    filterable
    tag
    clearable
    :options="options"
    :max-tag-count="6"
    :max="max ?? 10"
    placeholder="选择已有标签，或直接输入新标签名后回车"
    :render-label="renderLabel"
  />
  <p v-if="loadError" class="tag-error">标签列表加载失败：{{ loadError }}（仍可直接输入新标签）</p>
</template>

<style scoped>
.tag-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.tag-count {
  font-size: 12px;
  color: var(--kms-text-secondary);
}

.tag-error {
  margin: 4px 0 0;
  font-size: 12px;
  color: #d03050;
}
</style>
