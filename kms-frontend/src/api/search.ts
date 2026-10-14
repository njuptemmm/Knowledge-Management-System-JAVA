import { http } from './client'
import type { Page, SearchHit } from '@/types/api'

export interface SearchQuery {
  keyword?: string
  categoryId?: number | null
  tagId?: number | null
  contentType?: string
  status?: string
  from?: string
  to?: string
  sort?: 'relevance' | 'latest' | 'hottest'
  mine?: boolean
  page?: number
  size?: number
}

export const searchApi = {
  search: (query: SearchQuery) => http.get<Page<SearchHit>>('/api/search', { params: { ...query } })
}

/** 排序选项。与后端 `SearchServiceImpl.ALLOWED_SORTS` 保持一致 */
export const SORT_OPTIONS = [
  { label: '相关度', value: 'relevance' },
  { label: '最新', value: 'latest' },
  { label: '最热', value: 'hottest' }
] as const
