import '@/assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { installNaive } from './naive'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/client'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
installNaive(app)

// 未授权统一处理：清本地状态并跳登录页，各页面不再各自处理
setUnauthorizedHandler(() => {
  useAuthStore(pinia).clear()
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.push({ path: '/login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
