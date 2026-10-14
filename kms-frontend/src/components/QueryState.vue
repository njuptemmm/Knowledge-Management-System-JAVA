<script setup lang="ts">
/**
 * 异步内容的三态统一呈现：加载中 / 出错 / 空。
 * 各页面不必重写一遍，也保证三种状态的样式与行为一致。
 *
 * 出错时必须给重试入口，空时必须允许通过 #empty 插槽给出下一步动作——
 * 只显示「暂无数据」等于把用户丢在死路上。
 */
defineProps<{
  loading?: boolean
  error?: string | null
  empty?: boolean
  emptyText?: string
  loadingText?: string
  onRetry?: () => void
}>()
</script>

<template>
  <div v-if="loading" class="query-state">
    <n-spin size="medium" />
    <p class="hint">{{ loadingText || '加载中…' }}</p>
  </div>

  <div v-else-if="error" class="query-state">
    <n-empty :description="error">
      <template #extra>
        <n-button v-if="onRetry" size="small" @click="onRetry">重试</n-button>
      </template>
    </n-empty>
  </div>

  <div v-else-if="empty" class="query-state">
    <n-empty :description="emptyText || '暂无数据'">
      <template #extra>
        <slot name="empty" />
      </template>
    </n-empty>
  </div>

  <slot v-else />
</template>

<style scoped>
.query-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  gap: 12px;
}

.hint {
  margin: 0;
  color: var(--kms-text-secondary);
  font-size: 13px;
}
</style>
