import { http, unwrap } from './client'
import type { AttachmentVO } from '@/types/api'

export const attachmentApi = {
  listByItem: (itemId: number) => http.get<AttachmentVO[]>(`/api/attachments/item/${itemId}`),

  /**
   * 上传附件。
   *
   * 不能复用 http.post：它会对 body 做 JSON.stringify，而 FormData 必须原样交给浏览器，
   * 由浏览器自己生成带 boundary 的 Content-Type —— 手工设置反而会让后端解析失败。
   */
  upload: async (itemId: number, file: File) => {
    const form = new FormData()
    form.append('itemId', String(itemId))
    form.append('file', file)
    const response = await http.raw('/api/attachments/upload', { method: 'POST', body: form })
    return unwrap<AttachmentVO>(response)
  },

  /** 解析状态与文本预览。解析中由界面轮询此接口 */
  text: (id: number) => http.get<AttachmentVO>(`/api/attachments/${id}/text`),

  remove: (id: number) => http.del<void>(`/api/attachments/${id}`),

  /**
   * 下载附件。
   *
   * 必须走 fetch 拿 blob 再触发保存：`<a href>` 无法携带 Authorization 头，
   * 直接指向接口会因未登录而失败。
   */
  download: async (id: number, originalName: string) => {
    const response = await http.raw(`/api/attachments/${id}/download`)
    if (!response.ok) {
      throw new Error('下载失败，请重试')
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = originalName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    // 不释放会一直占着内存，大文件多次下载会明显吃内存
    URL.revokeObjectURL(url)
  }
}
