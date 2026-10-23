const TOKEN_KEY = 'im_token'
const ROLE_KEY = 'im_role'

export function getAuthToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setAuthToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getAuthRole(): string {
  return localStorage.getItem(ROLE_KEY) || ''
}

export function setAuthRole(role: string) {
  localStorage.setItem(ROLE_KEY, role)
}

export function clearAuthStorage() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ROLE_KEY)
}
