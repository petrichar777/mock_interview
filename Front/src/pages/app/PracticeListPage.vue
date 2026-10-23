<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppEmptyState from '@/components/common/AppEmptyState.vue'
import AppErrorState from '@/components/common/AppErrorState.vue'
import AppLoading from '@/components/common/AppLoading.vue'
import { usePracticeStore } from '@/stores/practice'
import { formatPercent } from '@/utils/format'

const practiceStore = usePracticeStore()
const activeCategory = ref<string>('all')

const directionButtons = computed(() => {
  if (practiceStore.directions.length > 0) {
    return practiceStore.directions
  }
  return []
})
const activeDirectionCode = ref<string | null>(null)
const categoryButtons = computed(() => {
  if (!activeDirectionCode.value) {
    return [{ directionId: null as number | null, code: 'all' as const, name: '全部分类', active: true }]
  }
  const cats = practiceStore.categories.length > 0
    ? practiceStore.categories
    : [
        { directionId: 1, code: 'backend' as const, name: '后端', active: activeCategory.value === 'backend' },
        { directionId: 1, code: 'frontend' as const, name: '前端', active: activeCategory.value === 'frontend' },
        { directionId: 1, code: 'algorithm' as const, name: '算法', active: activeCategory.value === 'algorithm' },
      ]
  return cats.filter((c) => c.directionId !== null && practiceStore.directions.find((d) => d.code === activeDirectionCode.value)?.id === c.directionId)
})

const cardThemes = [
  {
    container: 'from-orange-50/50',
    bar: 'from-orange-400 to-amber-400',
    iconBg: 'bg-orange-50',
    iconColor: 'text-orange-400',
    hoverTitle: 'group-hover:text-orange-500',
    buttonClass:
      'bg-orange-50 text-orange-600 border border-orange-100 hover:bg-orange-100 shadow-sm transform active:scale-95',
    progressTrack: 'bg-gradient-to-r from-orange-400 to-amber-400',
    progressText: 'text-orange-500',
    completeText: 'text-emerald-500',
    completeButton: 'hover:text-orange-500',
  },
  {
    container: 'from-rose-50/50',
    bar: 'from-rose-400 to-pink-400',
    iconBg: 'bg-rose-50',
    iconColor: 'text-rose-400',
    hoverTitle: 'group-hover:text-rose-500',
    buttonClass:
      'bg-rose-50 text-rose-600 border border-rose-100 hover:bg-rose-100 shadow-sm transform active:scale-95',
    progressTrack: 'bg-gradient-to-r from-rose-400 to-pink-400',
    progressText: 'text-rose-500',
    completeText: 'text-emerald-500',
    completeButton: 'hover:text-rose-500',
  },
  {
    container: 'from-emerald-50/50',
    bar: 'from-emerald-400 to-teal-400',
    iconBg: 'bg-emerald-50',
    iconColor: 'text-emerald-400',
    hoverTitle: 'group-hover:text-emerald-500',
    buttonClass:
      'bg-emerald-50 text-emerald-600 border border-emerald-100 hover:bg-emerald-100 shadow-sm transform active:scale-95',
    progressTrack: 'bg-emerald-400',
    progressText: 'text-emerald-500',
    completeText: 'text-emerald-500',
    completeButton: 'hover:text-emerald-500',
  },
  {
    container: 'from-violet-50/50',
    bar: 'from-violet-400 to-indigo-400',
    iconBg: 'bg-violet-50',
    iconColor: 'text-violet-500',
    hoverTitle: 'group-hover:text-violet-500',
    buttonClass:
      'bg-violet-50 text-violet-600 border border-violet-100 hover:bg-violet-100 shadow-sm transform active:scale-95',
    progressTrack: 'bg-gradient-to-r from-violet-400 to-indigo-400',
    progressText: 'text-violet-500',
    completeText: 'text-emerald-500',
    completeButton: 'hover:text-violet-500',
  },
  {
    container: 'from-cyan-50/60',
    bar: 'from-cyan-400 to-sky-400',
    iconBg: 'bg-cyan-50',
    iconColor: 'text-cyan-500',
    hoverTitle: 'group-hover:text-cyan-500',
    buttonClass:
      'bg-cyan-50 text-cyan-600 border border-cyan-100 hover:bg-cyan-100 shadow-sm transform active:scale-95',
    progressTrack: 'bg-gradient-to-r from-cyan-400 to-sky-400',
    progressText: 'text-cyan-500',
    completeText: 'text-emerald-500',
    completeButton: 'hover:text-cyan-500',
  },
]

function isFrontendModule(name: string) {
  return /vue|javascript|前端|react|css|html/i.test(name)
}

function isAlgorithmModule(name: string) {
  return /算法|双指针|树|图|二分|动态规划|滑动窗口/i.test(name)
}

const filteredModules = computed(() => practiceStore.modules)

function getTheme(index: number) {
  return cardThemes[index % cardThemes.length]
}

function getCategory(name: string) {
  if (isAlgorithmModule(name)) {
    return '算法'
  }
  return isFrontendModule(name) ? '前端' : '后端'
}

function getCategoryDisplay(item: { categoryName?: string; categoryCode?: string; name: string }) {
  if (item.categoryName?.trim()) {
    return item.categoryName
  }
  if (item.categoryCode === 'algorithm') {
    return '算法'
  }
  if (item.categoryCode === 'frontend') {
    return '前端'
  }
  if (item.categoryCode === 'backend') {
    return '后端'
  }
  return getCategory(item.name)
}

function getIconPath(name: string) {
  if (isAlgorithmModule(name)) {
    return 'M3 15a4 4 0 014-4h10a4 4 0 014 4M8 15v1a4 4 0 008 0v-1M9 7h6M12 4v6'
  }
  if (isFrontendModule(name)) {
    return 'M13 10V3L4 14h7v7l9-11h-7z'
  }
  if (/spring|框架|原理/i.test(name)) {
    return 'M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z'
  }
  return 'M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01'
}

function getModuleDescription(item: { name: string; description?: string }) {
  if (item.description?.trim()) {
    return item.description
  }
  const { name } = item
  if (isAlgorithmModule(name)) {
    return '覆盖数组、双指针、树图等高频题型，强化思路表达、复杂度分析与边界处理。'
  }
  if (isFrontendModule(name)) {
    return '闭包、原型链、事件循环、Promise、工程化能力等前端核心面试考点。'
  }
  if (/spring|框架|原理/i.test(name)) {
    return 'IOC、AOP、自动装配、事务传播等进阶知识点，面向中高级后端面试。'
  }
  return '包含面向对象、集合框架、异常处理、并发基础等后端高频面试题。'
}

function getPracticeLink(moduleId: string, name: string) {
  if (isAlgorithmModule(name)) {
    return `/app/practice/${moduleId}?category=algorithm`
  }
  return `/app/practice/${moduleId}`
}

function getActionText(item: { done: number; total: number; name: string; ctaText?: string }) {
  if (item.ctaText) {
    return item.ctaText
  }
  const { done, total, name } = item
  if (isAlgorithmModule(name) && done === 0) {
    return '开始算法练习'
  }
  if (done === 0) {
    return '开始练习'
  }
  if (done === total && total > 0) {
    return '重新练习'
  }
  return '继续练习'
}

function onSelectDirection(directionCode: string | null) {
  activeDirectionCode.value = directionCode
  activeCategory.value = 'all'
  if (directionCode) {
    const dirCats = practiceStore.categories.filter((c) => {
      const d = practiceStore.directions.find((dd) => dd.code === directionCode)
      return d && c.directionId === d.id
    })
    if (dirCats.length > 0) {
      activeCategory.value = dirCats[0].code
    }
  }
  practiceStore.fetchModules(activeCategory.value)
}

async function onSelectCategory(categoryCode: string) {
  activeCategory.value = categoryCode
  await practiceStore.fetchModules(categoryCode)
}

onMounted(async () => {
  await practiceStore.fetchModules('all')
  if (practiceStore.directions.length > 0) {
    activeDirectionCode.value = practiceStore.directions[0].code
    const dirCats = practiceStore.categories.filter(
      (c) => c.directionId === practiceStore.directions[0].id,
    )
    activeCategory.value = dirCats.length > 0 ? dirCats[0].code : 'all'
    await practiceStore.fetchModules(activeCategory.value)
  }
})
</script>

<template>
  <main class="relative z-10 mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-12 lg:px-8">
    <div class="fade-in-up mb-10 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
      <div>
        <h1 class="mb-2 text-3xl font-extrabold text-gray-900">选择题库模块</h1>
        <p class="font-medium text-gray-400">支持后端 / 前端 / 算法，题型覆盖单选、多选、判断</p>
      </div>
      <div class="flex flex-col gap-2">
        <div v-if="directionButtons.length > 0" class="flex flex-wrap gap-2 rounded-xl border border-gray-100 bg-white p-1.5 shadow-sm">
          <button
            v-for="d in directionButtons"
            :key="d.code"
            type="button"
            class="rounded-lg px-5 py-2 text-sm font-bold transition-colors"
            :class="
              activeDirectionCode === d.code
                ? 'bg-emerald-50 text-emerald-600 shadow-sm'
                : 'bg-transparent text-gray-600 hover:bg-gray-50 hover:text-emerald-500'
            "
            @click="onSelectDirection(d.code)"
          >
            {{ d.name }}
          </button>
        </div>
        <div class="flex flex-wrap gap-2 rounded-xl border border-gray-100 bg-white p-1.5 shadow-sm">
          <button
            v-for="item in categoryButtons"
            :key="item.code"
            type="button"
            class="rounded-lg px-5 py-2 text-sm font-bold transition-colors"
            :class="
              activeCategory === item.code
                ? 'bg-rose-50 text-rose-500 shadow-sm'
                : 'bg-transparent text-gray-600 hover:bg-gray-50 hover:text-rose-500'
            "
            @click="onSelectCategory(item.code)"
          >
            {{ item.name }}
          </button>
        </div>
      </div>
    </div>

    <div class="fade-in-up delay-100 mb-8 rounded-2xl border border-orange-100 bg-white p-4 shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)] sm:p-5">
      <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p class="mb-1 text-sm font-extrabold text-gray-700">智能练习能力</p>
          <p class="text-xs font-medium text-gray-500 sm:text-sm">
            答题后即时展示解析与知识点标签，答错自动加入错题本并支持专项重练。
          </p>
        </div>
        <RouterLink
          to="/app/mistakes"
          class="inline-flex items-center justify-center rounded-xl border border-orange-100 px-4 py-2.5 text-sm font-bold transition-colors"
          :class="practiceStore.mistakeEntry.enabled ? 'bg-orange-50 text-orange-600 hover:bg-orange-100' : 'cursor-not-allowed bg-gray-100 text-gray-400'"
          :aria-disabled="!practiceStore.mistakeEntry.enabled"
          @click.prevent="!practiceStore.mistakeEntry.enabled"
        >
          {{ `${practiceStore.mistakeEntry.buttonText}${practiceStore.mistakeEntry.enabled ? `（${practiceStore.mistakeEntry.mistakeCount}）` : ''}` }}
        </RouterLink>
      </div>
    </div>

    <AppLoading v-if="practiceStore.loading" />
    <AppErrorState v-else-if="practiceStore.error" :message="practiceStore.error" @retry="practiceStore.fetchModules" />
    <AppEmptyState v-else-if="filteredModules.length === 0" message="暂无可练习模块" />

    <div v-else class="grid grid-cols-1 gap-6 sm:gap-8 md:grid-cols-2 lg:grid-cols-3">
      <article
        v-for="(item, index) in filteredModules"
        :key="item.id"
        class="hover-card fade-in-up group relative flex flex-col overflow-hidden rounded-3xl border border-gray-50 bg-white shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)]"
        :class="index === 0 ? 'delay-100' : index === 1 ? 'delay-200' : 'delay-300'"
      >
        <div
          class="card-bg-overlay absolute inset-0 bg-gradient-to-br to-transparent"
          :class="getTheme(index).container"
        />
        <div class="relative h-2 overflow-hidden bg-gray-200">
          <div class="card-bar-overlay absolute inset-0 bg-gradient-to-r" :class="getTheme(index).bar" />
        </div>
        <div class="relative z-10 flex-grow p-6 sm:p-8">
          <div class="mb-4 flex items-start justify-between">
            <div class="flex items-center gap-3">
              <div
                class="icon-hover-target flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-2xl transition-transform"
                :class="[getTheme(index).iconBg, getTheme(index).iconColor]"
              >
                <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    :d="getIconPath(item.name)"
                  />
                </svg>
              </div>
              <h3
                class="text-xl font-extrabold text-gray-900 transition-colors"
                :class="getTheme(index).hoverTitle"
              >
                {{ item.name }}
              </h3>
            </div>
            <span class="inline-flex items-center rounded-lg bg-gray-100 px-3 py-1 text-xs font-bold text-gray-500">
              {{ getCategoryDisplay(item) }}
            </span>
          </div>
          <p class="mb-8 text-sm font-medium leading-relaxed text-gray-400">
            {{ getModuleDescription(item) }}
          </p>
          <div class="mb-2 mt-auto">
            <div class="mb-2 flex justify-between text-sm font-bold">
              <span class="text-gray-500">进度: {{ item.done }}/{{ item.total }} 题</span>
              <span :class="item.done === item.total && item.total > 0 ? getTheme(index).completeText : getTheme(index).progressText">
                {{ formatPercent(item.done, item.total) }}
              </span>
            </div>
            <div class="h-2.5 w-full overflow-hidden rounded-full bg-gray-100">
              <div
                class="relative h-full rounded-full"
                :class="item.done === 0 ? 'bg-gray-300' : getTheme(index).progressTrack"
                :style="{ width: `${Math.max(0, Math.min(100, item.total > 0 ? Math.round((item.done / item.total) * 100) : 0))}%` }"
              >
                <div v-if="item.done > 0" class="absolute inset-0 animate-[shimmer_2s_infinite] bg-white/20" />
              </div>
            </div>
          </div>
        </div>
        <div class="relative z-10 px-6 pb-6 pt-2 sm:px-8 sm:pb-8">
          <RouterLink
            :to="getPracticeLink(item.id, item.name)"
            class="block w-full rounded-xl py-3.5 text-center text-sm font-bold transition-all"
            :class="
              item.done === item.total && item.total > 0
                ? `bg-white border border-gray-200 text-gray-600 ${getTheme(index).completeButton}`
                : getTheme(index).buttonClass
            "
          >
            {{ getActionText(item) }}
          </RouterLink>
        </div>
      </article>
    </div>
  </main>
</template>

<style scoped>
.fade-in-up {
  animation: fadeInUp 0.6s ease-out forwards;
  opacity: 0;
}

.delay-100 {
  animation-delay: 0.1s;
}

.delay-200 {
  animation-delay: 0.2s;
}

.delay-300 {
  animation-delay: 0.3s;
}

.hover-card {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.hover-card:hover {
  transform: translateY(-4px);
  border-color: transparent;
  box-shadow: 0 12px 24px -8px rgba(0, 0, 0, 0.08);
}

.card-bg-overlay,
.card-bar-overlay {
  opacity: 0;
  transition: opacity 0.3s ease;
}

.hover-card:hover .card-bg-overlay,
.hover-card:hover .card-bar-overlay {
  opacity: 1;
}

.hover-card:hover .icon-hover-target {
  transform: scale(1.1);
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
