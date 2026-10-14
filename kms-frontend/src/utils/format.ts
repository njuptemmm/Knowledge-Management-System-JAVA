import type { KnowledgeStatus, ParseStatus } from '@/types/api'

/** 状态中文名。界面各处共用，避免出现「草稿」「DRAFT」混用。 */
export const STATUS_LABEL: Record<KnowledgeStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档'
}

/** naive-ui 的类型色。草稿灰、已发布绿、已归档橙。 */
export const STATUS_TYPE: Record<KnowledgeStatus, 'default' | 'success' | 'warning'> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  ARCHIVED: 'warning'
}

export const PARSE_LABEL: Record<ParseStatus, string> = {
  PENDING: '等待解析',
  PARSING: '解析中',
  DONE: '解析完成',
  FAILED: '解析失败'
}

export const PARSE_TYPE: Record<ParseStatus, 'default' | 'info' | 'success' | 'error'> = {
  PENDING: 'default',
  PARSING: 'info',
  DONE: 'success',
  FAILED: 'error'
}

/** 后端返回 ISO 时间，展示时截到分钟即可。 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

export function formatSize(bytes: number | null | undefined): string {
  if (!bytes || bytes < 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
