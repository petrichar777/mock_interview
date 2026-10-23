<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps<{
  showBackLink: boolean
}>()

const route = useRoute()
const navItems = [
  {
    to: '/admin/users',
    label: '用户管理',
    icon: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z',
  },
  {
    to: '/admin/categories',
    label: '分类与模块',
    icon: 'M4 6h16M4 12h16M4 18h7',
  },
  {
    to: '/admin/questions',
    label: '题库管理',
    icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01',
  },
]

function resolveActiveGroup(p: string) {
  if (p.startsWith('/admin/questions')) return '/admin/questions'
  if (p.startsWith('/admin/categories')) return '/admin/categories'
  return p
}

const activeGroup = computed(() => resolveActiveGroup(route.path))

function getNavClass(to: string) {
  if (activeGroup.value === to) {
    return 'bg-rose-50 text-rose-500 border border-rose-100 shadow-sm'
  }
  return 'text-gray-500 hover:text-rose-500 hover:bg-rose-50/50 border border-transparent hover:border-rose-50'
}
</script>

<template>
  <aside
    class="sidebar-scroll hidden h-screen w-64 flex-col border-r border-white/50 bg-white/80 text-gray-800 shadow-[4px_0_24px_rgba(0,0,0,0.02)] backdrop-blur-xl md:flex"
  >
    <div class="flex h-16 items-center border-b border-white bg-white/40 px-6">
      <div class="flex items-center gap-2 text-rose-500">
        <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
          />
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
        <span class="text-lg font-extrabold tracking-wide text-gray-800">Admin Panel</span>
      </div>
    </div>

    <nav class="flex-1 space-y-2 overflow-y-auto px-3 py-6">
      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        class="flex items-center rounded-2xl px-4 py-3 text-sm font-bold transition-all"
        :class="getNavClass(item.to)"
      >
        <svg class="mr-3 h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" :d="item.icon" />
        </svg>
        {{ item.label }}
      </RouterLink>
    </nav>

    <div v-if="props.showBackLink" class="border-t border-white bg-white/40 p-4">
      <RouterLink
        to="/app/dashboard"
        class="flex w-full items-center justify-center gap-2 rounded-xl border border-gray-100 bg-white px-4 py-2.5 text-sm font-bold text-gray-600 shadow-sm transition-all hover:border-gray-200 hover:bg-gray-50 hover:text-rose-500"
      >
        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
        </svg>
        返回用户端
      </RouterLink>
    </div>
  </aside>
</template>

<style scoped>
.sidebar-scroll::-webkit-scrollbar {
  width: 4px;
}

.sidebar-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
}

.sidebar-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}
</style>
