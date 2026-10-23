<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { resetPassword, sendResetPasswordCode } from '@/api/modules/auth'

const router = useRouter()

const form = reactive({
  email: '',
  verifyCode: '',
  newPassword: '',
})

const state = reactive({
  loading: false,
  sendingCode: false,
  feedback: '',
  feedbackType: 'default' as 'default' | 'error' | 'success',
})

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

function setError(message: string) {
  state.feedback = message
  state.feedbackType = 'error'
}

function setSuccess(message: string) {
  state.feedback = message
  state.feedbackType = 'success'
}

async function createVerifyCode() {
  if (!form.email.trim()) {
    setError('请先输入注册邮箱')
    return
  }
  state.sendingCode = true
  try {
    await sendResetPasswordCode({
      email: form.email.trim(),
    })
    setSuccess('验证码已发送，请查收邮箱')
    startCooldown()
  } catch (error) {
    setError(error instanceof Error ? error.message : '验证码发送失败，请稍后重试')
  } finally {
    state.sendingCode = false
  }
}

async function onSubmit() {
  if (!form.email.trim()) {
    setError('请输入注册邮箱')
    return
  }
  if (!form.verifyCode.trim()) {
    setError('请输入验证码')
    return
  }
  if (form.newPassword.trim().length < 8) {
    setError('新密码长度至少8位')
    return
  }
  state.loading = true
  try {
    await resetPassword({
      email: form.email.trim(),
      verifyCode: form.verifyCode.trim(),
      newPassword: form.newPassword,
    })
    setSuccess('重置成功，正在跳转到登录页...')
    setTimeout(() => {
      router.push('/login')
    }, 800)
  } catch (error) {
    setError(error instanceof Error ? error.message : '重置失败，请稍后重试')
  } finally {
    state.loading = false
  }
}
</script>

<template>
  <div class="relative z-10 mb-8 text-center">
    <div
      class="fade-in-up delay-100 mb-4 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-rose-50 text-rose-500 opacity-0 shadow-sm transition-transform duration-300 hover:scale-105"
    >
      <svg class="h-8 w-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2h-1V9a5 5 0 00-10 0v2H6a2 2 0 00-2 2v6a2 2 0 002 2zm3-10V9a3 3 0 016 0v2H9z"
        />
      </svg>
    </div>
    <h1 class="fade-in-up delay-100 mb-2 text-2xl font-extrabold tracking-tight text-gray-900 opacity-0 sm:text-3xl">重置密码</h1>
    <p class="fade-in-up delay-100 text-sm font-medium text-gray-400 opacity-0 sm:text-base">输入注册邮箱，我们将向您发送重置链接</p>
  </div>

  <form class="fade-in-up delay-200 relative z-10 space-y-6 opacity-0" @submit.prevent="onSubmit">
    <div class="group">
      <label
        for="email"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        注册邮箱
      </label>
      <input
        id="email"
        v-model="form.email"
        type="email"
        class="w-full rounded-xl border border-transparent bg-gray-50 px-4 py-3.5 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
        placeholder="输入您注册时使用的邮箱"
      />
    </div>

    <div class="group">
      <label
        for="verify"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        验证码
      </label>
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          id="verify"
          v-model="form.verifyCode"
          type="text"
          class="min-w-0 flex-1 rounded-xl border border-transparent bg-gray-50 px-4 py-3.5 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
          placeholder="输入验证码"
        />
        <button
          type="button"
          :disabled="state.loading || state.sendingCode || cooldownSeconds > 0"
          class="w-full whitespace-nowrap rounded-xl bg-gray-900 px-4 py-3.5 text-sm font-bold text-white transition-all duration-300 hover:bg-black active:scale-95 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto"
          @click="createVerifyCode"
        >
          {{ state.sendingCode ? '发送中...' : cooldownSeconds > 0 ? `${cooldownSeconds}s 后重试` : '获取验证码' }}
        </button>
      </div>
    </div>

    <div class="group">
      <label
        for="newPassword"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        新密码
      </label>
      <input
        id="newPassword"
        v-model="form.newPassword"
        type="password"
        class="w-full rounded-xl border border-transparent bg-gray-50 px-4 py-3.5 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
        placeholder="请输入新密码（至少8位）"
      />
    </div>

    <p
      class="min-h-[20px] text-sm font-bold"
      :class="{
        'text-gray-500': state.feedbackType === 'default',
        'text-rose-500': state.feedbackType === 'error',
        'text-emerald-600': state.feedbackType === 'success',
      }"
    >
      {{ state.feedback }}
    </p>

    <button
      type="submit"
      :disabled="state.loading"
      class="w-full rounded-xl bg-rose-500 py-3.5 font-bold text-white transition-all duration-300 hover:-translate-y-0.5 hover:bg-rose-600 hover:shadow-lg hover:shadow-rose-500/30 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 active:scale-95 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0"
    >
      {{ state.loading ? '重置中...' : '立 即 重 置 密 码' }}
    </button>
  </form>

  <div class="fade-in-up delay-300 relative z-10 mt-8 text-center text-sm font-bold text-gray-400 opacity-0">
    想起密码了？
    <RouterLink to="/login" class="text-gray-800 transition-colors hover:text-rose-500">返回登录</RouterLink>
  </div>
</template>
