import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'browse', component: () => import('@/views/BrowseView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/detail/:id', name: 'detail', component: () => import('@/views/DetailView.vue') },
    { path: '/search', name: 'search', component: () => import('@/views/SearchView.vue') },
    { path: '/qa', name: 'qa', component: () => import('@/views/QaView.vue') },
    {
      path: '/editor/new',
      name: 'editor-new',
      component: () => import('@/views/EditorView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/editor/:id',
      name: 'editor-edit',
      component: () => import('@/views/EditorView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/mine',
      name: 'mine',
      component: () => import('@/views/MineView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/categories',
      name: 'categories',
      component: () => import('@/views/CategoryAdminView.vue'),
      meta: { requiresAuth: true }
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  // 首次进入时先恢复身份，否则刷新页面会被误判为未登录而跳走
  if (!auth.restored) {
    await auth.restore()
  }
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
