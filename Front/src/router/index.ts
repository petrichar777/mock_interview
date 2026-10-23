import { createRouter, createWebHistory } from 'vue-router'
import { setupRouterGuards } from '@/router/guards'
import { authRoutes } from '@/router/routes/auth'
import { appRoutes } from '@/router/routes/app'
import { adminRoutes } from '@/router/routes/admin'
import { APP_NAME } from '@/config/app'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior() {
    return { top: 0 }
  },
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    ...authRoutes,
    ...appRoutes,
    ...adminRoutes,
    {
      path: '/:pathMatch(.*)*',
      redirect: '/login',
    },
  ],
})

router.afterEach((to) => {
  const pageTitle = to.meta.title as string | undefined
  document.title = pageTitle ? `${pageTitle} - ${APP_NAME}` : APP_NAME
})

setupRouterGuards(router)

export default router
