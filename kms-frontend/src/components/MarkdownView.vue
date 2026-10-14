<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 只注册用得到的语言。
// `import hljs from 'highlight.js'` 会把 190 种语言全打进来，构建产物 1MB；
// 按需注册后降到约 50KB。
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import xml from 'highlight.js/lib/languages/xml'
import json from 'highlight.js/lib/languages/json'
import yaml from 'highlight.js/lib/languages/yaml'
import markdown from 'highlight.js/lib/languages/markdown'
import 'highlight.js/styles/github.css'

hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('json', json)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('markdown', markdown)

const props = defineProps<{ content: string | null | undefined }>()

const root = ref<HTMLElement | null>(null)

const html = computed(() => {
  if (!props.content) return ''
  const raw = marked.parse(props.content, { async: false }) as string
  // 必须过滤：正文由用户提交，未过滤等于给每个读者装了一个 XSS 入口。
  // 这是整个前端唯一允许注入 HTML 的地方，因此也是唯一需要 DOMPurify 的地方。
  return DOMPurify.sanitize(raw, { ADD_ATTR: ['target', 'rel'] })
})

async function highlightCode() {
  await nextTick()
  root.value?.querySelectorAll('pre code').forEach((el) => {
    // 已高亮过的元素重复调用会报错，先清标记
    el.removeAttribute('data-highlighted')
    hljs.highlightElement(el as HTMLElement)
  })
}

onMounted(highlightCode)
watch(() => props.content, highlightCode)
</script>

<template>
  <div ref="root" class="markdown-body" v-html="html" />
</template>
