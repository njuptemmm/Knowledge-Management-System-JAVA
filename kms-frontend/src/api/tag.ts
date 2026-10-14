import { http } from './client'
import type { TagVO } from '@/types/api'

/**
 * 标签没有独立的增删接口，这是有意的：
 * 标签由条目的使用自然产生，单独管理反而增加负担。
 * 编辑器通过 `KnowledgeSaveRequest.tagNames` 按名称提交，不存在的会自动创建。
 */
export const tagApi = {
  list: () => http.get<TagVO[]>('/api/tags')
}
