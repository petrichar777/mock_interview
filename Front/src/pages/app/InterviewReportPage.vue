<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  getReportDetail,
  getReportList,
  getTrendScores,
  listInterviewMessages,
  downloadReportPdf,
  type EvaluateResult,
  type InterviewMessage,
  type InterviewSessionMessage,
  type ReportItem,
} from '@/api/modules/interview'
import InterviewReplayTab from '@/features/interview/InterviewReplayTab.vue'

type ModeFilter = '' | 'COMPREHENSIVE' | 'ALGORITHM'

const page = ref(1)
const pageSize = ref(8)
const modeFilter = ref<ModeFilter>('')
const loading = ref(false)
const error = ref('')
const reports = ref<ReportItem[]>([])
const total = ref(0)

const detailVisible = ref(false)
const detailSessionId = ref<number | null>(null)
const detailTitle = ref('')
const detailEvaluation = ref<EvaluateResult | null>(null)
const detailMessages = ref<InterviewMessage[]>([])
const detailLoading = ref(false)
const detailError = ref('')
const activeTab = ref<'evaluation' | 'replay'>('evaluation')

const animatedScore = ref(0)
const barsVisible = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const canPrev = computed(() => page.value > 1)
const canNext = computed(() => page.value < totalPages.value)
const empty = computed(() => total.value === 0 && !loading.value)

const stats = computed(() => {
  const scored = reports.value.filter((r) => r.totalScore != null)
  if (scored.length === 0) return { count: total.value, avgScore: 0, maxScore: 0 }
  const totalScore = scored.reduce((sum, r) => sum + (r.totalScore ?? 0), 0)
  return {
    count: total.value,
    avgScore: Math.round(totalScore / scored.length),
    maxScore: Math.max(...scored.map((r) => r.totalScore ?? 0)),
  }
})

// ── Trend chart ──
const trendScores = ref<ReportItem[]>([])
const trendLoading = ref(false)

interface ChartPoint {
  x: number; y: number; score: number; date: string; title: string
}

const CHART_W = 600
const CHART_H = 200
const CHART_PAD = { top: 18, right: 24, bottom: 38, left: 40 }

const chartPoints = computed<ChartPoint[]>(() => {
  const scored = trendScores.value.filter((r) => r.totalScore != null)
  if (scored.length === 0) return []
  const plotW = CHART_W - CHART_PAD.left - CHART_PAD.right
  const plotH = CHART_H - CHART_PAD.top - CHART_PAD.bottom
  return scored.map((r, i) => ({
    x: CHART_PAD.left + (scored.length === 1 ? plotW / 2 : (i / (scored.length - 1)) * plotW),
    y: CHART_PAD.top + (1 - Math.min(Math.max((r.totalScore ?? 0), 0), 100) / 100) * plotH,
    score: r.totalScore ?? 0,
    date: formatDateShort(r.finishedAt),
    title: r.title,
  }))
})

const chartLinePath = computed(() => {
  const pts = chartPoints.value
  if (pts.length < 2) return ''
  let d = `M ${pts[0].x} ${pts[0].y}`
  for (let i = 0; i < pts.length - 1; i++) {
    const cpX = (pts[i].x + pts[i + 1].x) / 2
    d += ` C ${cpX} ${pts[i].y} ${cpX} ${pts[i + 1].y} ${pts[i + 1].x} ${pts[i + 1].y}`
  }
  return d
})

const chartAreaPath = computed(() => {
  if (chartPoints.value.length < 2) return ''
  const bottom = CHART_H - CHART_PAD.bottom
  const last = chartPoints.value[chartPoints.value.length - 1]
  const first = chartPoints.value[0]
  return `${chartLinePath.value} L ${last.x} ${bottom} L ${first.x} ${bottom} Z`
})

const chartYLabels = [0, 20, 40, 60, 80, 100]

async function fetchTrendScores() {
  trendLoading.value = true
  try {
    trendScores.value = await getTrendScores(15)
  } catch {
    // Silently fail — trend chart is supplementary, list is the primary content
  } finally {
    trendLoading.value = false
  }
}

function getScoreLabel(score: number | null) {
  if (score == null) return '未评估'
  if (score >= 85) return '优秀'
  if (score >= 70) return '良好'
  if (score >= 60) return '一般'
  return '需努力'
}

function getScoreClass(score: number | null) {
  if (score == null) return 'bg-gray-50 text-gray-400'
  if (score >= 85) return 'bg-emerald-50 text-emerald-500'
  if (score >= 70) return 'bg-blue-50 text-blue-500'
  if (score >= 60) return 'bg-amber-50 text-amber-500'
  return 'bg-rose-50 text-rose-500'
}

function getScoreTextClass(score: number | null) {
  if (score == null) return 'text-gray-400'
  if (score >= 85) return 'text-emerald-500'
  if (score >= 70) return 'text-blue-500'
  if (score >= 60) return 'text-amber-500'
  return 'text-rose-500'
}

function getModeLabel(mode: string) {
  return mode === 'ALGORITHM' ? '算法面试' : '综合面试'
}

function getStyleLabel(style: string) {
  return style === 'PRESSURE' ? '压力式' : '引导式'
}

function formatTime(value?: string) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function formatDateShort(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getMonth() + 1}/${date.getDate()}`
}

async function fetchReports() {
  loading.value = true
  error.value = ''
  try {
    const params: { page: number; size: number; mode?: string } = {
      page: page.value,
      size: pageSize.value,
    }
    if (modeFilter.value) params.mode = modeFilter.value
    const data = await getReportList(params)
    reports.value = data.list
    total.value = data.total
    page.value = data.page
  } catch (err) {
    error.value = err instanceof Error ? err.message : '获取面试报告列表失败'
  } finally {
    loading.value = false
  }
}

function normalizeMessageRole(role: string): InterviewMessage['role'] {
  if (role === 'user') return 'USER'
  if (role === 'system') return 'SYSTEM'
  return 'AI'
}

function computeBarColor(score: number, maxScore: number) {
  const ratio = score / maxScore
  if (ratio >= 0.75) return '#10b981'
  if (ratio >= 0.5) return '#f59e0b'
  return '#ef4444'
}

function getEvalScoreLabel(score: number) {
  if (score >= 90) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '一般'
  return '需努力'
}

function getEvalScoreColor(score: number) {
  if (score >= 90) return '#10b981'
  if (score >= 75) return '#3b82f6'
  if (score >= 60) return '#f59e0b'
  return '#ef4444'
}

async function openDetail(item: ReportItem) {
  detailEvaluation.value = null
  detailMessages.value = []
  detailError.value = ''
  detailTitle.value = item.title
  detailSessionId.value = item.sessionId
  detailLoading.value = true
  detailVisible.value = true
  activeTab.value = 'evaluation'
  try {
    const [evaluation, serverMessages] = await Promise.all([
      getReportDetail(item.sessionId),
      listInterviewMessages(item.sessionId),
    ])
    detailEvaluation.value = evaluation
    detailMessages.value = serverMessages.map((msg: InterviewSessionMessage): InterviewMessage => ({
      id: String(msg.id),
      role: normalizeMessageRole(msg.role),
      content: msg.content,
      createdAt: msg.createdAt,
      kind: msg.kind,
      contentFormat: msg.contentFormat,
      turnNo: msg.turnNo,
      typing: false,
      reviewTitle: msg.reviewTitle ?? null,
      reviewType: msg.reviewType ?? null,
    }))
  } catch (err) {
    detailError.value = err instanceof Error ? err.message : '获取详情失败'
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailVisible.value = false
  detailEvaluation.value = null
  detailMessages.value = []
  detailError.value = ''
  activeTab.value = 'evaluation'
}

const exporting = ref(false)
async function handleExportPdf() {
  if (exporting.value || !detailSessionId.value) return
  exporting.value = true
  try {
    await downloadReportPdf(detailSessionId.value)
  } finally {
    exporting.value = false
  }
}

watch(detailEvaluation, (evaluation) => {
  if (evaluation) {
    animatedScore.value = 0
    barsVisible.value = false
    const target = evaluation.totalScore
    const duration = 800
    const start = performance.now()
    const tick = (now: number) => {
      const elapsed = now - start
      const progress = Math.min(elapsed / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3)
      animatedScore.value = Math.round(eased * target)
      if (progress < 1) requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
    setTimeout(() => { barsVisible.value = true }, 400)
  }
}, { immediate: true })

function onModeFilterChange(value: ModeFilter) {
  modeFilter.value = value
  page.value = 1
  fetchReports()
}

function prevPage() {
  if (canPrev.value) { page.value -= 1; fetchReports() }
}

function nextPage() {
  if (canNext.value) { page.value += 1; fetchReports() }
}

onMounted(() => {
  fetchReports()
  fetchTrendScores()
})
</script>

<template>
  <main class="mx-auto max-w-4xl px-4 py-8 sm:px-6 sm:py-12">
    <div class="fade-in-up relative z-10 mb-8 flex flex-col items-start justify-between gap-4 border-b border-gray-200/50 pb-6 sm:flex-row sm:items-end">
      <div>
        <div class="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-violet-200 bg-violet-100 text-violet-500 shadow-sm">
          <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
        </div>
        <h1 class="mb-2 text-3xl font-extrabold text-gray-800">面试记录</h1>
        <p class="font-medium text-gray-500">
          共 <span class="mx-1 rounded-lg bg-violet-50 px-2 py-0.5 font-extrabold text-violet-500 shadow-sm">{{ total }}</span> 次面试记录
        </p>
      </div>
    </div>

    <!-- Stats -->
    <section class="fade-in-up delay-100 relative z-10 mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
      <div class="rounded-2xl border border-white bg-white/80 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-md">
        <p class="mb-2 text-xs font-bold text-gray-400">累计面试</p>
        <p class="text-2xl font-extrabold text-violet-500">{{ stats.count }} 次</p>
      </div>
      <div class="rounded-2xl border border-white bg-white/80 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-md">
        <p class="mb-2 text-xs font-bold text-gray-400">平均得分</p>
        <p class="text-2xl font-extrabold text-emerald-500">{{ stats.avgScore }} 分</p>
      </div>
      <div class="rounded-2xl border border-white bg-white/80 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-md">
        <p class="mb-2 text-xs font-bold text-gray-400">最高得分</p>
        <p class="text-2xl font-extrabold text-orange-500">{{ stats.maxScore }} 分</p>
      </div>
    </section>

    <!-- Trend Chart -->
    <section
      v-if="chartPoints.length >= 2"
      class="fade-in-up delay-150 relative z-10 mb-6 overflow-hidden rounded-3xl border border-white bg-white/80 p-5 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-md sm:p-6"
    >
      <div class="mb-4 flex items-center justify-between">
        <div>
          <h3 class="text-base font-extrabold text-gray-800">分数趋势</h3>
          <p class="text-xs font-bold text-gray-400">最近 {{ chartPoints.length }} 次已评估面试</p>
        </div>
        <div class="flex items-center gap-3 text-xs font-bold">
          <span class="flex items-center gap-1.5 text-emerald-500">
            <span class="h-2 w-2 rounded-full bg-emerald-400" /> 趋势
          </span>
          <span class="flex items-center gap-1.5 text-gray-400">
            <span class="inline-block h-px w-3 rounded bg-gray-300" /> 60分及格线
          </span>
        </div>
      </div>
      <div class="relative w-full">
        <svg :viewBox="`0 0 ${CHART_W} ${CHART_H}`" class="h-auto w-full" preserveAspectRatio="xMidYMid meet">
          <!-- Gradients -->
          <defs>
            <linearGradient id="trendLineGrad" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stop-color="#8b5cf6" />
              <stop offset="100%" stop-color="#a78bfa" />
            </linearGradient>
            <linearGradient id="trendAreaGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#8b5cf6" stop-opacity="0.18" />
              <stop offset="100%" stop-color="#8b5cf6" stop-opacity="0.02" />
            </linearGradient>
            <filter id="trendGlow">
              <feGaussianBlur in="SourceGraphic" stdDeviation="2.5" />
            </filter>
          </defs>

          <!-- Horizontal grid lines + Y labels -->
          <template v-for="yVal in chartYLabels" :key="'y' + yVal">
            <line
              v-if="yVal > 0 && yVal < 100"
              :x1="CHART_PAD.left"
              :x2="CHART_W - CHART_PAD.right"
              :y1="CHART_PAD.top + (1 - yVal / 100) * (CHART_H - CHART_PAD.top - CHART_PAD.bottom)"
              :y2="CHART_PAD.top + (1 - yVal / 100) * (CHART_H - CHART_PAD.top - CHART_PAD.bottom)"
              stroke="#e2e8f0"
              stroke-dasharray="3 4"
              stroke-width="0.8"
            />
            <!-- 60分及格线 brighter -->
            <line
              v-if="yVal === 60"
              :x1="CHART_PAD.left"
              :x2="CHART_W - CHART_PAD.right"
              :y1="CHART_PAD.top + (1 - yVal / 100) * (CHART_H - CHART_PAD.top - CHART_PAD.bottom)"
              :y2="CHART_PAD.top + (1 - yVal / 100) * (CHART_H - CHART_PAD.top - CHART_PAD.bottom)"
              stroke="#f59e0b"
              stroke-dasharray="6 3"
              stroke-width="1.2"
              opacity="0.3"
            />
            <text
              :x="CHART_PAD.left - 6"
              :y="CHART_PAD.top + (1 - yVal / 100) * (CHART_H - CHART_PAD.top - CHART_PAD.bottom) + 3.5"
              text-anchor="end"
              fill="#94a3b8"
              font-size="10"
              font-weight="700"
            >{{ yVal }}</text>
          </template>

          <!-- Area fill -->
          <path v-if="chartAreaPath" :d="chartAreaPath" fill="url(#trendAreaGrad)" />

          <!-- Line -->
          <path
            v-if="chartLinePath"
            :d="chartLinePath"
            fill="none"
            stroke="url(#trendLineGrad)"
            stroke-width="2.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          />

          <!-- Data points -->
          <template v-for="(pt, i) in chartPoints" :key="'pt' + i">
            <!-- Outer glow -->
            <circle
              :cx="pt.x" :cy="pt.y" r="7"
              fill="#8b5cf6" opacity="0.12"
              :filter="i === chartPoints.length - 1 ? 'url(#trendGlow)' : undefined"
            />
            <!-- White ring -->
            <circle :cx="pt.x" :cy="pt.y" r="4" fill="white" stroke="#8b5cf6" stroke-width="2.5" />
            <!-- Inner dot -->
            <circle :cx="pt.x" :cy="pt.y" r="1.8" fill="#8b5cf6" />
          </template>

          <!-- X-axis labels -->
          <text
            v-for="(pt, i) in chartPoints"
            :key="'xl' + i"
            :x="pt.x"
            :y="CHART_H - 6"
            text-anchor="middle"
            fill="#94a3b8"
            font-size="10"
            font-weight="600"
          >{{ pt.date }}</text>
        </svg>
      </div>
    </section>

    <!-- Mode Filter -->
    <div class="fade-in-up delay-100 relative z-10 mb-6 flex gap-2">
      <button
        v-for="opt in [{ value: '', label: '全部' }, { value: 'COMPREHENSIVE', label: '综合面试' }, { value: 'ALGORITHM', label: '算法面试' }]"
        :key="opt.value"
        type="button"
        class="rounded-xl px-4 py-2 text-sm font-bold transition-all"
        :class="modeFilter === (opt.value as ModeFilter)
          ? 'bg-violet-500 text-white shadow-[0_4px_12px_rgba(139,92,246,0.3)]'
          : 'border border-white bg-white/80 text-gray-500 hover:bg-violet-50 hover:text-violet-500'"
        @click="onModeFilterChange(opt.value as ModeFilter)"
      >
        {{ opt.label }}
      </button>
    </div>

    <!-- Error -->
    <div v-if="error" class="relative z-10 mb-4 rounded-2xl border border-rose-100 bg-rose-50/80 p-4 text-sm font-bold text-rose-500">
      {{ error }}
    </div>

    <!-- Loading -->
    <div v-if="loading" class="relative z-10 mb-4 rounded-2xl border border-white bg-white/80 p-4 text-sm font-bold text-gray-500">
      正在加载面试记录...
    </div>

    <!-- Report List -->
    <div class="relative z-10 space-y-4">
      <article
        v-for="(item, index) in reports"
        :key="item.sessionId"
        class="fade-in-up group relative cursor-pointer overflow-hidden rounded-3xl border border-white bg-white/80 p-5 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md transition-all hover:-translate-y-1 hover:shadow-[0_12px_35px_rgba(139,92,246,0.1)] sm:p-6"
        :class="index % 2 === 0 ? 'delay-100' : 'delay-200'"
        @click="openDetail(item)"
      >
        <div class="absolute bottom-0 left-0 top-0 w-1.5 bg-violet-400 opacity-0 transition-opacity group-hover:opacity-100" />
        <div class="flex items-center justify-between gap-4">
          <div class="min-w-0 flex-1">
            <h3 class="mb-2 truncate text-base font-extrabold text-gray-800">{{ item.title }}</h3>
            <div class="flex flex-wrap items-center gap-2">
              <span class="rounded-lg border border-violet-100 bg-violet-50 px-2 py-0.5 text-xs font-bold text-violet-500">
                {{ getModeLabel(item.mode) }}
              </span>
              <span class="rounded-lg border border-orange-100 bg-orange-50 px-2 py-0.5 text-xs font-bold text-orange-500">
                {{ getStyleLabel(item.style) }}
              </span>
              <span class="text-xs font-bold text-gray-400">{{ formatTime(item.finishedAt) }}</span>
            </div>
          </div>
          <div class="flex flex-shrink-0 flex-col items-center gap-1">
            <span class="text-3xl font-extrabold" :class="getScoreTextClass(item.totalScore)">
              {{ item.totalScore ?? '--' }}
            </span>
            <span class="rounded-full px-2 py-0.5 text-xs font-bold" :class="getScoreClass(item.totalScore)">
              {{ getScoreLabel(item.totalScore) }}
            </span>
          </div>
        </div>
      </article>
    </div>

    <!-- Empty -->
    <div v-if="empty" class="relative z-10 mt-4 rounded-2xl border border-white bg-white/80 p-6 text-center text-sm font-bold text-gray-500 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-sm">
      还没有面试记录，去完成一场 AI 模拟面试吧。
    </div>

    <!-- Pagination -->
    <div v-if="!empty" class="fade-in-up delay-200 relative z-10 mt-10 flex justify-center">
      <nav class="flex items-center space-x-2 rounded-2xl border border-white bg-white/80 p-1.5 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-sm">
        <button
          type="button"
          class="rounded-xl p-2 transition-colors"
          :class="canPrev ? 'text-gray-600 hover:bg-violet-50 hover:text-violet-500' : 'cursor-not-allowed text-gray-400'"
          :disabled="!canPrev"
          @click="prevPage"
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <button
          v-for="n in totalPages"
          :key="n"
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-xl text-sm font-bold transition-colors"
          :class="page === n
            ? 'bg-gradient-to-r from-violet-400 to-purple-400 text-white shadow-[0_4px_10px_rgba(139,92,246,0.3)]'
            : 'text-gray-600 hover:bg-violet-50 hover:text-violet-500'"
          @click="page = n; fetchReports()"
        >
          {{ n }}
        </button>
        <button
          type="button"
          class="rounded-xl p-2 transition-colors"
          :class="canNext ? 'text-gray-600 hover:bg-violet-50 hover:text-violet-500' : 'cursor-not-allowed text-gray-400'"
          :disabled="!canNext"
          @click="nextPage"
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </nav>
    </div>
  </main>

  <!-- Detail overlay: loading -->
  <Teleport to="body">
    <div v-if="detailVisible && detailLoading" class="fixed inset-0 z-[10000] flex items-center justify-center" @click.self="closeDetail">
      <div class="absolute inset-0 bg-gray-900/50 backdrop-blur-md" />
      <div class="relative rounded-2xl border border-white bg-white px-8 py-6 text-sm font-bold text-gray-500 shadow-2xl">
        正在加载评估详情...
      </div>
    </div>
  </Teleport>

  <!-- Detail overlay: error -->
  <Teleport to="body">
    <div v-if="detailVisible && detailError" class="fixed inset-0 z-[10000] flex items-center justify-center" @click.self="closeDetail">
      <div class="absolute inset-0 bg-gray-900/50 backdrop-blur-md" />
      <div class="relative mx-4 w-full max-w-sm rounded-2xl border border-rose-100 bg-white p-6 text-sm font-bold text-rose-500 shadow-2xl">
        {{ detailError }}
        <button type="button" class="mt-3 w-full rounded-xl bg-rose-50 px-3 py-2 font-bold text-rose-500 hover:bg-rose-100" @click="closeDetail">关闭</button>
      </div>
    </div>
  </Teleport>

  <!-- Detail overlay: tabbed content -->
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="detailVisible && !detailLoading && !detailError && detailEvaluation"
        class="fixed inset-0 z-[10000] flex items-center justify-center p-4"
        @click.self="closeDetail"
      >
        <div class="absolute inset-0 bg-gray-900/50 backdrop-blur-md" />
        <div
          class="relative flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-3xl border border-white/30 bg-white/95 shadow-2xl backdrop-blur-xl"
          @click.stop
        >
          <!-- Tab Header -->
          <div class="flex flex-shrink-0 border-b border-gray-100">
            <button
              v-for="tab in [
                { key: 'evaluation' as const, label: '评估报告' },
                { key: 'replay' as const, label: '对话回放' },
              ]"
              :key="tab.key"
              type="button"
              class="flex-1 px-4 py-3.5 text-sm font-bold transition-colors"
              :class="activeTab === tab.key
                ? 'border-b-2 border-violet-500 text-violet-600'
                : 'text-gray-400 hover:text-gray-600'"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
            </button>
            <button
              type="button"
              class="flex items-center gap-1.5 px-2 py-3.5 text-sm font-bold text-gray-400 transition-colors hover:text-rose-500 sm:px-4"
              :disabled="exporting"
              @click="handleExportPdf"
            >
              <svg v-if="exporting" class="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <svg v-else class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span class="hidden sm:inline">导出 PDF</span>
            </button>
            <button
              type="button"
              class="px-4 py-3.5 text-gray-400 transition-colors hover:text-gray-600"
              @click="closeDetail"
            >
              <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <!-- Tab Content: Evaluation -->
          <div v-if="activeTab === 'evaluation'" class="flex-1 overflow-y-auto px-6 py-6 sm:px-8">
            <!-- Score circle -->
            <div class="mb-8 text-center">
              <div
                class="mx-auto mb-3 flex h-24 w-24 items-center justify-center rounded-full text-3xl font-extrabold text-white shadow-lg"
                :style="{ background: `linear-gradient(135deg, ${getEvalScoreColor(animatedScore)}, ${getEvalScoreColor(animatedScore)}dd)` }"
              >
                {{ animatedScore }}
              </div>
              <span class="rounded-full px-3 py-1 text-xs font-bold text-white" :style="{ background: getEvalScoreColor(detailEvaluation.totalScore) }">
                {{ getEvalScoreLabel(detailEvaluation.totalScore) }}
              </span>
              <h2 class="mt-3 text-lg font-extrabold text-gray-800">面试评估报告</h2>
            </div>

            <!-- Dimensions -->
            <div v-if="detailEvaluation.dimensions?.length" class="mb-6 space-y-4">
              <div v-for="(dim, i) in detailEvaluation.dimensions" :key="dim.name">
                <div class="mb-1 flex items-center justify-between text-sm">
                  <span class="font-bold text-gray-700">{{ dim.name }}</span>
                  <span class="text-xs font-bold text-gray-400">{{ dim.score }} / {{ dim.maxScore }}</span>
                </div>
                <div class="h-2 w-full overflow-hidden rounded-full bg-gray-100">
                  <div
                    class="h-full rounded-full transition-all duration-700 ease-out"
                    :style="{
                      width: barsVisible ? `${(dim.score / dim.maxScore) * 100}%` : '0%',
                      background: computeBarColor(dim.score, dim.maxScore),
                      transitionDelay: `${i * 100}ms`,
                    }"
                  />
                </div>
                <p v-if="dim.comment" class="mt-1 text-xs text-gray-500">{{ dim.comment }}</p>
              </div>
            </div>

            <!-- Summary -->
            <div class="mb-4 rounded-2xl bg-blue-50/80 p-4">
              <h3 class="mb-2 text-sm font-extrabold text-blue-600">总体评价</h3>
              <p class="text-sm leading-relaxed text-gray-700 whitespace-pre-wrap">{{ detailEvaluation.summary }}</p>
            </div>

            <!-- Highlights -->
            <div v-if="detailEvaluation.highlights" class="mb-4 rounded-2xl bg-emerald-50/80 p-4">
              <h3 class="mb-2 text-sm font-extrabold text-emerald-600">亮点</h3>
              <p class="text-sm leading-relaxed text-gray-700 whitespace-pre-wrap">{{ detailEvaluation.highlights }}</p>
            </div>

            <!-- Improvements -->
            <div v-if="detailEvaluation.improvements" class="mb-4 rounded-2xl bg-amber-50/80 p-4">
              <h3 class="mb-2 text-sm font-extrabold text-amber-600">改进方向</h3>
              <p class="text-sm leading-relaxed text-gray-700 whitespace-pre-wrap">{{ detailEvaluation.improvements }}</p>
            </div>

            <!-- Suggestion -->
            <div v-if="detailEvaluation.suggestion" class="mb-4 rounded-2xl bg-violet-50/80 p-4">
              <h3 class="mb-2 text-sm font-extrabold text-violet-600">学习建议</h3>
              <p class="text-sm leading-relaxed text-gray-700 whitespace-pre-wrap">{{ detailEvaluation.suggestion }}</p>
            </div>

            <!-- Resume Advice -->
            <div v-if="detailEvaluation.resumeAdvice" class="rounded-2xl border border-teal-200 bg-teal-50/80 p-4">
              <h3 class="mb-2 text-sm font-extrabold text-teal-600">简历优化建议</h3>
              <p class="text-sm leading-relaxed text-gray-700 whitespace-pre-wrap">{{ detailEvaluation.resumeAdvice }}</p>
            </div>
          </div>

          <!-- Tab Content: Replay -->
          <div v-else-if="activeTab === 'replay'" class="flex-1 overflow-hidden">
            <InterviewReplayTab :messages="detailMessages" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-in-up {
  animation: fadeInUp 0.5s ease-out forwards;
  opacity: 0;
}

.delay-100 { animation-delay: 0.1s; }
.delay-200 { animation-delay: 0.2s; }

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.modal-enter-active {
  transition: all 0.35s ease-out;
}

.modal-leave-active {
  transition: all 0.2s ease-in;
}

.modal-enter-from {
  opacity: 0;
}

.modal-enter-from > :last-child {
  transform: scale(0.92) translateY(20px);
  opacity: 0;
}

.modal-leave-to {
  opacity: 0;
}

.modal-leave-to > :last-child {
  transform: scale(0.95) translateY(10px);
  opacity: 0;
}
</style>
