import { apiClient, unwrap } from '@/api/client'
import type { Role } from '@/api/types'

export type LoginRequest = {
  account: string
  password: string
}

export type RegisterRequest = {
  username: string
  email: string
  password: string
}

export type LoginResponse = {
  token: string
  userId: number
  role: Role
}

export type CurrentUserResponse = {
  id: number
  username: string
  email: string
  role: Role
  status: number
  continuousCheckinDays: number
  doneQuestionCount: number
}

export function login(body: LoginRequest) {
  return unwrap<LoginResponse>(apiClient.post('/api/auth/login', body))
}

export function getCurrentUser() {
  return unwrap<CurrentUserResponse>(apiClient.get('/api/users/me'))
}

export function register(body: RegisterRequest) {
  return unwrap<void>(apiClient.post('/api/auth/register', body))
}

export type ResetPasswordCodeRequest = {
  email: string
}

export type ResetPasswordRequest = {
  email: string
  verifyCode: string
  newPassword: string
}

export function sendResetPasswordCode(body: ResetPasswordCodeRequest) {
  return unwrap<void>(apiClient.post('/api/auth/password/reset-code', body))
}

export function resetPassword(body: ResetPasswordRequest) {
  return unwrap<void>(apiClient.post('/api/auth/password/reset', body))
}

export type SendLoginCodeRequest = {
  email: string
}

export type LoginByCodeRequest = {
  email: string
  code: string
}

export function sendLoginCode(body: SendLoginCodeRequest) {
  return unwrap<void>(apiClient.post('/api/auth/login/code', body))
}

export function loginByCode(body: LoginByCodeRequest) {
  return unwrap<LoginResponse>(apiClient.post('/api/auth/login/by-code', body))
}

export function logout() {
  return unwrap<void>(apiClient.post('/api/auth/logout'))
}
