<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePracticeStore } from '@/stores/practice'
import { useVersionCheck } from '@/composables/useVersionCheck'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'
import ContactDeveloperModal from '@/components/common/ContactDeveloperModal.vue'
import AppFooter from '@/components/layout/AppFooter.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const practiceStore = usePracticeStore()
const profileOpen = ref(false)
const mobileMenuOpen = ref(false)
const contactModal = ref<InstanceType<typeof ContactDeveloperModal>>()
const { hasUpdate, applyUpdate } = useVersionCheck()

const navItems = [
  { to: '/app/dashboard', label: '首页', matchPrefix: '/app/dashboard' },
  { to: '/app/practice', label: '题库练习', matchPrefix: '/app/practice' },
  { to: '/app/interview/setup', label: 'AI 模拟面试', matchPrefix: '/app/interview' },
  { to: '/app/mistakes', label: '错题本', matchPrefix: '/app/mistakes' },
  { to: '/app/reports', label: '面试记录', matchPrefix: '/app/reports' },
  { to: '/app/resume-optimizer', label: '简历优化', matchPrefix: '/app/resume-optimizer' },
]

const activeNavPrefix = computed(() => {
  const current = route.path
  return navItems.find((item) => current.startsWith(item.matchPrefix))?.matchPrefix || ''
})
const isPracticeDetailPage = computed(() => route.name === 'app-practice-detail')
const isInterviewChatPage = computed(() => route.name === 'app-interview-chat')
const isMistakeMode = computed(() => route.query.mode === 'mistake')
const backTarget = computed(() => isMistakeMode.value ? '/app/mistakes' : '/app/practice')
const backLabel = computed(() => isMistakeMode.value ? '返回错题本' : '返回题库')
const currentModuleId = computed(() => String(route.params.moduleId || ''))
const currentModule = computed(() => practiceStore.modules.find((item) => item.id === currentModuleId.value))
const detailModuleTitle = computed(() => currentModule.value?.name || 'Java 核心基础')
const userName = computed(() => authStore.displayName || '用户')
const userEmail = computed(() => authStore.displayEmail || '暂无邮箱')
const userRoleText = computed(() => (authStore.role === 'ADMIN' ? '管理员' : '普通用户'))
const userAvatarUrl = computed(
  () => `https://ui-avatars.com/api/?name=${encodeURIComponent(userName.value)}&background=f43f5e&color=fff`,
)

function closeProfileMenu() {
  profileOpen.value = false
}

function toggleProfileMenu(event: MouseEvent) {
  event.stopPropagation()
  profileOpen.value = !profileOpen.value
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

function onMobileNavClick() {
  closeMobileMenu()
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

function goPracticeList() {
  router.push(backTarget.value)
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
  <div class="relative flex min-h-screen flex-col overflow-x-hidden bg-[#f8f9fa] text-gray-800">
    <div
      class="pointer-events-none fixed left-0 top-0 h-64 w-64 -translate-x-1/2 -translate-y-1/2 rounded-full bg-rose-200 opacity-20 blur-3xl"
    />
    <div
      class="pointer-events-none fixed bottom-0 left-0 h-80 w-80 -translate-x-1/3 translate-y-1/3 rounded-full bg-rose-400 opacity-20 blur-3xl"
    />
    <div
      class="pointer-events-none fixed right-0 top-1/4 h-64 w-64 translate-x-1/3 rounded-full bg-emerald-200 opacity-20 blur-3xl"
    />

    <div class="flex-1">

    <!-- 版本更新提醒 -->
    <Transition name="slide-down">
      <div
        v-if="hasUpdate"
        class="sticky top-0 z-[60] flex items-center justify-center gap-3 bg-rose-500 px-4 py-2.5 text-sm text-white shadow-lg"
      >
        <span>检测到新版本，建议刷新页面以获取最新功能</span>
        <button
          class="rounded-lg bg-white/20 px-3 py-1 text-sm font-medium transition-colors hover:bg-white/30"
          @click="applyUpdate"
        >
          立即刷新
        </button>
      </div>
    </Transition>

    <nav
      v-if="!isPracticeDetailPage && !isInterviewChatPage"
      class="sticky top-0 z-50 border-b border-gray-100 bg-white/80 backdrop-blur-md transition-all duration-300"
    >
      <div class="mx-auto h-16 max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="flex h-full justify-between">
          <div class="flex items-center">
            <button
              v-if="!isPracticeDetailPage && !isInterviewChatPage"
              type="button"
              class="mr-2 rounded-xl p-2 text-gray-500 transition-colors hover:bg-rose-50 hover:text-rose-500 md:hidden"
              @click="toggleMobileMenu"
            >
              <svg v-if="!mobileMenuOpen" class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
              <svg v-else class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
            <RouterLink
              to="/app/dashboard"
              class="mr-4 flex items-center gap-2 text-rose-500 transition-transform hover:scale-105 md:mr-8"
            >
              <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
                />
              </svg>
              <span class="text-base font-extrabold tracking-tight text-gray-900 sm:text-xl">面试达人</span>
            </RouterLink>
            <div class="hidden items-center space-x-1 md:flex">
              <RouterLink
                v-for="item in navItems"
                :key="item.to"
                :to="item.to"
                class="rounded-lg px-4 py-2 text-sm font-semibold transition-all"
                :class="
                  activeNavPrefix === item.matchPrefix
                    ? 'bg-rose-50 font-bold text-rose-500'
                    : 'text-gray-500 hover:bg-rose-50/50 hover:text-rose-500'
                "
              >
                {{ item.label }}
              </RouterLink>
              <RouterLink
                v-if="authStore.isAdmin"
                to="/admin/users"
                class="ml-2 rounded-lg px-4 py-2 text-sm font-bold text-orange-500 transition-all hover:bg-orange-50 hover:text-orange-600"
              >
                管理后台
              </RouterLink>
              <button
                type="button"
                class="ml-2 rounded-lg px-4 py-2 text-sm font-bold text-blue-500 transition-all hover:bg-blue-50 hover:text-blue-600"
                @click="contactModal?.open()"
              >
                联系开发者
              </button>
            </div>
          </div>
          <div class="flex items-center">
            <div class="relative">
              <button
                type="button"
                class="flex items-center gap-2 rounded-2xl border border-white bg-white/70 p-1 pr-2 shadow-sm transition-all hover:bg-white hover:shadow-md sm:gap-3 sm:p-1.5"
                @click="toggleProfileMenu"
              >
                <div class="hidden text-right sm:block">
                  <div class="text-sm font-extrabold text-gray-700">{{ userName }}</div>
                  <div class="text-xs font-bold text-gray-400">{{ userRoleText }}</div>
                </div>
                <div
                  class="h-8 w-8 overflow-hidden rounded-2xl border border-rose-200/50 bg-gradient-to-tr from-rose-400 to-orange-400 shadow-sm sm:h-10 sm:w-10"
                >
                  <img
                    :src="userAvatarUrl"
                    alt="avatar"
                    class="h-full w-full object-cover"
                  />
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
                    <span class="font-bold text-gray-600">身份</span>
                    <span class="font-extrabold text-rose-500">{{ userRoleText }}</span>
                  </div>
                  <div class="flex items-center justify-between rounded-xl bg-orange-50/70 px-3 py-2">
                    <span class="font-bold text-gray-600">连续学习</span>
                    <span class="font-extrabold text-orange-500">{{ authStore.continuousCheckinDays }} 天</span>
                  </div>
                  <div class="flex items-center justify-between rounded-xl bg-emerald-50/70 px-3 py-2">
                    <span class="font-bold text-gray-600">已刷题目</span>
                    <span class="font-extrabold text-emerald-500">{{ authStore.doneQuestionCount }} 题</span>
                  </div>
                </div>
                <button
                  type="button"
                  class="mt-3 w-full rounded-xl border border-gray-100 bg-white px-3 py-2 font-bold text-gray-600 transition-colors hover:bg-rose-50 hover:text-rose-500"
                  @click="onLogout"
                >
                  退出登录
                </button>
                <RouterLink
                  v-if="authStore.isAdmin"
                  to="/admin/users"
                  class="mt-2 block w-full rounded-xl border border-orange-200 bg-orange-50 px-3 py-2 text-center font-bold text-orange-500 transition-colors hover:bg-orange-100"
                >
                  进入管理后台
                </RouterLink>
              </div>
            </div>
          </div>
        </div>
      </div>
    </nav>

    <nav
      v-else-if="isPracticeDetailPage"
      class="sticky top-0 z-50 border-b border-white/50 bg-white/70 shadow-[0_4px_30px_rgba(0,0,0,0.03)] backdrop-blur-xl transition-all duration-300"
    >
      <div class="relative mx-auto h-14 max-w-4xl px-4 sm:px-6 lg:px-8">
        <div class="flex h-full items-center gap-4">
          <button
            type="button"
            class="flex items-center gap-2 rounded-xl border border-white bg-white/80 px-3 py-1.5 text-sm font-bold text-gray-500 shadow-[0_2px_10px_rgba(0,0,0,0.05)] transition-all hover:-translate-x-1 hover:bg-orange-50 hover:text-orange-500"
            @click="goPracticeList"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            {{ backLabel }}
          </button>
          <div class="h-5 w-px bg-gray-200" />
          <span class="text-base font-extrabold text-gray-800">{{ detailModuleTitle }}</span>
        </div>
      </div>
    </nav>

    <RouterView />

    </div>

    <AppFooter />

    <Teleport to="body">
      <Transition name="modal">
        <div v-if="mobileMenuOpen" class="fixed inset-0 z-[100] md:hidden">
          <div class="absolute inset-0 bg-black/30 backdrop-blur-sm" @click="closeMobileMenu" />
          <div class="absolute right-0 top-0 flex h-full w-72 flex-col bg-white/95 backdrop-blur-xl shadow-[0_16px_60px_rgba(0,0,0,0.12)]">
            <div class="flex items-center justify-between border-b border-gray-100 px-5 py-4">
              <span class="text-lg font-extrabold tracking-tight text-gray-900">面试达人</span>
              <button
                type="button"
                class="rounded-xl p-1.5 text-gray-400 transition-colors hover:bg-rose-50 hover:text-rose-500"
                @click="closeMobileMenu"
              >
                <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <nav class="flex-1 space-y-1 overflow-y-auto px-3 py-4">
              <RouterLink
                v-for="item in navItems"
                :key="item.to"
                :to="item.to"
                class="flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-bold transition-all"
                :class="
                  activeNavPrefix === item.matchPrefix
                    ? 'bg-rose-50 font-extrabold text-rose-500'
                    : 'text-gray-500 hover:bg-rose-50/50 hover:text-rose-500'
                "
                @click="onMobileNavClick"
              >
                {{ item.label }}
              </RouterLink>
              <RouterLink
                v-if="authStore.isAdmin"
                to="/admin/users"
                class="flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-bold text-orange-500 transition-all hover:bg-orange-50 hover:text-orange-600"
                @click="onMobileNavClick"
              >
                管理后台
              </RouterLink>
              <button
                type="button"
                class="flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-sm font-bold text-blue-500 transition-all hover:bg-blue-50 hover:text-blue-600"
                @click="contactModal?.open(); onMobileNavClick()"
              >
                联系开发者
              </button>
            </nav>

            <div class="border-t border-gray-100 p-4">
              <div class="mb-3 flex items-center gap-3">
                <div
                  class="flex h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-tr from-rose-400 to-orange-400 text-sm font-extrabold text-white shadow-sm"
                >
                  {{ authStore.avatarText }}
                </div>
                <div>
                  <div class="text-sm font-extrabold text-gray-800">{{ userName }}</div>
                  <div class="text-xs font-bold text-gray-400">{{ userRoleText }}</div>
                </div>
              </div>
              <button
                type="button"
                class="w-full rounded-xl border border-gray-100 bg-white px-3 py-2.5 text-sm font-bold text-gray-600 transition-colors hover:bg-rose-50 hover:text-rose-500"
                @click="onLogout"
              >
                退出登录
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <ContactDeveloperModal ref="contactModal" />
  </div>
</template>

<style scoped>
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}
.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-100%);
}
</style>
