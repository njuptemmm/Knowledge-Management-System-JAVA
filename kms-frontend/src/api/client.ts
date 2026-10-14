import type { ApiResult } from '@/types/api'

/** 后端地址。8080 被本机其他服务占用，故用 8090。 */
export const BASE_URL = 'http://localhost:8090'

/** 业务失败。code 保留下来供调用方区分处理。 */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

const TOKEN_KEY = 'kms.token'

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY)
}

/**
 * 未授权回调。由 main.ts 注入 router 行为，
 * 避免 client 直接依赖 router 造成循环引用。
 */
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export type QueryParams = Record<string, unknown>

interface RequestOptions {
  params?: QueryParams
  body?: unknown
}

function buildUrl(path: string, params?: QueryParams): string {
  const url = new URL(BASE_URL + path)
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      // 空值不传：后端据此走默认逻辑（如 status 不传表示按 mine 决定可见范围）
      if (value === undefined || value === null || value === '') continue
      url.searchParams.set(key, String(value))
    }
  }
  return url.toString()
}

/**
 * 解析统一响应体。
 * 上传等需要手工走 fetch 的场景复用它，保证 code 判定逻辑只有一处。
 */
export async function unwrap<T>(response: Response): Promise<T> {
  // 404 与 500 是仅有的两个按 HTTP 状态判断的情形
  if (response.status === 404) throw new ApiError(1004, '接口不存在')
  if (response.status === 500) throw new ApiError(5000, '系统繁忙，请稍后重试')

  let payload: ApiResult<T>
  try {
    payload = await response.json()
  } catch {
    throw new ApiError(5000, '服务返回了无法解析的内容')
  }

  if (payload.code === 1002) {
    tokenStorage.clear()
    onUnauthorized()
    throw new ApiError(1002, payload.message || '登录已过期，请重新登录')
  }
  if (payload.code !== 0) {
    throw new ApiError(payload.code, payload.message || '操作失败')
  }
  return payload.data as T
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  const token = tokenStorage.get()
  // 不加 Bearer 前缀：后端不接受
  if (token) headers['Authorization'] = token
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(buildUrl(path, options.params), {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  })
  return unwrap<T>(response)
}

export const http = {
  get: <T>(path: string, options?: RequestOptions) => request<T>('GET', path, options),
  post: <T>(path: string, options?: RequestOptions) => request<T>('POST', path, options),
  put: <T>(path: string, options?: RequestOptions) => request<T>('PUT', path, options),
  del: <T>(path: string, options?: RequestOptions) => request<T>('DELETE', path, options),

  /**
   * 原始 Response。用于下载（要 blob）与 SSE（要 ReadableStream）。
   * 仍会自动带上 Authorization。
   */
  raw: (path: string, init?: RequestInit) => {
    const token = tokenStorage.get()
    return fetch(buildUrl(path), {
      ...init,
      headers: {
        ...(init?.headers ?? {}),
        ...(token ? { Authorization: token } : {})
      }
    })
  }
}

/** 把任意异常转成可展示的中文文案，避免各页面重复写分支。 */
export function describeError(e: unknown): string {
  if (e instanceof ApiError) return e.message
  // fetch 在网络不通时抛 TypeError，这是最常见的情况（后端没启动）
  if (e instanceof TypeError) return '无法连接服务器，请确认后端已启动'
  if (e instanceof Error && e.message) return e.message
  return '操作失败，请重试'
}
