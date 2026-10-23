<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminSidebar from '@/components/layout/AdminSidebar.vue'
import ContactDeveloperModal from '@/components/common/ContactDeveloperModal.vue'
import AppFooter from '@/components/layout/AppFooter.vue'
import { useAuthStore } from '@/stores/auth'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const profileOpen = ref(false)
const contactModal = ref<InstanceType<typeof ContactDeveloperModal>>()
const userName = computed(() => authStore.displayName || 'Admin')
const userEmail = computed(() => authStore.displayEmail || '暂无邮箱')
const userRoleText = computed(() => (authStore.role === 'ADMIN' ? '系统管理员' : '普通用户'))

const pageConfig = computed(() => {
  if (route.name === 'admin-users') {
    return { title: '用户管理', module: '用户管理', showBack: false }
  }
  if (route.name === 'admin-questions') {
    return { title: '题库管理', module: '题库管理', showBack: false }
  }
  if (route.name === 'admin-questions-import') {
    return { title: '批量导入题目', module: '批量导入', showBack: true }
  }
  if (route.name === 'admin-question-edit') {
    return { title: '添加 / 编辑题目', module: '题目编辑', showBack: true }
  }
  if (route.name === 'admin-questions-generate') {
    return { title: 'AI 智能生成', module: '题库管理', showBack: true }
  }
  if (route.name === 'admin-questions-drafts') {
    return { title: '草稿审核', module: '题库管理', showBack: true }
  }
  return { title: '管理后台', module: '管理后台', showBack: false }
})

const showSidebarBackLink = computed(() => route.name === 'admin-users' || route.name === 'admin-questions')

function closeProfileMenu() {
  profileOpen.value = false
}

function toggleProfileMenu(event: MouseEvent) {
  event.stopPropagation()
  profileOpen.value = !profileOpen.value
}

function goBack() {
  router.push('/admin/questions')
}

async function onLogout() {
  try {
    await authStore.doLogout()
    showActionSuccessToast('退出登录', 'top-center')
  } catch (error) {
    const message = error instanceof Error ? error.message : '退出登录失败，请稍后重试'
    showErrorToast(message, '退出登录失败', 'top-right')
  }
  await router.push('/login')
}

onMounted(() => {
  document.addEventListener('click', closeProfileMenu)
  if (authStore.isAuthed) {
    authStore.fetchCurrentUserProfile()
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeProfileMenu)
})
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-slate-50 text-gray-800">
    <AdminSidebar :show-back-link="showSidebarBackLink" />

    <div class="relative flex min-w-0 flex-1 flex-col overflow-hidden bg-slate-50">
      <div class="pointer-events-none fixed left-0 top-0 -z-10 h-full w-full overflow-hidden">
        <div
          class="animate-blob absolute left-[-10%] top-[-10%] h-[40%] w-[40%] rounded-full bg-rose-400 opacity-20 mix-blend-multiply blur-3xl filter"
        />
        <div
          class="animate-blob animation-delay-2000 absolute right-[-10%] top-[-10%] h-[40%] w-[40%] rounded-full bg-emerald-400 opacity-20 mix-blend-multiply blur-3xl filter"
        />
        <div
          class="animate-blob animation-delay-4000 absolute bottom-[-20%] left-[20%] h-[40%] w-[40%] rounded-full bg-orange-400 opacity-20 mix-blend-multiply blur-3xl filter"
        />
      </div>

      <header
        class="sticky top-0 z-40 flex h-16 flex-shrink-0 items-center justify-between border-b border-white/50 bg-white/70 px-4 shadow-[0_4px_30px_rgba(0,0,0,0.03)] backdrop-blur-xl sm:px-8"
      >
        <div class="flex items-center">
          <button
            v-if="pageConfig.showBack"
            type="button"
            class="mr-4 flex h-8 w-8 items-center justify-center rounded-full border border-white bg-white/80 text-gray-500 shadow-sm transition-all hover:-translate-x-1 hover:bg-orange-50 hover:text-orange-500"
            @click="goBack"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
          </button>
          <h1 class="text-lg font-extrabold text-gray-800 sm:text-xl">{{ pageConfig.title }}</h1>
        </div>

        <button
          type="button"
          class="mr-3 rounded-xl px-4 py-2 text-sm font-bold text-blue-500 transition-all hover:bg-blue-50 hover:text-blue-600"
          @click="contactModal?.open()"
        >
          联系开发者
        </button>
        <div class="relative">
          <button
            type="button"
            class="flex items-center gap-3 rounded-2xl border border-white bg-white/70 p-1.5 pr-2 shadow-sm transition-all hover:bg-white hover:shadow-md"
            @click="toggleProfileMenu"
          >
            <div class="hidden text-right sm:block">
              <div class="text-sm font-extrabold text-gray-700">{{ userName }}</div>
              <div class="text-xs font-bold text-gray-400">{{ userRoleText }}</div>
            </div>
            <div
              class="flex h-9 w-9 items-center justify-center rounded-xl border border-rose-200/50 bg-gradient-to-tr from-rose-400 to-orange-400 text-sm font-extrabold text-white shadow-sm"
            >
              {{ authStore.avatarText }}
            </div>
            <svg
              class="h-4 w-4 text-gray-400 transition-transform"
              :class="{ 'rotate-180': profileOpen }"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M19 9l-7 7-7-7" />
            </svg>
          </button>

          <div
            v-if="profileOpen"
            class="absolute right-0 z-50 mt-2 w-72 rounded-2xl border border-white bg-white/90 p-4 shadow-[0_16px_40px_rgba(0,0,0,0.08)] backdrop-blur-xl"
            @click.stop
          >
            <div class="flex items-center gap-3 border-b border-gray-100 pb-3">
              <div
                class="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-tr from-rose-400 to-orange-400 font-extrabold text-white shadow-sm"
              >
                {{ authStore.avatarText }}
              </div>
              <div>
                <div class="text-sm font-extrabold text-gray-800">{{ userName }}</div>
                <div class="text-xs font-bold text-gray-500">{{ userEmail }}</div>
              </div>
            </div>
            <div class="space-y-2 pt-3 text-sm">
              <div class="flex items-center justify-between rounded-xl bg-rose-50/70 px-3 py-2">
                <span class="font-bold text-gray-600">角色</span>
                <span class="font-extrabold text-rose-500">{{ userRoleText }}</span>
              </div>
              <div class="flex items-center justify-between rounded-xl bg-orange-50/70 px-3 py-2">
                <span class="font-bold text-gray-600">当前模块</span>
                <span class="font-extrabold text-orange-500">{{ pageConfig.module }}</span>
              </div>
            </div>
            <button
              type="button"
              class="mt-3 w-full rounded-xl border border-gray-100 bg-white px-3 py-2 font-bold text-gray-600 transition-colors hover:bg-rose-50 hover:text-rose-500"
              @click="onLogout"
            >
              退出登录
            </button>
          </div>
        </div>
      </header>

      <main class="relative z-0 flex-1 overflow-y-auto">
        <RouterView />
        <AppFooter />
      </main>
    </div>

    <ContactDeveloperModal ref="contactModal" />
  </div>
</template>

<style scoped>
.animate-blob {
  animation: blob 12s infinite;
}

.animation-delay-2000 {
  animation-delay: 2s;
}

.animation-delay-4000 {
  animation-delay: 4s;
}

@keyframes blob {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }

  33% {
    transform: translate(30px, -50px) scale(1.08);
  }

  66% {
    transform: translate(-20px, 20px) scale(0.95);
  }
}
</style>
