import { computed, ref, watch, type Ref } from 'vue'
import { describeError } from '@/api/client'
import type { Page } from '@/types/api'

export interface PagedListOptions {
  size?: number
  /** 额外筛选条件。返回的数组内容变化时自动回到第一页并重新加载 */
  deps?: () => unknown[]
}

/**
 * 分页列表的加载 / 错误 / 空态 / 切页统一处理。
 *
 * 并发保护用请求序号实现：快速切页时先发的请求可能后返回，
 * 不加序号会用旧结果覆盖新结果。
 */
export function usePagedList<T>(
  fetcher: (page: number, size: number) => Promise<Page<T>>,
  options: PagedListOptions = {}
) {
  const records: Ref<T[]> = ref([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(options.size ?? 10)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const empty = computed(() => !loading.value && !error.value && records.value.length === 0)

  let requestId = 0

  async function reload() {
    const current = ++requestId
    loading.value = true
    error.value = null
    try {
      const result = await fetcher(page.value, size.value)
      if (current !== requestId) return // 已被更新的请求取代，丢弃结果
      records.value = result?.records ?? []
      total.value = result?.total ?? 0
    } catch (e) {
      if (current !== requestId) return
      error.value = describeError(e)
      records.value = []
      total.value = 0
    } finally {
      if (current === requestId) loading.value = false
    }
  }

  /** 筛选条件变化时回到第一页再查，否则会停在一个空的第 3 页 */
  async function reset() {
    if (page.value !== 1) {
      page.value = 1 // 触发下面的 watch(page) → reload
    } else {
      await reload()
    }
  }

  if (options.deps) {
    watch(options.deps, reset, { deep: true })
  }
  watch(page, reload)

  return { records, total, page, size, loading, error, empty, reload, reset }
}
