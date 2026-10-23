<script setup lang="ts">
withDefaults(
  defineProps<{
    message?: string
  }>(),
  {
    message: '请求失败，请稍后重试',
  },
)

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="space-y-3 rounded-lg border border-red-200 bg-red-50 p-6">
    <p class="text-sm text-red-700">{{ message }}</p>
    <button
      type="button"
      class="rounded-md bg-red-600 px-3 py-2 text-xs font-medium text-white hover:bg-red-700"
      @click="emit('retry')"
    >
      重试
    </button>
  </div>
</template>
