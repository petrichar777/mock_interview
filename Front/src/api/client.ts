import axios, { AxiosError, type AxiosResponse } from 'axios'
import type { ApiResponse } from '@/api/types'
import { BACKEND_BASE_URL } from '@/config/backend'
import { clearAuthStorage, getAuthToken } from '@/utils/guard'
import { showErrorToast } from '@/utils/toast'

const apiBaseUrl = BACKEND_BASE_URL
const authHeader = (import.meta.env.VITE_AUTH_HEADER as string) || 'Authorization'
const authScheme = (import.meta.env.VITE_AUTH_SCHEME as string) || 'Bearer'

// Prevent the 401 interceptor from clearing auth state during login flow.
// If getCurrentUser() fails during doLogin(), the token from the login
// response is still valid — we must not wipe it.
let loginInProgress = false
export function setLoginInProgress(v: boolean) {
  loginInProgress = v
}

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 15000,
})

apiClient.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token) {
    config.headers = config.headers ?? {}
    config.headers[authHeader] = authScheme ? `${authScheme} ${token}` : token
  }
  return config
})

apiClient.interceptors.response.use(
  (resp) => resp,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    if (status === 401) {
      // During login, a 401 from the login endpoint means bad credentials,
      // not an expired token. Preserve the backend's actual error message.
      if (loginInProgress) {
        const backendMessage = error.response?.data?.message
        const message = typeof backendMessage === 'string' && backendMessage.trim()
          ? backendMessage
          : '账号或密码错误'
        return Promise.reject(new Error(message))
      }
      clearAuthStorage()
      window.location.href = '/login'
      return Promise.reject(new Error('未登录，请重新登录'))
    }
    // For HTTP-level errors (network, 5xx, etc.), auto-show toast.
    // Business errors from backend responses are handled by page-level catch blocks.
    if (status === 500 || !status) {
      showErrorToast('服务器异常，请稍后重试', '请求失败', 'top-center')
    }
    const backendMessage = error.response?.data?.message
    const message = typeof backendMessage === 'string' && backendMessage.trim()
      ? backendMessage
      : (error.message || '请求失败')
    return Promise.reject(new Error(message))
  },
)

export type ApiError = Error & { code: number }

export async function unwrap<T>(promise: Promise<AxiosResponse<ApiResponse<T>>>): Promise<T> {
  const response = await promise
  const body = response.data
  if (body.code !== 0) {
    const err = new Error(body.message || '请求失败') as ApiError
    err.code = body.code
    throw err
  }
  return body.data
}
