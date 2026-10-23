import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/api/types'

// Mock dependencies before importing the module
vi.mock('@/config/backend', () => ({
  BACKEND_BASE_URL: 'http://localhost:8084',
}))

vi.mock('@/utils/guard', () => ({
  getAuthToken: vi.fn(() => ''),
  clearAuthStorage: vi.fn(),
}))

vi.mock('@/utils/toast', () => ({
  showErrorToast: vi.fn(),
}))

describe('apiClient', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('creates axios instance with correct baseURL', async () => {
    const { apiClient } = await import('@/api/client')
    expect(apiClient.defaults.baseURL).toBe('http://localhost:8084')
    expect(apiClient.defaults.timeout).toBe(15000)
  })

  it('injects auth token in request interceptor', async () => {
    const { getAuthToken } = await import('@/utils/guard')
    vi.mocked(getAuthToken).mockReturnValue('test-token')

    const { apiClient } = await import('@/api/client')

    const config = { headers: {} } as InternalAxiosRequestConfig
    const interceptor = (apiClient.interceptors.request as any).handlers[0]
    const result = interceptor.fulfilled(config)

    expect(result.headers['Authorization']).toBe('Bearer test-token')
  })

  it('does not inject auth header when no token', async () => {
    const { getAuthToken } = await import('@/utils/guard')
    vi.mocked(getAuthToken).mockReturnValue('')

    const { apiClient } = await import('@/api/client')

    const config = { headers: {} } as InternalAxiosRequestConfig
    const interceptor = (apiClient.interceptors.request as any).handlers[0]
    const result = interceptor.fulfilled(config)

    expect(result.headers['Authorization']).toBeUndefined()
  })
})

describe('unwrap', () => {
  it('returns data when code is 0', async () => {
    const { unwrap } = await import('@/api/client')
    const response = {
      data: { code: 0, message: 'OK', data: { id: 1, name: 'test' } },
    } as AxiosResponse<ApiResponse<{ id: number; name: string }>>

    const result = await unwrap(Promise.resolve(response))
    expect(result).toEqual({ id: 1, name: 'test' })
  })

  it('throws when code is not 0', async () => {
    const { unwrap } = await import('@/api/client')
    const response = {
      data: { code: 400, message: 'Bad Request', data: null },
    } as AxiosResponse<ApiResponse<unknown>>

    await expect(unwrap(Promise.resolve(response))).rejects.toThrow('Bad Request')
  })

  it('includes error code in thrown error', async () => {
    const { unwrap } = await import('@/api/client')
    const response = {
      data: { code: 401, message: 'Unauthorized', data: null },
    } as AxiosResponse<ApiResponse<unknown>>

    try {
      await unwrap(Promise.resolve(response))
      expect.fail('should have thrown')
    } catch (err) {
      expect((err as any).code).toBe(401)
    }
  })
})
