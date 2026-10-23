<script setup lang="ts">
import { useRouter } from 'vue-router'

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()

function completeOnboarding() {
  localStorage.setItem('onboarding_completed', 'true')
  emit('close')
}

function goPractice() {
  completeOnboarding()
  router.push('/app/practice')
}

function goInterview() {
  completeOnboarding()
  router.push('/app/interview/setup')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div class="fixed inset-0 z-[100] flex items-center justify-center bg-black/30 backdrop-blur-sm">
        <div class="relative mx-4 flex max-h-[90vh] w-full max-w-2xl flex-col overflow-y-auto rounded-3xl border border-white bg-white/95 p-6 shadow-[0_24px_60px_rgba(0,0,0,0.12)] backdrop-blur-xl sm:p-10">
          <!-- Welcome header -->
          <div class="mb-8 text-center">
            <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-tr from-rose-400 to-orange-400 shadow-lg shadow-rose-200">
              <svg class="h-8 w-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
            </div>
            <h2 class="text-2xl font-extrabold text-gray-900 sm:text-3xl">欢迎来到面试达人</h2>
            <p class="mt-3 text-xs font-medium leading-relaxed text-gray-500 sm:text-sm">
              一站式面试准备平台，涵盖<strong class="text-gray-700">模块化刷题</strong>、<strong class="text-gray-700">AI 模拟面试</strong>、<strong class="text-gray-700">错题回顾</strong>和<strong class="text-gray-700">面试记录复盘</strong>。
              <br />选择一种方式开始，系统会逐步引导你探索全部功能。
            </p>
          </div>

          <!-- Choice cards -->
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <!-- Practice card -->
            <button
              type="button"
              class="group flex flex-col items-center rounded-2xl border border-orange-100 bg-gradient-to-br from-orange-50 to-amber-50 p-6 text-center transition-all hover:-translate-y-1 hover:border-orange-200 hover:shadow-lg"
              @click="goPractice"
            >
              <div class="mb-3 flex h-14 w-14 items-center justify-center rounded-xl bg-orange-100 text-orange-500 transition-transform group-hover:scale-110">
                <svg class="h-7 w-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                </svg>
              </div>
              <h3 class="mb-1 text-lg font-extrabold text-gray-900">刷题打基础</h3>
              <p class="mb-4 text-sm font-medium leading-relaxed text-gray-500">
                按模块系统学习，夯实知识点，查漏补缺
              </p>
              <span class="inline-flex items-center gap-1.5 rounded-xl bg-white px-4 py-2 text-sm font-extrabold text-orange-500 shadow-sm transition-colors group-hover:bg-orange-500 group-hover:text-white">
                开始刷题
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </span>
            </button>

            <!-- Interview card -->
            <button
              type="button"
              class="group flex flex-col items-center rounded-2xl border border-rose-100 bg-gradient-to-br from-rose-50 to-pink-50 p-6 text-center transition-all hover:-translate-y-1 hover:border-rose-200 hover:shadow-lg"
              @click="goInterview"
            >
              <div class="mb-3 flex h-14 w-14 items-center justify-center rounded-xl bg-rose-100 text-rose-500 transition-transform group-hover:scale-110">
                <svg class="h-7 w-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                </svg>
              </div>
              <h3 class="mb-1 text-lg font-extrabold text-gray-900">模拟面试</h3>
              <p class="mb-4 text-sm font-medium leading-relaxed text-gray-500">
                AI 面试官一对一深度对话，真实还原大厂面试场景
              </p>
              <span class="inline-flex items-center gap-1.5 rounded-xl bg-white px-4 py-2 text-sm font-extrabold text-rose-500 shadow-sm transition-colors group-hover:bg-rose-500 group-hover:text-white">
                开始面试
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </span>
            </button>
          </div>

          <!-- Skip -->
          <div class="mt-6 text-center">
            <button
              type="button"
              class="text-sm font-bold text-gray-400 transition-colors hover:text-gray-600"
              @click="completeOnboarding"
            >
              以后再说，直接进入首页
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active {
  transition: all 0.35s ease-out;
}

.modal-leave-active {
  transition: all 0.2s ease-in;
}

.modal-enter-from {
  opacity: 0;
}

.modal-enter-from > :last-child {
  transform: scale(0.92) translateY(20px);
  opacity: 0;
}

.modal-leave-to {
  opacity: 0;
}

.modal-leave-to > :last-child {
  transform: scale(0.95) translateY(10px);
  opacity: 0;
}
</style>
