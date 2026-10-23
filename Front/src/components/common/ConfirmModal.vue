<script setup lang="ts">
interface Props {
  open: boolean
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  confirmText: '确认删除',
  cancelText: '取消',
  loading: false,
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm px-4"
        @click.self="emit('cancel')"
      >
        <div class="w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl">
          <div class="mb-5 flex items-start gap-3">
            <div class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-rose-100">
              <svg class="h-5 w-5 text-rose-500" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4.5c-.77-.833-2.694-.833-3.464 0L3.34 16.5c-.77.833.192 2.5 1.732 2.5z" />
              </svg>
            </div>
            <div class="flex-1">
              <h3 class="text-base font-extrabold text-gray-900">{{ title }}</h3>
              <p class="mt-1 text-sm leading-relaxed text-gray-500">{{ message }}</p>
            </div>
          </div>
          <div class="flex gap-3">
            <button
              class="flex-1 rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-bold text-gray-600 transition-all hover:bg-gray-50"
              :disabled="loading"
              @click="emit('cancel')"
            >
              {{ cancelText }}
            </button>
            <button
              class="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-rose-500 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition-all hover:bg-rose-600 disabled:opacity-50"
              :disabled="loading"
              @click="emit('confirm')"
            >
              <span
                v-if="loading"
                class="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/30 border-t-white"
              />
              {{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active :deep(> div),
.modal-leave-active :deep(> div) {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-from :deep(> div) {
  transform: scale(0.95) translateY(8px);
  opacity: 0;
}
.modal-leave-to :deep(> div) {
  transform: scale(0.95) translateY(8px);
  opacity: 0;
}
</style>
