import { http } from './client'
import type { LoginResult, UserVO } from '@/types/api'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<LoginResult>('/api/auth/login', { body: { username, password } }),

  logout: () => http.post<void>('/api/auth/logout'),

  me: () => http.get<UserVO>('/api/auth/me')
}
