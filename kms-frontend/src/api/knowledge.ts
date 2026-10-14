import { http } from './client'
import type {
  KnowledgeDetailVO,
  KnowledgeListVO,
  KnowledgeSaveRequest,
  Page
} from '@/types/api'

export interface KnowledgeQuery {
  categoryId?: number | null
  tagId?: number | null
  status?: string | null
  mine?: boolean
  page?: number
  size?: number
}

export const knowledgeApi = {
  /** 分页浏览。未登录或不传 mine 时只返回已发布条目 */
  page: (query: KnowledgeQuery) =>
    http.get<Page<KnowledgeListVO>>('/api/knowledge', { params: { ...query } }),

  /** 详情。**会使浏览数自增**，不要在列表渲染时循环调用 */
  detail: (id: number) => http.get<KnowledgeDetailVO>(`/api/knowledge/${id}`),

  create: (body: KnowledgeSaveRequest) => http.post<KnowledgeDetailVO>('/api/knowledge', { body }),

  update: (id: number, body: KnowledgeSaveRequest) =>
    http.put<KnowledgeDetailVO>(`/api/knowledge/${id}`, { body }),

  remove: (id: number) => http.del<void>(`/api/knowledge/${id}`),

  // 状态流转。后端对非法跃迁会拒绝，前端应按当前状态只显示合法操作
  publish: (id: number) => http.post<KnowledgeDetailVO>(`/api/knowledge/${id}/publish`),
  archive: (id: number) => http.post<KnowledgeDetailVO>(`/api/knowledge/${id}/archive`),
  restore: (id: number) => http.post<KnowledgeDetailVO>(`/api/knowledge/${id}/restore`)
}
