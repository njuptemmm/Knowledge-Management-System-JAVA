<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  text: string | null | undefined
  keyword?: string | null
}>()

interface Part {
  text: string
  hit: boolean
}

/**
 * 把文本按关键词切成若干段，用 Vue 模板渲染 <mark>。
 *
 * **刻意不使用 v-html**：后端返回的 snippet 是纯文本（它刻意不拼 HTML 以防 XSS），
 * 前端若用 v-html 把关键词包进去，等于把后端拆掉的那道防线又装回来。
 * Vue 模板渲染会自动转义，因此这里无需额外处理。
 */
const parts = computed<Part[]>(() => {
  const text = props.text ?? ''
  const keyword = props.keyword?.trim()
  if (!text) return []
  if (!keyword) return [{ text, hit: false }]

  const lowerText = text.toLowerCase()
  const lowerKeyword = keyword.toLowerCase()
  const result: Part[] = []
  let cursor = 0

  for (;;) {
    const index = lowerText.indexOf(lowerKeyword, cursor)
    if (index < 0) break
    if (index > cursor) result.push({ text: text.slice(cursor, index), hit: false })
    result.push({ text: text.slice(index, index + keyword.length), hit: true })
    cursor = index + keyword.length
  }
  if (cursor < text.length) result.push({ text: text.slice(cursor), hit: false })
  return result
})
</script>

<template>
  <span>
    <template v-for="(part, i) in parts" :key="i">
      <mark v-if="part.hit">{{ part.text }}</mark>
      <template v-else>{{ part.text }}</template>
    </template>
  </span>
</template>
