<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/modules/auth'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'

const router = useRouter()
const form = reactive({
  username: '',
  email: '',
  password: '',
})
const submitState = reactive({
  loading: false,
  error: '',
})

async function onSubmit() {
  submitState.error = ''
  const username = form.username.trim()
  const email = form.email.trim()
  if (!username) {
    submitState.error = '请输入用户名'
    return
  }
  if (!email) {
    submitState.error = '请输入邮箱'
    return
  }
  if (form.password.trim().length < 8) {
    submitState.error = '密码至少8位'
    return
  }
  submitState.loading = true
  try {
    await register({
      username,
      email,
      password: form.password,
    })
    showActionSuccessToast('注册', 'top-center')
    await router.push('/login')
  } catch (error) {
    const message = error instanceof Error ? error.message : '注册失败，请稍后重试'
    submitState.error = message
    showErrorToast(message, '注册失败', 'top-right')
  } finally {
    submitState.loading = false
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
          d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"
        />
      </svg>
    </div>
    <h1 class="fade-in-up delay-100 mb-2 text-2xl font-extrabold tracking-tight text-gray-900 opacity-0 sm:text-3xl">
      创建账号
    </h1>
    <p class="fade-in-up delay-100 text-sm font-medium text-gray-400 opacity-0 sm:text-base">加入面试达人，提升面试技能</p>
  </div>
  <form class="fade-in-up delay-200 relative z-10 space-y-6 opacity-0" @submit.prevent="onSubmit">
    <div class="group">
      <label
        for="username"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        用户名
      </label>
      <input
        id="username"
        v-model="form.username"
        type="text"
        class="w-full rounded-xl border border-transparent bg-gray-50 px-4 py-3.5 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
        placeholder="设置一个用户名"
      />
    </div>
    <div class="group">
      <label
        for="email"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        邮箱
      </label>
      <input
        id="email"
        v-model="form.email"
        type="email"
        class="w-full rounded-xl border border-transparent bg-gray-50 px-4 py-3.5 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
        placeholder="输入您的邮箱"
      />
    </div>
    <div class="group">
      <label
        for="password"
        class="mb-2 block text-sm font-bold text-gray-700 transition-colors group-focus-within:text-rose-500"
      >
        密码
      </label>
      <input
        id="password"
        v-model="form.password"
        type="password"
        class="w-full rounded-xl border border-transparent bg-gray-50 px-4 py-3.5 transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
        placeholder="设置密码（至少8位）"
      />
    </div>
    <p v-if="submitState.error" class="text-sm font-medium text-rose-500">{{ submitState.error }}</p>
    <button
      type="submit"
      :disabled="submitState.loading"
      class="mt-2 w-full rounded-xl bg-rose-500 py-3.5 font-bold text-white transition-all duration-300 hover:-translate-y-0.5 hover:bg-rose-600 hover:shadow-lg hover:shadow-rose-500/30 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 active:scale-95 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0"
    >
      {{ submitState.loading ? '注册中...' : '注 册' }}
    </button>
  </form>
  <div class="fade-in-up delay-300 relative z-10 mt-8 text-center text-sm font-bold text-gray-400 opacity-0">
    已有账号？
    <RouterLink to="/login" class="text-gray-800 transition-colors hover:text-rose-500">立即登录</RouterLink>
  </div>
</template>
