import { beforeEach, describe, expect, it } from 'vitest'
import { clearAuthStorage, getAuthRole, getAuthToken, setAuthRole, setAuthToken } from '@/utils/guard'

describe('guard (auth storage)', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('getAuthToken', () => {
    it('returns empty string when no token stored', () => {
      expect(getAuthToken()).toBe('')
    })

    it('returns stored token', () => {
      localStorage.setItem('im_token', 'test-token-123')
      expect(getAuthToken()).toBe('test-token-123')
    })
  })

  describe('setAuthToken', () => {
    it('stores token in localStorage', () => {
      setAuthToken('my-token')
      expect(localStorage.getItem('im_token')).toBe('my-token')
    })

    it('overwrites existing token', () => {
      setAuthToken('old')
      setAuthToken('new')
      expect(localStorage.getItem('im_token')).toBe('new')
    })
  })

  describe('getAuthRole', () => {
    it('returns empty string when no role stored', () => {
      expect(getAuthRole()).toBe('')
    })

    it('returns stored role', () => {
      localStorage.setItem('im_role', 'ADMIN')
      expect(getAuthRole()).toBe('ADMIN')
    })
  })

  describe('setAuthRole', () => {
    it('stores role in localStorage', () => {
      setAuthRole('USER')
      expect(localStorage.getItem('im_role')).toBe('USER')
    })
  })

  describe('clearAuthStorage', () => {
    it('removes token and role from localStorage', () => {
      setAuthToken('token')
      setAuthRole('ADMIN')
      clearAuthStorage()
      expect(localStorage.getItem('im_token')).toBeNull()
      expect(localStorage.getItem('im_role')).toBeNull()
    })

    it('does not throw when nothing to clear', () => {
      expect(() => clearAuthStorage()).not.toThrow()
    })
  })
})
