<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

onMounted(async () => {
  try {
    const tags: TagVO[] = await tagApi.list()
    options.value = tags.map((t) => ({ label: `${t.name}（${t.itemCount}）`, value: t.name }))
  } catch (e) {
    loadError.value = describeError(e)
  }
})

defineProps<{ max?: number }>()
</script>

<template>
  <n-select
    v-model:value="model"
    multiple
    filterable
    tag
    clearable
    :options="options"
    :max-tag-count="6"
    :max="max ?? 10"
    placeholder="选择已有标签，或直接输入新标签名后回车"
  />
  <p v-if="loadError" class="tag-error">标签列表加载失败：{{ loadError }}（仍可直接输入新标签）</p>
</template>

<style scoped>
.tag-error {
  margin: 4px 0 0;
  font-size: 12px;
  color: #d03050;
}
</style>
