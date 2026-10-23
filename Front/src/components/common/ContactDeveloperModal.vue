<script setup lang="ts">
import { ref } from 'vue'

const visible = ref(false)
const phoneCopied = ref(false)
const emailCopied = ref(false)

function open() {
  visible.value = true
  phoneCopied.value = false
  emailCopied.value = false
}

function close() {
  visible.value = false
}

async function copyPhone() {
  await navigator.clipboard.writeText('18108008195')
  phoneCopied.value = true
  setTimeout(() => { phoneCopied.value = false }, 2000)
}

async function copyEmail() {
  await navigator.clipboard.writeText('745120634@qq.com')
  emailCopied.value = true
  setTimeout(() => { emailCopied.value = false }, 2000)
}

defineExpose({ open })
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="visible"
        class="fixed inset-0 z-[9999] flex items-center justify-center bg-gray-900/30 px-4 backdrop-blur-sm"
        @click="close"
      >
        <section
          class="w-full max-w-sm rounded-3xl border border-white bg-white/95 p-6 shadow-[0_18px_45px_rgba(0,0,0,0.12)] backdrop-blur-xl"
          @click.stop
        >
          <div class="mb-5 flex items-start gap-4">
            <div class="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-2xl bg-blue-50 text-blue-500">
              <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
                />
              </svg>
            </div>
            <div class="min-w-0 flex-1">
              <h3 class="text-lg font-extrabold text-gray-900">联系开发者</h3>
              <p class="mt-1 text-sm font-medium text-gray-500">
                如果你在使用过程中遇到任何 Bug、闪退、数据错误，或对功能有建议，欢迎随时联系我。
              </p>
            </div>
          </div>

          <div class="space-y-3">
            <div class="flex items-center gap-3 rounded-2xl bg-slate-50 px-4 py-3">
              <svg class="h-5 w-5 flex-shrink-0 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z"
                />
              </svg>
              <span class="flex-1 text-base font-bold tracking-wide text-gray-800">18108008195</span>
              <button
                type="button"
                class="flex-shrink-0 rounded-xl px-3 py-1.5 text-xs font-bold transition-all"
                :class="
                  phoneCopied
                    ? 'bg-emerald-100 text-emerald-600'
                    : 'bg-white text-gray-500 hover:bg-blue-50 hover:text-blue-500'
                "
                @click="copyPhone"
              >
                {{ phoneCopied ? '已复制' : '复制' }}
              </button>
            </div>

            <div class="flex items-center gap-3 rounded-2xl bg-slate-50 px-4 py-3">
              <svg class="h-5 w-5 flex-shrink-0 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
                />
              </svg>
              <span class="flex-1 text-sm font-bold text-gray-800">745120634@qq.com</span>
              <button
                type="button"
                class="flex-shrink-0 rounded-xl px-3 py-1.5 text-xs font-bold transition-all"
                :class="
                  emailCopied
                    ? 'bg-emerald-100 text-emerald-600'
                    : 'bg-white text-gray-500 hover:bg-blue-50 hover:text-blue-500'
                "
                @click="copyEmail"
              >
                {{ emailCopied ? '已复制' : '复制' }}
              </button>
            </div>

            <p class="text-center text-xs font-medium text-gray-400">
              手机号与微信同号，添加时请备注"面试达人反馈"
            </p>
          </div>

          <button
            type="button"
            class="mt-5 w-full rounded-2xl bg-gradient-to-r from-blue-400 to-indigo-400 py-3 font-bold text-white shadow-[0_10px_24px] shadow-blue-500/30 transition-all duration-300 hover:-translate-y-0.5 active:scale-95"
            @click="close"
          >
            知道了
          </button>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
