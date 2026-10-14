import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import { tokenStorage } from '@/api/client'
import type { UserVO } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const token = ref<string | null>(tokenStorage.get())
  /** 是否已完成一次身份恢复，避免守卫在恢复完成前就判定为未登录 */
  const restored = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')

  async function login(username: string, password: string) {
    const result = await authApi.login(username, password)
    token.value = result.token
    tokenStorage.set(result.token)
    user.value = result.user
    restored.value = true
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      // 后端失败也要清本地状态，否则用户会卡在「看着像登录了但请求全失败」
      clear()
    }
  }

  /** 刷新页面后 token 还在但 user 丢了，用它补回来。 */
  async function restore() {
    if (restored.value) return
    if (!token.value) {
      restored.value = true
      return
    }
    try {
      user.value = await authApi.me()
    } catch {
      clear()
    } finally {
      restored.value = true
    }
  }

  function clear() {
    token.value = null
    user.value = null
    tokenStorage.clear()
  }

  return { user, token, isLoggedIn, isAdmin, displayName, restored, login, logout, restore, clear }
})
