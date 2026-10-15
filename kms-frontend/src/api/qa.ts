import { http } from './client'
import type { QaMessage, QaStatusVO, SourceRef } from '@/types/api'

export interface AskCallbacks {
  question: string
  chatId?: string | null
  onSources: (sources: SourceRef[]) => void
  onDelta: (text: string) => void
  onDone: (chatId: string) => void
  onError: (message: string) => void
  signal?: AbortSignal
}

/**
 * 流式提问。
 *
 * **必须用 fetch 而不是 EventSource**：EventSource 无法自定义请求头，
 * 带不上 Authorization；而且它只支持 GET，本接口是 POST
 * （问题可以很长，中文经 URL 编码后放不进 URL）。
 */
export async function ask(params: AskCallbacks): Promise<void> {
  const response = await http.raw('/api/qa/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ question: params.question, chatId: params.chatId ?? undefined }),
    signal: params.signal
  })

  if (!response.ok || !response.body) {
    params.onError('问答服务暂时不可用，请稍后重试')
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      // stream: true 不能省——中文是多字节字符，可能被切在两次 read() 之间，
      // 不加这个标志会解出乱码
      buffer += decoder.decode(value, { stream: true })

      const chunks = buffer.split('\n\n')
      buffer = chunks.pop() ?? ''
      for (const chunk of chunks) {
        dispatch(chunk, params)
      }

    }
    // 处理最后一个没有以空行收尾的事件
    if (buffer.trim()) dispatch(buffer, params)
  } catch (e) {
    // AbortError 是用户主动停止，不算错误
    if (e instanceof DOMException && e.name === 'AbortError') return
    params.onError('连接中断，请重试')
  }
}

function dispatch(chunk: string, params: AskCallbacks) {
  let event = ''
  const dataLines: string[] = []
  for (const line of chunk.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
  }
  if (!event || dataLines.length === 0) return

  // 只用 '\n' 拼接多行 data（SSE 规范），这里不再 trim 单个片段：
  // 内容的空白属于内容本身，交给 JSON 解析去处理
  const raw = dataLines.join('\n')

  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    // 不是 JSON 时按纯文本兜底，避免一个畸形事件中断整个流
    if (event === 'delta') params.onDelta(raw)
    return
  }

  switch (event) {
    case 'sources':
      params.onSources(Array.isArray(parsed) ? parsed : [])
      break
    case 'delta': {
      // 后端把片段包成 { text } 发送，换行与空格才能安全穿过 SSE
      const text =
        parsed && typeof parsed === 'object' && 'text' in parsed
          ? String((parsed as { text: unknown }).text ?? '')
          : typeof parsed === 'string'
            ? parsed
            : String(parsed ?? '')
      params.onDelta(text)
      break
    }
    case 'done':
      params.onDone((parsed as { chatId?: string })?.chatId ?? '')
      break
    case 'error':
      params.onError((parsed as { message?: string })?.message ?? '生成失败')
      break
  }
}

export const qaApi = {
  ask,
  status: () => http.get<QaStatusVO>('/api/qa/status'),
  reindex: () => http.post<QaStatusVO>('/api/qa/reindex'),
  history: (chatId: string) => http.get<QaMessage[]>(`/api/qa/history/${chatId}`),
  clearHistory: (chatId: string) => http.del<void>(`/api/qa/history/${chatId}`)
}
