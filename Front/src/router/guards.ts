import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export function setupRouterGuards(router: Router) {
  router.beforeEach(async (to, _from) => {
    // Lazily access store — Pinia is already installed before router
    const authStore = useAuthStore()

    // If visiting a guarded page with no token, redirect to login
    const requiresAuth = to.matched.some((r) => r.meta.requiresAuth === true)
    if (requiresAuth && !authStore.isAuthed) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    // If requires admin role, check it
    const requiresAdmin = to.matched.some((r) => r.meta.requiresAdmin === true)
    if (requiresAdmin) {
      // Fetch profile if not loaded yet (page refresh scenario)
      if (!authStore.userProfile) {
        await authStore.fetchCurrentUserProfile()
      }
      if (!authStore.isAdmin) {
        return { path: '/app/dashboard' }
      }
    }

    // If authed user visits guest-only page (login, register), redirect to dashboard
    const guestOnly = to.matched.some((r) => r.meta.guestOnly === true)
    if (guestOnly && authStore.isAuthed) {
      if (authStore.isAdmin) {
        return { path: '/admin/users' }
      }
      return { path: '/app/dashboard' }
    }

    return true
  })
}
