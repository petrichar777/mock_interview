<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<template>
  <header class="border-b border-slate-200 bg-white px-4 py-3 md:hidden">
    <h2 class="text-base font-semibold text-slate-900">{{ title }}</h2>
  </header>
</template>
