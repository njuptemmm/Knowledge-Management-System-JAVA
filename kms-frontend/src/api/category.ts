import { http } from './client'
import type { CategoryNode } from '@/types/api'

export interface CategorySaveRequest {
  name: string
  parentId?: number | null
  sort?: number
  description?: string
}

export const categoryApi = {
  tree: () => http.get<CategoryNode[]>('/api/categories/tree'),

  create: (body: CategorySaveRequest) => http.post<CategoryNode>('/api/categories', { body }),

  /**
   * 修改分类。注意：后端会**忽略 parentId**（v0.0.1 不支持移动分类），
   * 界面上应把父分类设为只读展示，而不是让用户改了却无效。
   */
  update: (id: number, body: CategorySaveRequest) =>
    http.put<CategoryNode>(`/api/categories/${id}`, { body }),

  remove: (id: number) => http.del<void>(`/api/categories/${id}`)
}
