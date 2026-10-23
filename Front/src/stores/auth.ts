import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { setLoginInProgress } from '@/api/client'
import { getCurrentUser, login, loginByCode, logout, type CurrentUserResponse, type LoginByCodeRequest, type LoginRequest } from '@/api/modules/auth'
import type { Role } from '@/api/types'
import { clearAuthStorage, getAuthRole, getAuthToken, setAuthRole, setAuthToken } from '@/utils/guard'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getAuthToken())
  const role = ref<Role | ''>((getAuthRole() as Role) || '')
  const loading = ref(false)
  const profileLoading = ref(false)
  const userProfile = ref<CurrentUserResponse | null>(null)

  const isAuthed = computed(() => Boolean(token.value))
  const isAdmin = computed(() => role.value === 'ADMIN')
  const displayName = computed(() => userProfile.value?.username || '')
  const displayEmail = computed(() => userProfile.value?.email || '')
  const continuousCheckinDays = computed(() => userProfile.value?.continuousCheckinDays ?? 0)
  const doneQuestionCount = computed(() => userProfile.value?.doneQuestionCount ?? 0)
  const status = computed(() => userProfile.value?.status ?? 0)
  const avatarText = computed(() => {
    if (displayName.value) {
      return displayName.value.slice(0, 1).toUpperCase()
    }
    if (displayEmail.value) {
      return displayEmail.value.slice(0, 1).toUpperCase()
    }
    return 'U'
  })

  async function fetchCurrentUserProfile() {
    if (!token.value) {
      return
    }
    profileLoading.value = true
    try {
      const data = await getCurrentUser()
      userProfile.value = data
      role.value = data.role
      setAuthRole(data.role)
    } catch {
      // Profile fetch is non-fatal — token and role from login response are sufficient
      // Don't let a failed profile request break the login flow or guard checks
    } finally {
      profileLoading.value = false
    }
  }

  async function doLogin(payload: LoginRequest) {
    loading.value = true
    setLoginInProgress(true)
    try {
      const data = await login(payload)
      token.value = data.token
      role.value = data.role
      setAuthToken(data.token)
      setAuthRole(data.role)
      await fetchCurrentUserProfile()
    } finally {
      loading.value = false
      setLoginInProgress(false)
    }
  }

  async function doLoginByCode(payload: LoginByCodeRequest) {
    loading.value = true
    setLoginInProgress(true)
    try {
      const data = await loginByCode(payload)
      token.value = data.token
      role.value = data.role
      setAuthToken(data.token)
      setAuthRole(data.role)
      await fetchCurrentUserProfile()
    } finally {
      loading.value = false
      setLoginInProgress(false)
    }
  }

  async function doLogout() {
    try {
      await logout()
    } finally {
      token.value = ''
      role.value = ''
      userProfile.value = null
      clearAuthStorage()
    }
  }

  function reset() {
    token.value = ''
    role.value = ''
    userProfile.value = null
    clearAuthStorage()
  }

  return {
    token,
    role,
    loading,
    profileLoading,
    userProfile,
    isAuthed,
    isAdmin,
    displayName,
    displayEmail,
    continuousCheckinDays,
    doneQuestionCount,
    status,
    avatarText,
    fetchCurrentUserProfile,
    doLogin,
    doLoginByCode,
    doLogout,
    reset,
  }
})
