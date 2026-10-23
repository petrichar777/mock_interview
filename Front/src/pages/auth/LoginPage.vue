<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { sendLoginCode } from '@/api/modules/auth'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeTab = ref<'password' | 'code'>('password')

const passwordForm = reactive({
  account: '',
  password: '',
})
const showPassword = ref(false)
const passwordError = ref('')

const codeForm = reactive({
  email: '',
  code: '',
})
const codeError = ref('')
const sendingCode = ref(false)
const cooldownSeconds = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

function startCooldown() {
  cooldownSeconds.value = 60
  cooldownTimer = setInterval(() => {
    cooldownSeconds.value--
    if (cooldownSeconds.value <= 0) {
      if (cooldownTimer) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }
  }, 1000)
}

onBeforeUnmount(() => {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
})

async function handlePasswordLogin() {
  passwordError.value = ''
  try {
    await authStore.doLogin({
      account: passwordForm.account,
      password: passwordForm.password,
    })
    showActionSuccessToast('登录', 'top-center')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const defaultPath = authStore.role === 'ADMIN' ? '/admin/users' : '/app/dashboard'
    await router.push(redirect || defaultPath)
  } catch (error) {
    const message = error instanceof Error ? error.message : '登录失败，请稍后重试'
    passwordError.value = message
    showErrorToast(message, '登录失败', 'top-right')
  }
}

async function handleSendCode() {
  codeError.value = ''
  if (!codeForm.email.trim()) {
    codeError.value = '请先输入邮箱'
    return
  }
  sendingCode.value = true
  try {
    await sendLoginCode({ email: codeForm.email.trim() })
    showActionSuccessToast('验证码已发送，请查收邮箱', 'top-center')
    startCooldown()
  } catch (error) {
    const message = error instanceof Error ? error.message : '发送失败，请稍后重试'
    codeError.value = message
    showErrorToast(message, '发送失败', 'top-right')
  } finally {
    sendingCode.value = false
  }
}

async function handleCodeLogin() {
  codeError.value = ''
  if (!codeForm.email.trim()) {
    codeError.value = '请输入邮箱'
    return
  }
  if (!codeForm.code.trim()) {
    codeError.value = '请输入验证码'
    return
  }
  try {
    await authStore.doLoginByCode({
      email: codeForm.email.trim(),
      code: codeForm.code.trim(),
    })
    showActionSuccessToast('登录', 'top-center')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const defaultPath = authStore.role === 'ADMIN' ? '/admin/users' : '/app/dashboard'
    await router.push(redirect || defaultPath)
  } catch (error) {
    const message = error instanceof Error ? error.message : '登录失败，请稍后重试'
    codeError.value = message
    showErrorToast(message, '登录失败', 'top-right')
  }
}
</script>

<template>
  <div class="relative z-10 mb-8 text-center">
    <div
      class="fade-in-up delay-100 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-rose-50 text-rose-500 opacity-0 shadow-sm transition-transform duration-300 hover:scale-105"
    >
      <svg class="h-8 w-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
        />
      </svg>
    </div>
    <h1 class="fade-in-up delay-100 mb-2 text-2xl font-extrabold tracking-tight text-gray-900 opacity-0 sm:text-3xl">
      面试达人
    </h1>
    <p class="fade-in-up delay-100 text-sm font-medium text-gray-400 opacity-0 sm:text-base">登录以继续您的面试准备之旅</p>
  </div>

  <!-- Tab switcher -->
  <div class="relative z-10 mb-6 flex rounded-2xl bg-gray-100 p-1">
    <button
      type="button"
      class="flex-1 rounded-xl py-2.5 text-sm font-bold transition-all duration-200"
      :class="
        activeTab === 'password'
          ? 'bg-white text-gray-900 shadow-sm'
          : 'text-gray-500 hover:text-gray-700'
      "
      @click="activeTab = 'password'"
    >
      密码登录
    </button>
    <button
      type="button"
      class="flex-1 rounded-xl py-2.5 text-sm font-bold transition-all duration-200"
      :class="
        activeTab === 'code'
          ? 'bg-white text-gray-900 shadow-sm'
          : 'text-gray-500 hover:text-gray-700'
      "
      @click="activeTab = 'code'"
    >
      验证码登录
    </button>
  </div>

  <!-- Password form -->
  <form v-if="activeTab === 'password'" class="relative z-10 space-y-6" @submit.prevent="handlePasswordLogin">
    <div class="group">
      <label
        for="username"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        用户名/邮箱
      </label>
      <input
        id="username"
        v-model="passwordForm.account"
        type="text"
        class="w-full rounded-xl border border-transparent bg-gray-50 px-4 py-3.5 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
        placeholder="输入您的用户名"
      />
    </div>
    <div class="group">
      <label
        for="password"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        密码
      </label>
      <div class="relative">
        <input
          id="password"
          v-model="passwordForm.password"
          :type="showPassword ? 'text' : 'password'"
          class="w-full rounded-xl border border-transparent bg-gray-50 py-3.5 pl-4 pr-11 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
          placeholder="输入密码"
        />
        <button
          type="button"
          class="absolute right-3 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-lg text-gray-400 transition-colors hover:text-gray-600"
          @click="showPassword = !showPassword"
        >
          <svg v-show="!showPassword" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
          </svg>
          <svg v-show="showPassword" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243a9.97 9.97 0 01-5.858.908M19.071 4.929a10 10 0 00-14.142 0M19.071 19.071a10 10 0 01-14.142 0" />
          </svg>
        </button>
      </div>
    </div>
    <p v-if="passwordError" class="text-sm font-medium text-rose-500">{{ passwordError }}</p>
    <div class="flex items-center justify-end">
      <RouterLink to="/forgot-password" class="text-sm font-bold text-rose-500 transition-colors hover:text-rose-600">
        忘记密码？
      </RouterLink>
    </div>
    <button
      type="submit"
      :disabled="authStore.loading"
      class="mt-2 w-full rounded-xl bg-rose-500 py-3.5 font-bold text-white transition-all duration-300 hover:-translate-y-0.5 hover:bg-rose-600 hover:shadow-lg hover:shadow-rose-500/30 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 active:scale-95 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0"
    >
      {{ authStore.loading && activeTab === 'password' ? '登录中...' : '登 录' }}
    </button>
  </form>

  <!-- Code form -->
  <form v-if="activeTab === 'code'" class="relative z-10 space-y-6" @submit.prevent="handleCodeLogin">
    <div class="group">
      <label
        for="code-email"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        邮箱
      </label>
      <input
        id="code-email"
        v-model="codeForm.email"
        type="email"
        class="w-full rounded-xl border border-transparent bg-gray-50 px-4 py-3.5 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
        placeholder="输入您的注册邮箱"
      />
    </div>
    <div class="group">
      <label
        for="code-input"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        验证码
      </label>
      <div class="flex gap-3">
        <input
          id="code-input"
          v-model="codeForm.code"
          type="text"
          inputmode="numeric"
          maxlength="6"
          autocomplete="one-time-code"
          class="min-w-0 flex-1 rounded-xl border border-transparent bg-gray-50 px-4 py-3.5 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
          placeholder="输入 6 位验证码"
        />
        <button
          type="button"
          :disabled="sendingCode || cooldownSeconds > 0"
          class="shrink-0 rounded-xl bg-gray-900 px-4 py-3.5 text-sm font-bold text-white transition-all duration-300 hover:bg-black active:scale-95 disabled:cursor-not-allowed disabled:opacity-60"
          @click="handleSendCode"
        >
          <template v-if="sendingCode">发送中...</template>
          <template v-else-if="cooldownSeconds > 0">{{ cooldownSeconds }}s 后重试</template>
          <template v-else>获取验证码</template>
        </button>
      </div>
    </div>
    <p v-if="codeError" class="text-sm font-medium text-rose-500">{{ codeError }}</p>
    <div class="flex items-center justify-end">
      <RouterLink to="/forgot-password" class="text-sm font-bold text-rose-500 transition-colors hover:text-rose-600">
        忘记密码？
      </RouterLink>
    </div>
    <button
      type="submit"
      :disabled="authStore.loading"
      class="mt-2 w-full rounded-xl bg-rose-500 py-3.5 font-bold text-white transition-all duration-300 hover:-translate-y-0.5 hover:bg-rose-600 hover:shadow-lg hover:shadow-rose-500/30 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 active:scale-95 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0"
    >
      {{ authStore.loading && activeTab === 'code' ? '登录中...' : '登 录' }}
    </button>
  </form>

  <div class="fade-in-up delay-300 relative z-10 mt-8 text-center text-sm font-bold text-gray-400 opacity-0">
    还没有账号？
    <RouterLink to="/register" class="text-gray-800 transition-colors hover:text-rose-500">立即注册</RouterLink>
  </div>
</template>
