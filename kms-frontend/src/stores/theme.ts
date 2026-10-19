import { computed, ref } from 'vue'

/**
 * 明暗主题。
 *
 * 不用 pinia：主题是两个地方需要的全局状态（App.vue 的包配置 + 顶栏开关），
 * 引一个 store 目录只装这一个文件是过度设计；模块级 ref 就是全局单例。
 *
 * 持久化到 localStorage，下次打开保持。初始值跟系统偏好走——
 * 用户第一次进来时是深色系统的，给浅色反而不尊重。
 */
export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'kms.theme'

const mode = ref<ThemeMode>(readInitial())
apply(mode.value)

function readInitial(): ThemeMode {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function apply(value: ThemeMode) {
  // 挂到 <html> 上：CSS 变量定义在 :root，scoped 样式和 naive 内部样式都能读到
  document.documentElement.dataset.theme = value
}

export const isDark = computed(() => mode.value === 'dark')

export function toggleTheme() {
  mode.value = mode.value === 'dark' ? 'light' : 'dark'
  localStorage.setItem(STORAGE_KEY, mode.value)
  apply(mode.value)
}
