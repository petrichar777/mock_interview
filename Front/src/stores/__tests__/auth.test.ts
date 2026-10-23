import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock all external dependencies
vi.mock('@/api/modules/auth', () => ({
  login: vi.fn(),
  getCurrentUser: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('@/utils/guard', () => ({
  getAuthToken: vi.fn(() => ''),
  setAuthToken: vi.fn(),
  getAuthRole: vi.fn(() => ''),
  setAuthRole: vi.fn(),
  clearAuthStorage: vi.fn(),
}))

import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/modules/auth'
import * as guard from '@/utils/guard'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('initial state', () => {
    it('is not authenticated by default', () => {
      const store = useAuthStore()
      expect(store.isAuthed).toBe(false)
      expect(store.isAdmin).toBe(false)
    })

    it('reads token from guard on init', () => {
      vi.mocked(guard.getAuthToken).mockReturnValue('existing-token')
      const store = useAuthStore()
      expect(store.isAuthed).toBe(true)
      expect(store.token).toBe('existing-token')
    })

    it('reads role from guard on init', () => {
      vi.mocked(guard.getAuthRole).mockReturnValue('ADMIN')
      const store = useAuthStore()
      expect(store.isAdmin).toBe(true)
      expect(store.role).toBe('ADMIN')
    })
  })

  describe('getters', () => {
    it('displayName returns username from profile', () => {
      const store = useAuthStore()
      store.userProfile = {
        id: 1,
        username: 'Alice',
        email: 'alice@test.com',
        role: 'USER',
        status: 1,
        continuousCheckinDays: 3,
        doneQuestionCount: 42,
      }
      expect(store.displayName).toBe('Alice')
    })

    it('displayEmail returns empty string when no profile', () => {
      const store = useAuthStore()
      expect(store.displayEmail).toBe('')
    })

    it('continuousCheckinDays defaults to 0', () => {
      const store = useAuthStore()
      expect(store.continuousCheckinDays).toBe(0)
    })

    it('doneQuestionCount defaults to 0', () => {
      const store = useAuthStore()
      expect(store.doneQuestionCount).toBe(0)
    })

    it('status defaults to 0', () => {
      const store = useAuthStore()
      expect(store.status).toBe(0)
    })

    it('avatarText returns first letter uppercase', () => {
      const store = useAuthStore()
      store.userProfile = {
        id: 1,
        username: 'bob',
        email: 'bob@test.com',
        role: 'USER',
        status: 1,
        continuousCheckinDays: 0,
        doneQuestionCount: 0,
      }
      expect(store.avatarText).toBe('B')
    })

    it('avatarText falls back to email when no username', () => {
      const store = useAuthStore()
      store.userProfile = {
        id: 1,
        username: '',
        email: 'charlie@test.com',
        role: 'USER',
        status: 1,
        continuousCheckinDays: 0,
        doneQuestionCount: 0,
      }
      expect(store.avatarText).toBe('C')
    })

    it('avatarText returns U when no name or email', () => {
      const store = useAuthStore()
      expect(store.avatarText).toBe('U')
    })
  })

  describe('doLogin', () => {
    it('calls login API, stores token and role, fetches profile', async () => {
      vi.mocked(authApi.login).mockResolvedValue({
        token: 'login-token',
        userId: 1,
        role: 'USER',
      })
      vi.mocked(authApi.getCurrentUser).mockResolvedValue({
        id: 1,
        username: 'testuser',
        email: 'test@test.com',
        role: 'USER',
        status: 1,
        continuousCheckinDays: 5,
        doneQuestionCount: 100,
      })

      const store = useAuthStore()
      await store.doLogin({ account: 'testuser', password: 'pass123' })

      expect(authApi.login).toHaveBeenCalledWith({ account: 'testuser', password: 'pass123' })
      expect(guard.setAuthToken).toHaveBeenCalledWith('login-token')
      expect(guard.setAuthRole).toHaveBeenCalledWith('USER')
      expect(store.token).toBe('login-token')
      expect(store.role).toBe('USER')
      expect(store.displayName).toBe('testuser')
    })
  })

  describe('doLogout', () => {
    it('calls logout API and clears all state', async () => {
      const store = useAuthStore()
      store.token = 'some-token'
      store.role = 'ADMIN'
      store.userProfile = { id: 1, username: 'admin', email: 'a@a.com', role: 'ADMIN', status: 1, continuousCheckinDays: 0, doneQuestionCount: 0 }

      await store.doLogout()

      expect(authApi.logout).toHaveBeenCalled()
      expect(guard.clearAuthStorage).toHaveBeenCalled()
      expect(store.token).toBe('')
      expect(store.role).toBe('')
      expect(store.userProfile).toBeNull()
    })

    it('clears state even if logout API fails', async () => {
      vi.mocked(authApi.logout).mockRejectedValue(new Error('Network error'))

      const store = useAuthStore()
      store.token = 'token'
      try {
        await store.doLogout()
      } catch {
        // doLogout uses try/finally without catch, error propagates
      }

      expect(store.token).toBe('')
      expect(store.userProfile).toBeNull()
      expect(guard.clearAuthStorage).toHaveBeenCalled()
    })
  })

  describe('fetchCurrentUserProfile', () => {
    it('does nothing when no token', async () => {
      vi.mocked(guard.getAuthToken).mockReturnValue('')
      const store = useAuthStore()
      await store.fetchCurrentUserProfile()
      expect(authApi.getCurrentUser).not.toHaveBeenCalled()
    })

    it('fetches and updates profile when token exists', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue({
        id: 2,
        username: 'Jane',
        email: 'jane@test.com',
        role: 'ADMIN',
        status: 1,
        continuousCheckinDays: 7,
        doneQuestionCount: 200,
      })

      const store = useAuthStore()
      store.token = 'token'
      await store.fetchCurrentUserProfile()

      expect(store.displayName).toBe('Jane')
      expect(store.role).toBe('ADMIN')
      expect(guard.setAuthRole).toHaveBeenCalledWith('ADMIN')
    })
  })

  describe('reset', () => {
    it('clears all state without API calls', () => {
      const store = useAuthStore()
      store.token = 'token'
      store.role = 'ADMIN'
      store.userProfile = { id: 1, username: 'x', email: 'x@x.com', role: 'ADMIN', status: 1, continuousCheckinDays: 0, doneQuestionCount: 0 }

      store.reset()

      expect(store.token).toBe('')
      expect(store.role).toBe('')
      expect(store.userProfile).toBeNull()
      expect(guard.clearAuthStorage).toHaveBeenCalled()
      expect(authApi.logout).not.toHaveBeenCalled()
    })
  })
})
