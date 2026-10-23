<script setup lang="ts">
const navItems = [
  { to: '/app/dashboard', label: '仪表盘' },
  { to: '/app/practice', label: '题库练习' },
  { to: '/app/mistakes', label: '错题本' },
  { to: '/app/interview/setup', label: 'AI 面试' },
]
</script>

<template>
  <aside class="w-64 border-r border-slate-200 bg-white p-4">
    <h1 class="mb-6 text-lg font-semibold text-slate-900">面试达人</h1>
    <nav class="space-y-1">
      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        class="block rounded-md px-3 py-2 text-sm text-slate-700 hover:bg-slate-100"
        active-class="bg-slate-100 text-slate-900 font-medium"
      >
        {{ item.label }}
      </RouterLink>
    </nav>
  </aside>
</template>
