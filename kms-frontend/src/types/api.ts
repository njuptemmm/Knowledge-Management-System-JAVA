/**
 * 后端接口的数据类型。
 *
 * 字段与 `kms-backend/docs/API.md` 一一对应，改名时必须两边同步。
 * 只声明实际用到的字段，不凭空补充后端没有的字段。
 */

/** 统一响应体。data 可能不存在（失败时后端不序列化该字段）。 */
export interface ApiResult<T> {
  code: number
  message: string
  data?: T
}

/** 分页结果。MyBatis-Plus 的分页结构。 */
export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface UserVO {
  id: number
  username: string
  nickname: string | null
  role: 'ADMIN' | 'USER'
}

export interface LoginResult {
  token: string
  user: UserVO
}

/** 分类树节点。children 永远存在（叶子节点为空数组）。 */
export interface CategoryNode {
  id: number
  name: string
  parentId: number
  level: number
  sort: number
  description: string | null
  children: CategoryNode[]
}

export type KnowledgeStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface KnowledgeListVO {
  id: number
  title: string
  summary: string | null
  status: KnowledgeStatus
  categoryId: number | null
  categoryName: string | null
  authorName: string | null
  viewCount: number
  updatedAt: string
}

export interface KnowledgeDetailVO {
  id: number
  title: string
  summary: string | null
  content: string | null
  contentType: string
  status: KnowledgeStatus
  categoryId: number | null
  categoryName: string | null
  authorId: number
  authorName: string | null
  viewCount: number
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface KnowledgeSaveRequest {
  title: string
  summary?: string
  content?: string
  contentType?: string
  categoryId?: number | null
  /** 按 id 指定标签；与 tagNames 只取其一，tagNames 优先 */
  tagIds?: number[]
  /** 按名称指定标签，不存在的自动创建。编辑器用这个 */
  tagNames?: string[]
}

export interface TagVO {
  id: number
  name: string
  itemCount: number
}

export interface SearchHit {
  id: number
  title: string
  /** 纯文本命中片段，不含 HTML。高亮由前端做 */
  snippet: string | null
  categoryId: number | null
  categoryName: string | null
  tags: string[]
  status: KnowledgeStatus
  updatedAt: string
  /** 量纲随关键词变化：中文为 TF-IDF，英文为固定权重。不要做数值假设 */
  score: number
}

export type ParseStatus = 'PENDING' | 'PARSING' | 'DONE' | 'FAILED'

export interface AttachmentVO {
  id: number
  itemId: number
  originalName: string
  mimeType: string | null
  sizeBytes: number
  parseStatus: ParseStatus
  parseError: string | null
  textPreview: string | null
  createdAt: string
}

export interface SourceRef {
  itemId: number
  title: string
  headingPath: string
  score: number
}

export interface QaMessage {
  role: 'user' | 'assistant'
  content: string
  sources: SourceRef[]
  at: string
}

/** 会话列表项。正文走 history 接口，列表不带。 */
export interface QaSession {
  chatId: string
  title: string
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface QaStatusVO {
  enabled: boolean
  indexedItems: number
  indexedChunks: number
  lastRebuildAt: string | null
}
