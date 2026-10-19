<script setup lang="ts">
import { computed, ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * 渲染智能问答的答案。
 *
 * 与 MarkdownView 的区别：答案里带有 `[1]` `[2]` 这样的引用标记，
 * 需要渲染成可点击的角标而不是纯文本。做法是先把标记改写成
 * 指向锚点的 Markdown 链接，渲染后再拦截点击——
 * 这样既能享受 Markdown 渲染（大模型的回答必然带加粗、列表），
 * 又不必在渲染后去遍历 DOM 改文本节点。
 */
const props = defineProps<{
  content: string
  /** 引用条数，用于过滤越界标记（模型偶尔会写出不存在的编号） */
  sourceCount?: number
}>()

const emit = defineEmits<{ cite: [index: number] }>()

const root = ref<HTMLElement | null>(null)

const html = computed(() => {
  const raw = props.content ?? ''
  if (!raw) return ''

  const withLinks = raw.replace(/\[(\d+)\]/g, (whole, n) => {
    const index = Number(n)
    // 越界的编号保持原样，避免渲染出一个点了没反应的链接
    if (props.sourceCount !== undefined && (index < 1 || index > props.sourceCount)) {
      return whole
    }
    return `[${n}](#cite-${n})`
  })

  const rendered = marked.parse(normalizeListMarkers(withLinks), { async: false }) as string
  return DOMPurify.sanitize(rendered)
})

/**
 * 补上列表符后缺失的空格。
 *
 * Markdown 要求列表项是「- 内容」，短横线后必须有空格；但模型有时会输出
 * 「-内容」，那样整段会被当成普通文字，列表就挤成一行了（实际出现过）。
 *
 * 只处理「行首的 - * + 紧跟加粗标记或中日韩字符」这一种情形——
 * 这样不会误伤 `-5 度` 这类行首负数，也不会动正文里正常的连字符。
 */
const LIST_MARKER_MISSING_SPACE = /^([ \t]*)([-*+])(?=\*\*|[一-鿿　-〿])/gm

function normalizeListMarkers(text: string): string {
  return text.replace(LIST_MARKER_MISSING_SPACE, '$1$2 $3')
}

function onClick(event: MouseEvent) {
  const anchor = (event.target as HTMLElement)?.closest('a')
  if (!anchor) return
  const href = anchor.getAttribute('href') ?? ''
  if (!href.startsWith('#cite-')) return
  // 阻止跳到锚点导致的页面跳动
  event.preventDefault()
  emit('cite', Number(href.slice('#cite-'.length)))
}
</script>

<template>
  <div ref="root" class="markdown-body qa-answer" @click="onClick" v-html="html" />
</template>

<style scoped>
.qa-answer {
  font-size: 14px;
}

.qa-answer :deep(p) {
  margin: 0.4em 0;
}

.qa-answer :deep(p:first-child) {
  margin-top: 0;
}

.qa-answer :deep(p:last-child) {
  margin-bottom: 0;
}

/* 引用角标 */
.qa-answer :deep(a[href^='#cite-']) {
  color: var(--kms-primary);
  font-size: 0.85em;
  padding: 0 2px;
  border-radius: 3px;
  vertical-align: super;
  line-height: 0;
  cursor: pointer;
}

.qa-answer :deep(a[href^='#cite-']:hover) {
  background: var(--kms-accent-hover);
}
</style>
