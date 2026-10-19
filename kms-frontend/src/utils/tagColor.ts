/**
 * 标签确定性配色。
 *
 * 同一个标签名必须总是同一种颜色——用户靠颜色认标签，
 * 今天蓝明天红比没有颜色更糟。因此对名称哈希后取模，
 * 从一组护眼色里稳定选色。
 *
 * 用柔和低饱和色：标签是辅助信息，高饱和的彩色会把视觉焦点从标题抢走。
 */

interface TagColor {
  bg: string
  text: string
}

/** 亮色模式：浅底 + 深色文字，保证 WCAG AA 对比度 */
const LIGHT: TagColor[] = [
  { bg: '#e8f4ff', text: '#1a6cc7' },
  { bg: '#e6f8ef', text: '#18794e' },
  { bg: '#fdf0e5', text: '#b25e09' },
  { bg: '#f4edfa', text: '#7d3fa0' },
  { bg: '#fdebee', text: '#c22e49' },
  { bg: '#e6f4f4', text: '#0e7a7a' },
  { bg: '#eef3fb', text: '#3d5a9a' },
  { bg: '#fbf3e2', text: '#9a7400' }
]

/** 暗色模式：深底 + 亮色文字 */
const DARK: TagColor[] = [
  { bg: '#14273e', text: '#6ab0ff' },
  { bg: '#14301f', text: '#5ec98a' },
  { bg: '#342312', text: '#eda35c' },
  { bg: '#251a33', text: '#b989dd' },
  { bg: '#33191f', text: '#f07f96' },
  { bg: '#0f2e2e', text: '#52c9c9' },
  { bg: '#1c2740', text: '#8ba7e0' },
  { bg: '#2e2810', text: '#d4b148' }
]

function hash(text: string): number {
  let h = 0
  for (let i = 0; i < text.length; i++) {
    h = (h * 31 + text.charCodeAt(i)) | 0
  }
  return Math.abs(h)
}

export function tagColor(name: string, dark: boolean): TagColor {
  const palette = dark ? DARK : LIGHT
  return palette[hash(name) % palette.length]!
}

/** 传给 n-tag 的 :color 覆盖 */
export function tagColorStyle(name: string, dark: boolean): { color: string; textColor: string } {
  const c = tagColor(name, dark)
  return { color: c.bg, textColor: c.text }
}
