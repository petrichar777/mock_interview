<script setup lang="ts">
import { computed } from 'vue'
import { closeToast, toastState } from '@/utils/toast'

const wrapperClass = computed(() => {
  if (toastState.placement === 'top-right') {
    return 'right-4 top-4 items-end'
  }
  return 'left-1/2 top-4 -translate-x-1/2 items-center'
})

const toneClass = computed(() => {
  if (toastState.tone === 'success') {
    return 'border-emerald-100 bg-emerald-50/90 text-emerald-700'
  }
  if (toastState.tone === 'warning') {
    return 'border-amber-100 bg-amber-50/90 text-amber-700'
  }
  return 'border-rose-100 bg-rose-50/90 text-rose-700'
})

const iconWrapClass = computed(() => {
  if (toastState.tone === 'success') {
    return 'bg-emerald-100 text-emerald-600'
  }
  if (toastState.tone === 'warning') {
    return 'bg-amber-100 text-amber-600'
  }
  return 'bg-rose-100 text-rose-600'
})
</script>

<template>
  <Teleport to="body">
    <div class="pointer-events-none fixed inset-0 z-[9998]">
      <div class="pointer-events-none absolute flex w-full px-4" :class="wrapperClass">
        <Transition
          enter-active-class="transition duration-300 ease-out"
          enter-from-class="-translate-y-3 opacity-0"
          enter-to-class="translate-y-0 opacity-100"
          leave-active-class="transition duration-200 ease-in"
          leave-from-class="translate-y-0 opacity-100"
          leave-to-class="-translate-y-2 opacity-0"
        >
          <section
            v-if="toastState.visible"
            class="pointer-events-auto mt-2 w-full max-w-md rounded-2xl border px-4 py-3 shadow-[0_10px_30px_rgba(0,0,0,0.12)] backdrop-blur-xl"
            :class="toneClass"
          >
            <div class="flex items-start gap-3">
              <div class="mt-0.5 flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-xl" :class="iconWrapClass">
                <svg v-if="toastState.tone === 'success'" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
                </svg>
                <svg v-else-if="toastState.tone === 'warning'" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 9v4m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
                </svg>
                <svg v-else class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 9v4m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
                </svg>
              </div>
              <div class="min-w-0 flex-1">
                <div class="text-sm font-extrabold">{{ toastState.title }}</div>
                <div class="mt-0.5 break-words text-sm font-semibold opacity-90">{{ toastState.message }}</div>
              </div>
              <button
                type="button"
                class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-white/70 hover:text-gray-600"
                @click="closeToast"
              >
                <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </section>
        </Transition>
      </div>
    </div>
  </Teleport>
</template>
