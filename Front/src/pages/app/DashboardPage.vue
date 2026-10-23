<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  checkinToday,
  getCheckinHeatmap,
  getDashboardSummary,
} from '@/api/modules/dashboard'
import { getMistakeModuleStats, type MistakeModuleStats } from '@/api/modules/mistakes'
import { showErrorToast, showSuccessToast } from '@/utils/toast'
import OnboardingGuide from '@/components/features/OnboardingGuide.vue'

type HeatCellTone = 'empty' | 'low' | 'medium' | 'high' | 'accent'

type HeatCell = {
  day: number | null
  tone: HeatCellTone
}

const weekLabels = ['一', '二', '三', '四', '五', '六', '日']
const monthKey = ref(formatMonthKey(new Date()))
const loading = ref(true)
const doneQuestionCount = ref(0)
const correctRate = ref(0)
const interviewCount = ref(0)
const remainingDailyInterviews = ref(6)
const maxDailyInterviews = ref(6)
const totalPracticeTimeMinutes = ref(0)
const continuousDays = ref(0)
const checkedInToday = ref(false)
const monthCheckedCount = ref(0)
const checkedDays = ref<number[]>([])
const checkinSubmitting = ref(false)
const weakModuleStats = ref<MistakeModuleStats[]>([])
const authStore = useAuthStore()
const showOnboarding = ref(localStorage.getItem('onboarding_completed') !== 'true')

const monthLabel = computed(() => {
  const [year, month] = monthKey.value.split('-')
  return `${year} 年 ${month} 月`
})
const heatmapCells = computed<HeatCell[]>(() => {
  const [yearText, monthText] = monthKey.value.split('-')
  const year = Number(yearText)
  const month = Number(monthText)
  if (!year || !month) {
    return []
  }
  const firstWeekDay = (new Date(year, month - 1, 1).getDay() + 6) % 7
  const daysInMonth = new Date(year, month, 0).getDate()
  const cells: HeatCell[] = []
  for (let i = 0; i < firstWeekDay; i += 1) {
    cells.push({ day: null, tone: 'empty' })
  }
  const checkedSet = new Set(checkedDays.value)
  for (let day = 1; day <= daysInMonth; day += 1) {
    const tone = checkedSet.has(day) ? getCheckedTone(day) : 'empty'
    cells.push({ day, tone })
  }
  while (cells.length % 7 !== 0) {
    cells.push({ day: null, tone: 'empty' })
  }
  return cells
})
const checkinHint = computed(() => {
  if (checkedInToday.value && hasCheckinReward.value) {
    return '已解锁 7 天连续签到奖励，今日面试次数 +1！继续保持！'
  }
  if (checkedInToday.value) {
    return `今天已完成学习打卡，再坚持 ${7 - continuousDays.value} 天即可解锁额外面试次数奖励。`
  }
  if (hasCheckinReward.value) {
    return '已解锁连续签到奖励，今日面试次数 +1！'
  }
  return `完成今日打卡即可保持连续学习势头，连续 7 天可解锁奖励。`
})
const checkinButtonText = computed(() => (checkedInToday.value ? '今日已打卡' : '立即打卡'))
const checkinStatusText = computed(() => (checkedInToday.value ? '已打卡' : '待打卡'))
const checkinStatusClass = computed(() => (checkedInToday.value ? 'text-emerald-500' : 'text-orange-500'))

const interviewRemainingColor = computed(() => {
  const v = remainingDailyInterviews.value
  if (typeof v !== 'number') return 'text-gray-400'
  if (v <= 1) return 'text-red-500'
  if (v <= 3) return 'text-orange-500'
  return 'text-emerald-500'
})
const interviewRemainingBg = computed(() => {
  const v = remainingDailyInterviews.value
  if (typeof v !== 'number') return 'bg-gray-300'
  if (v <= 1) return 'bg-red-500'
  if (v <= 3) return 'bg-orange-500'
  return 'bg-emerald-500'
})
const hasCheckinReward = computed(() => continuousDays.value >= 7)
const interviewRemainingProgress = computed(() => {
  const v = remainingDailyInterviews.value
  const max = maxDailyInterviews.value
  if (typeof v !== 'number' || !max) return 0
  return Math.round(((max - v) / max) * 100)
})

function formatMonthKey(date: Date) {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}`
}

function isCurrentMonth() {
  return monthKey.value === formatMonthKey(new Date())
}

function syncTodayCheckinStatus() {
  if (!isCurrentMonth()) {
    checkedInToday.value = false
    return
  }
  const todayDate = new Date().getDate()
  checkedInToday.value = checkedDays.value.includes(todayDate)
}

function getCheckedTone(day: number): HeatCellTone {
  if (day % 13 === 0 || day % 5 === 1) {
    return 'accent'
  }
  if (day % 7 === 0 || day % 11 === 0) {
    return 'high'
  }
  if (day % 3 === 0) {
    return 'medium'
  }
  return 'low'
}

function getCellClass(cell: HeatCell) {
  if (cell.day === null) {
    return 'h-10 rounded-xl bg-transparent'
  }
  if (cell.tone === 'high') {
    return 'h-10 rounded-xl bg-rose-300 text-rose-700 text-xs font-bold flex items-center justify-center'
  }
  if (cell.tone === 'medium') {
    return 'h-10 rounded-xl bg-rose-200 text-rose-600 text-xs font-bold flex items-center justify-center'
  }
  if (cell.tone === 'accent') {
    return 'h-10 rounded-xl bg-orange-100 text-orange-500 text-xs font-bold flex items-center justify-center'
  }
  if (cell.tone === 'low') {
    return 'h-10 rounded-xl bg-rose-100 text-rose-500 text-xs font-bold flex items-center justify-center'
  }
  return 'h-10 rounded-xl bg-gray-50 text-gray-300 text-xs font-bold flex items-center justify-center'
}

async function loadDashboardSummary() {
  try {
    const data = await getDashboardSummary()
    doneQuestionCount.value = data.doneQuestionCount
    interviewCount.value = data.interviewCount
    remainingDailyInterviews.value = data.remainingDailyInterviews ?? 6
    maxDailyInterviews.value = data.maxDailyInterviews ?? 6
    continuousDays.value = data.continuousCheckinDays
    correctRate.value = Math.round(data.correctRate * 100)
    totalPracticeTimeMinutes.value = data.totalPracticeTimeMinutes
  } catch {
    return
  }
}

async function loadHeatmap() {
  try {
    const data = await getCheckinHeatmap(monthKey.value)
    checkedDays.value = data.checkedDays.filter((day) => day >= 1 && day <= 31)
    continuousDays.value = data.continuousDays
    monthCheckedCount.value = checkedDays.value.length
    syncTodayCheckinStatus()
  } catch {
    checkedDays.value = []
    monthCheckedCount.value = 0
    syncTodayCheckinStatus()
  }
}

async function loadWeakModuleStats() {
  try {
    const data = await getMistakeModuleStats(5)
    weakModuleStats.value = data
  } catch {
    weakModuleStats.value = []
  }
}

async function handleCheckin() {
  if (checkedInToday.value || checkinSubmitting.value) {
    return
  }
  checkinSubmitting.value = true
  try {
    const data = await checkinToday()
    continuousDays.value = data.continuousDays
    checkedInToday.value = data.checkedIn
    if (isCurrentMonth()) {
      const todayDate = new Date().getDate()
      if (!checkedDays.value.includes(todayDate)) {
        checkedDays.value = [...checkedDays.value, todayDate].sort((a, b) => a - b)
      }
      monthCheckedCount.value = checkedDays.value.length
    }
    showSuccessToast(`已连续打卡${data.continuousDays}天`, '打卡成功', 'top-right')
  } catch (error) {
    const message = error instanceof Error ? error.message : '打卡失败，请稍后重试'
    showErrorToast(message, '打卡失败', 'top-right')
  } finally {
    checkinSubmitting.value = false
  }
}

onMounted(async () => {
  try {
    await Promise.all([loadDashboardSummary(), loadHeatmap(), loadWeakModuleStats()])
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="relative z-10 mx-auto max-w-7xl px-4 py-8 sm:px-6 sm:py-12 lg:px-8">
    <div class="fade-in-up mb-10">
      <h1 class="mb-2 text-3xl font-extrabold tracking-tight text-gray-900 sm:text-4xl">欢迎回来，{{ authStore.displayName }}</h1>
      <p class="text-lg font-medium text-gray-400">
        今天是您连续学习的第
        <span v-if="loading" class="mx-1 inline-block h-6 w-8 animate-pulse rounded-md bg-gray-200" />
        <span v-else class="mx-1 rounded-md border border-gray-100 bg-white px-2 py-0.5 font-extrabold text-rose-500 shadow-sm">
          {{ continuousDays }}
        </span>
        天，继续保持！
      </p>
    </div>

    <div class="mb-10 grid grid-cols-1 gap-6 md:grid-cols-3">
      <article
        class="hover-card fade-in-up delay-100 flex flex-col items-center justify-center rounded-3xl border border-gray-50 bg-white p-6 text-center shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)] sm:p-8"
      >
        <h3 class="mb-4 text-sm font-bold text-gray-500">已刷题目</h3>
        <div class="relative mb-4">
          <div class="relative flex h-16 w-16 items-center justify-center rounded-full bg-orange-50">
            <div class="absolute inset-0 rounded-full bg-orange-400 opacity-20 blur-md" />
            <svg class="relative z-10 h-8 w-8 text-orange-400" fill="currentColor" viewBox="0 0 20 20">
              <path
                d="M10 2a8 8 0 100 16 8 8 0 000-16zM8 11.5a1.5 1.5 0 113 0 1.5 1.5 0 01-3 0zM10 5a1 1 0 011 1v3a1 1 0 11-2 0V6a1 1 0 011-1z"
              />
            </svg>
          </div>
        </div>
        <div class="text-3xl font-extrabold text-gray-900">
          <span v-if="loading" class="inline-block h-8 w-16 animate-pulse rounded-lg bg-gray-200" />
          <span v-else>{{ doneQuestionCount }} <span class="text-sm font-bold text-gray-400">道</span></span>
        </div>
        <div class="mt-2 flex items-center justify-center gap-1 text-xs font-bold text-emerald-500">
          <svg class="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 10l7-7m0 0l7 7m-7-7v18" />
          </svg>
          {{ correctRate }}%
        </div>
      </article>

      <article
        class="hover-card fade-in-up delay-200 flex flex-col items-center justify-center rounded-3xl border border-gray-50 bg-white p-6 text-center shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)] sm:p-8"
      >
        <h3 class="mb-4 text-sm font-bold text-gray-500">AI 模拟面试</h3>
        <div class="relative mb-4">
          <div class="relative flex h-16 w-16 items-center justify-center rounded-full bg-rose-50">
            <div class="absolute inset-0 rounded-full bg-rose-400 opacity-20 blur-md" />
            <svg class="relative z-10 h-8 w-8 text-rose-500" fill="currentColor" viewBox="0 0 20 20">
              <path d="M2 10a8 8 0 018-8v8h8a8 8 0 11-16 0z" />
              <path d="M12 2.252A8.014 8.014 0 0117.748 8H12V2.252z" />
            </svg>
          </div>
        </div>
        <div class="text-3xl font-extrabold" :class="interviewRemainingColor">
          <span v-if="loading" class="inline-block h-8 w-16 animate-pulse rounded-lg bg-gray-200" />
          <span v-else>{{ remainingDailyInterviews }} <span class="text-sm font-bold text-gray-400">/ {{ maxDailyInterviews }} 次</span></span>
        </div>
        <div class="mt-2 w-full max-w-[120px]">
          <div class="h-1.5 w-full overflow-hidden rounded-full bg-gray-100">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="interviewRemainingBg"
              :style="{ width: interviewRemainingProgress + '%' }"
            />
          </div>
        </div>
        <div class="mt-2 flex items-center justify-center gap-1 text-xs font-bold" :class="remainingDailyInterviews === 0 ? 'text-red-400' : 'text-gray-400'">
          {{ remainingDailyInterviews === 0 ? '今日面试次数已用完' : '今日剩余面试次数' }} · 累计 {{ interviewCount }} 场
        </div>
      </article>

      <article
        class="hover-card fade-in-up delay-300 flex flex-col items-center justify-center rounded-3xl border border-gray-50 bg-white p-6 text-center shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)] sm:p-8"
      >
        <h3 class="mb-4 text-sm font-bold text-gray-500">累计刷题时长</h3>
        <div class="relative mb-4">
          <div class="relative flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50">
            <div class="absolute inset-0 rounded-full bg-emerald-400 opacity-20 blur-md" />
            <svg class="relative z-10 h-8 w-8 text-emerald-400" fill="currentColor" viewBox="0 0 20 20">
              <path
                fill-rule="evenodd"
                d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                clip-rule="evenodd"
              />
            </svg>
          </div>
        </div>
        <div class="text-3xl font-extrabold text-gray-900">
          <span v-if="loading" class="inline-block h-8 w-12 animate-pulse rounded-lg bg-gray-200" />
          <span v-else>{{ totalPracticeTimeMinutes }} <span class="text-sm font-bold text-gray-400">分钟</span></span>
        </div>
        <div class="mt-2 flex items-center justify-center gap-1 text-xs font-bold text-emerald-500">
          <svg class="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 10l7-7m0 0l7 7m-7-7v18" />
          </svg>
          <span v-if="loading" class="inline-block h-4 w-16 animate-pulse rounded bg-gray-200" />
          <span v-else>连续 {{ continuousDays }} 天</span>
        </div>
      </article>
    </div>

    <div class="mb-10 grid grid-cols-1 gap-8 xl:grid-cols-3">
      <div class="fade-in-up delay-200 xl:col-span-1">
        <div class="flex h-full flex-col justify-between rounded-3xl border border-gray-50 bg-white p-6 shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)]">
          <div>
            <div class="mb-4 inline-flex items-center gap-2 rounded-full bg-orange-50 px-3 py-1 text-xs font-extrabold text-orange-500">
              <span class="h-2 w-2 rounded-full bg-orange-400" />
              今日打卡
            </div>
            <h2 class="mb-2 text-2xl font-extrabold text-gray-900">
              <span v-if="loading" class="inline-block h-8 w-40 animate-pulse rounded-lg bg-gray-200" />
              <span v-else>连续学习 {{ continuousDays }} 天</span>
            </h2>
            <p class="text-sm font-medium leading-relaxed text-gray-400">
              {{ checkinHint }}
            </p>
            <div
              v-if="hasCheckinReward"
              class="mt-4 inline-flex items-center gap-2 rounded-2xl bg-gradient-to-r from-amber-50 to-orange-50 border border-amber-200 px-4 py-2.5 text-sm font-extrabold text-amber-700 shadow-sm"
            >
              <svg class="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
              连续签到 7 天奖励已解锁：今日面试次数 +1
            </div>
          </div>
          <div class="mt-6 grid grid-cols-2 gap-3">
            <div class="rounded-2xl bg-emerald-50/70 px-4 py-3">
              <p class="mb-1 text-xs font-bold text-gray-500">今日状态</p>
              <p class="text-sm font-extrabold" :class="checkinStatusClass">{{ checkinStatusText }}</p>
            </div>
            <div class="rounded-2xl bg-rose-50/70 px-4 py-3">
              <p class="mb-1 text-xs font-bold text-gray-500">本月累计</p>
              <p class="text-sm font-extrabold text-rose-500">{{ monthCheckedCount }} 天</p>
            </div>
          </div>
          <button
            type="button"
            class="mt-6 w-full rounded-xl py-3 font-extrabold text-white shadow-sm transition-colors shadow-rose-500/25"
            :class="loading ? 'bg-gray-300 cursor-wait' : (checkedInToday ? 'bg-gray-300 cursor-not-allowed' : 'bg-rose-500 hover:bg-rose-600')"
            :disabled="loading || checkedInToday || checkinSubmitting"
            @click="handleCheckin"
          >
            <span v-if="loading" class="inline-block h-5 w-16 animate-pulse rounded bg-white/30" />
            <span v-else>{{ checkinButtonText }}</span>
          </button>
        </div>
      </div>

      <div class="fade-in-up delay-300 xl:col-span-2">
        <div class="rounded-3xl border border-gray-50 bg-white p-6 shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)]">
          <div class="mb-5 flex items-center justify-between">
            <h2 class="text-xl font-extrabold text-gray-900">月度打卡热力图</h2>
            <span class="text-sm font-bold text-gray-400">{{ monthLabel }}</span>
          </div>
          <div class="mb-3 grid grid-cols-7 gap-2 text-center text-xs font-bold text-gray-400">
            <span v-for="label in weekLabels" :key="label">{{ label }}</span>
          </div>
          <div class="grid grid-cols-7 gap-2">
            <div v-for="(cell, index) in heatmapCells" :key="`cell-${index}-${cell.day ?? 'blank'}`" :class="getCellClass(cell)">
              <span v-if="cell.day !== null">{{ cell.day }}</span>
            </div>
          </div>
          <div class="mt-5 flex items-center gap-4 text-xs font-bold text-gray-400">
            <span>少</span>
            <span class="h-4 w-4 rounded bg-gray-50" />
            <span class="h-4 w-4 rounded bg-rose-100" />
            <span class="h-4 w-4 rounded bg-rose-200" />
            <span class="h-4 w-4 rounded bg-rose-300" />
            <span>多</span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-8 lg:grid-cols-2">
      <section class="fade-in-up delay-200">
        <div class="mb-5 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <h2 class="text-xl font-extrabold text-gray-900">薄弱模块排行</h2>
            <span class="rounded-full bg-rose-50 px-2 py-0.5 text-xs font-bold text-rose-500">基于错题数据</span>
          </div>
          <RouterLink
            to="/app/mistakes"
            class="flex items-center gap-1 rounded-full border border-gray-100 bg-white px-4 py-2 text-sm font-bold text-gray-600 shadow-sm transition-all hover:text-rose-500 hover:shadow-md"
          >
            错题本
          </RouterLink>
        </div>
        <div v-if="weakModuleStats.length === 0" class="flex flex-col items-center justify-center rounded-3xl border border-gray-50 bg-white p-12 text-center shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)]">
          <div class="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-50">
            <svg class="h-8 w-8 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <p class="text-sm font-medium text-gray-400">暂无薄弱模块</p>
          <p class="mt-1 text-xs text-gray-300">做错的题目会出现在这里</p>
        </div>
        <div v-else class="space-y-3">
          <RouterLink
            v-for="(item, index) in weakModuleStats"
            :key="item.moduleId"
            :to="`/app/practice/${item.moduleId}?mode=mistake`"
            class="hover-card group block overflow-hidden rounded-2xl border border-gray-50 bg-white p-4 shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)] transition-all hover:border-rose-100"
          >
            <div class="flex items-center gap-4">
              <div
                class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-xl font-extrabold"
                :class="index === 0 ? 'bg-rose-100 text-rose-500' : index === 1 ? 'bg-orange-100 text-orange-500' : 'bg-gray-100 text-gray-500'"
              >
                {{ index + 1 }}
              </div>
              <div class="min-w-0 flex-1">
                <h3 class="truncate text-base font-extrabold text-gray-900 transition-colors group-hover:text-rose-500">
                  {{ item.moduleName }}
                </h3>
                <div class="mt-1 flex items-center gap-3 text-xs text-gray-400">
                  <span class="flex items-center gap-1">
                    <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    {{ item.mistakeCount }} 错题
                  </span>
                  <span class="flex items-center gap-1">
                    <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                    </svg>
                    正确率 {{ item.correctRate }}%
                  </span>
                </div>
              </div>
              <div class="flex-shrink-0">
                <div class="flex h-10 w-10 items-center justify-center rounded-full bg-rose-50 text-rose-400 opacity-0 transition-all group-hover:opacity-100">
                  <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                  </svg>
                </div>
              </div>
            </div>
          </RouterLink>
        </div>
      </section>

      <section class="fade-in-up delay-300 h-full">
        <div class="mb-5 flex items-center justify-between">
          <h2 class="text-xl font-extrabold text-gray-900">实战模拟</h2>
        </div>
        <div
          class="group relative flex h-[calc(100%-3rem)] flex-col justify-between overflow-hidden rounded-3xl border border-rose-100 bg-gradient-to-br from-rose-50 to-orange-50 p-8 shadow-[0_4px_20px_-2px_rgba(0,0,0,0.03)]"
        >
          <div
            class="absolute right-0 top-0 -mr-10 -mt-10 h-40 w-40 rounded-full bg-white opacity-40 blur-2xl transition-transform duration-700 group-hover:scale-125"
          />
          <div
            class="absolute bottom-0 right-10 -mb-10 h-32 w-32 rounded-full bg-orange-200 opacity-30 blur-2xl transition-transform delay-100 duration-700 group-hover:scale-125"
          />

          <div class="relative z-10">
            <div
              class="mb-4 inline-flex items-center gap-2 rounded-full bg-white/60 px-3 py-1 text-xs font-extrabold text-rose-600 shadow-sm backdrop-blur-sm"
            >
              <span class="h-2 w-2 animate-pulse rounded-full bg-rose-500" />
              AI 面试官在线
            </div>
            <h3 class="mb-3 text-2xl font-extrabold text-gray-900">沉浸式逐问模拟面试</h3>
            <p class="mb-8 max-w-md text-sm font-medium leading-relaxed text-gray-600">
              提供您的意向岗位 JD 和简历，AI 将扮演资深面试官与您进行深度的一对一对话，真实还原大厂面试场景，精准指出薄弱点。
            </p>
          </div>
          <RouterLink
            to="/app/interview/setup"
            class="group/btn relative z-10 inline-flex w-fit items-center justify-center gap-2 rounded-xl border border-rose-100 bg-white px-6 py-3.5 text-center font-extrabold text-rose-600 shadow-md transition-all hover:-translate-y-1 hover:shadow-lg"
          >
            创建新面试
            <svg
              class="h-5 w-5 transition-transform group-hover/btn:translate-x-1"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </RouterLink>
        </div>
      </section>
    </div>
    <OnboardingGuide v-if="showOnboarding" @close="showOnboarding = false" />
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
