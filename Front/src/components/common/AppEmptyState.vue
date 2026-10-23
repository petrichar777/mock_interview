<script setup lang="ts">
withDefaults(
  defineProps<{
    message?: string
  }>(),
  {
    message: '暂无数据',
  },
)
</script>

<template>
  <div class="rounded-lg border border-dashed border-slate-300 bg-white p-6 text-sm text-slate-500">
    {{ message }}
  </div>
</template>
