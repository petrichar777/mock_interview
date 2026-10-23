<script setup lang="ts">
import { computed } from 'vue'
import { closePopup, popupState } from '@/utils/popup'

const panelClass = computed(() => {
  if (popupState.tone === 'success') {
    return 'border-emerald-100'
  }
  return 'border-rose-100'
})

const iconWrapClass = computed(() => {
  if (popupState.tone === 'success') {
    return 'bg-emerald-50 text-emerald-500'
  }
  return 'bg-rose-50 text-rose-500'
})

const actionClass = computed(() => {
  if (popupState.tone === 'success') {
    return 'from-emerald-400 to-teal-400 hover:from-emerald-500 hover:to-teal-500 shadow-emerald-500/30'
  }
  return 'from-rose-400 to-orange-400 hover:from-rose-500 hover:to-orange-500 shadow-rose-500/30'
})
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
        v-if="popupState.visible"
        class="fixed inset-0 z-[9999] flex items-center justify-center bg-gray-900/30 px-4 backdrop-blur-sm"
        @click="closePopup"
      >
        <section
          class="w-full max-w-sm rounded-3xl border bg-white/95 p-6 shadow-[0_18px_45px_rgba(0,0,0,0.12)] backdrop-blur-xl"
          :class="panelClass"
          @click.stop
        >
          <div class="mb-4 flex items-start gap-4">
            <div class="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-2xl" :class="iconWrapClass">
              <svg v-if="popupState.tone === 'success'" class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
              </svg>
              <svg v-else class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 9v4m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4c-.77-1.33-2.69-1.33-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
              </svg>
            </div>
            <div class="min-w-0 flex-1">
              <h3 class="text-lg font-extrabold text-gray-900">{{ popupState.title }}</h3>
              <p class="mt-2 break-words text-sm font-medium text-gray-500">{{ popupState.message }}</p>
            </div>
          </div>

          <button
            type="button"
            class="w-full rounded-2xl bg-gradient-to-r py-3 font-bold text-white shadow-[0_10px_24px] transition-all duration-300 hover:-translate-y-0.5 active:scale-95"
            :class="actionClass"
            @click="closePopup"
          >
            {{ popupState.confirmText }}
          </button>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
