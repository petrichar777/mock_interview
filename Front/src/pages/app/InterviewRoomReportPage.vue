<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppErrorState from '@/components/common/AppErrorState.vue'
import AppLoading from '@/components/common/AppLoading.vue'
import { getRoomReport, type RoomReportResponse } from '@/api/modules/room'
import { renderMarkdownSafe } from '@/utils/markdown'
import { showErrorToast } from '@/utils/toast'

const route = useRoute()
const roomCode = computed(() => String(route.params.roomCode || '').trim().toUpperCase())
const report = ref<RoomReportResponse | null>(null)
const loading = ref(true)
const errorMessage = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null
let pollCount = 0

const LEVEL_CLASS_MAP: Record<string, string> = {
  强: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  中: 'bg-amber-50 text-amber-700 border-amber-200',
  弱: 'bg-rose-50 text-rose-700 border-rose-200',
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function levelClass(level: string) {
  return LEVEL_CLASS_MAP[level] || 'bg-slate-50 text-slate-600 border-slate-200'
}

function startPollingIfNeeded() {
  stopPolling()
  if (report.value?.status !== 0) {
    return
  }
  pollCount = 0
  pollTimer = setInterval(async () => {
    pollCount += 1
    if (pollCount > 60) {
      stopPolling()
      errorMessage.value = '报告生成时间较长，请稍后刷新页面查看'
      return
    }
    try {
      const latest = await getRoomReport(roomCode.value)
      report.value = latest
      if (latest.status !== 0) {
        stopPolling()
      }
    } catch {
      // 下一轮继续轮询
    }
  }, 2000)
}

async function loadReport() {
  stopPolling()
  loading.value = true
  errorMessage.value = ''
  try {
    report.value = await getRoomReport(roomCode.value)
    startPollingIfNeeded()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '报告加载失败'
  } finally {
    loading.value = false
  }
}

function onRetryGenerate() {
  showErrorToast('请回到面试房间，由面试官重新点击“结束面试”触发重试', '需要面试官操作', 'top-right')
}

watch(roomCode, () => {
  void loadReport()
}, { immediate: true })

onUnmounted(stopPolling)
</script>

<template>
  <main class="mx-auto w-full max-w-4xl flex-grow px-4 py-8 sm:px-6 lg:py-10">
    <div class="mb-5 flex flex-wrap items-center gap-3">
      <RouterLink :to="`/app/interview/room/${roomCode}`" class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-500 hover:border-rose-200 hover:text-rose-500">← 返回面试房间</RouterLink>
      <div>
        <h1 class="text-xl font-extrabold text-gray-800">双人面试报告</h1>
        <p class="mt-0.5 text-xs font-medium text-gray-500">房间 {{ roomCode }} · AI 根据结构化面试单生成</p>
      </div>
    </div>

    <AppLoading v-if="loading" message="正在加载面试报告..." />
    <AppErrorState v-else-if="errorMessage" :message="errorMessage" @retry="loadReport" />

    <section v-else-if="report" class="space-y-5">
      <div v-if="report.status === 0" class="rounded-3xl border border-white bg-white/85 p-10 text-center shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md">
        <div class="mx-auto mb-4 h-10 w-10 animate-spin rounded-full border-4 border-rose-100 border-t-rose-400" />
        <h2 class="text-lg font-extrabold text-gray-800">AI 正在总结这场面试...</h2>
        <p class="mt-2 text-sm font-medium text-gray-500">通常需要十几秒，页面会自动刷新结果。</p>
      </div>

      <div v-else-if="report.status === 2" class="rounded-3xl border border-rose-100 bg-rose-50/80 p-8 text-center">
        <h2 class="text-lg font-extrabold text-rose-700">报告生成失败</h2>
        <p class="mt-2 text-sm font-medium text-rose-600">{{ report.errorMessage || 'AI 服务暂时不可用' }}</p>
        <p class="mt-4 text-xs font-medium text-rose-500">请回到面试房间，由面试官重新点击“结束面试”触发重试。</p>
        <button type="button" class="mt-4 rounded-xl bg-rose-500 px-4 py-2 text-xs font-extrabold text-white" @click="onRetryGenerate">如何重试？</button>
      </div>

      <template v-else>
        <div class="grid gap-5 sm:grid-cols-[180px_minmax(0,1fr)]">
          <div class="flex flex-col items-center justify-center rounded-3xl border border-white bg-white/85 p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md">
            <span class="text-xs font-extrabold text-slate-400">综合评分</span>
            <div class="mt-2 flex items-end gap-1">
              <span class="bg-gradient-to-r from-rose-500 to-orange-400 bg-clip-text text-5xl font-black text-transparent">{{ report.overallScore ?? 0 }}</span>
              <span class="mb-1 text-xs font-bold text-slate-400">/100</span>
            </div>
            <p class="mt-2 text-center text-[11px] font-medium leading-relaxed text-slate-500">基于已评价题目生成，未评题目不计分</p>
          </div>
          <div class="rounded-3xl border border-white bg-white/85 p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md">
            <h2 class="mb-3 text-sm font-extrabold text-gray-800">总体评价</h2>
            <div class="report-markdown text-sm leading-relaxed text-slate-600" v-html="renderMarkdownSafe(report.summary || report.report?.summary || '')" />
          </div>
        </div>

        <section v-if="report.report?.moduleMastery?.length" class="rounded-3xl border border-white bg-white/85 p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md">
          <h2 class="mb-4 text-sm font-extrabold text-gray-800">模块掌握度</h2>
          <div class="grid gap-3 sm:grid-cols-2">
            <div v-for="module in report.report.moduleMastery" :key="`${module.moduleId}-${module.moduleName}`" class="rounded-2xl border border-slate-100 p-4">
              <div class="flex items-center justify-between gap-2">
                <span class="text-sm font-extrabold text-gray-800">{{ module.moduleName }}</span>
                <span class="rounded-full border px-2.5 py-1 text-[11px] font-extrabold" :class="levelClass(module.level)">{{ module.level }}</span>
              </div>
              <p class="mt-2 text-xs font-medium leading-relaxed text-slate-500">{{ module.comment }}</p>
            </div>
          </div>
        </section>

        <section v-if="report.report?.uncoveredQuestions?.length" class="rounded-2xl border border-amber-100 bg-amber-50/80 p-5">
          <h2 class="text-sm font-extrabold text-amber-700">未覆盖题目</h2>
          <ul class="mt-2 list-inside list-disc space-y-1 text-xs font-medium text-amber-700/90">
            <li v-for="(item, index) in report.report.uncoveredQuestions" :key="`uncovered-${index}`">{{ item }}</li>
          </ul>
        </section>

        <section v-if="report.report?.recommendedQuestions?.length" class="rounded-3xl border border-white bg-white/85 p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="text-sm font-extrabold text-gray-800">推荐练习题目</h2>
            <RouterLink to="/app/practice" class="text-xs font-extrabold text-rose-500 hover:underline">去题库练习 →</RouterLink>
          </div>
          <ol class="space-y-2">
            <li v-for="(question, index) in report.report.recommendedQuestions" :key="question.questionId" class="rounded-2xl border border-slate-100 bg-slate-50/60 px-4 py-3">
              <div class="flex items-start gap-2">
                <span class="text-xs font-extrabold text-slate-400">{{ index + 1 }}.</span>
                <div class="min-w-0">
                  <p class="text-xs font-semibold leading-relaxed text-gray-700">{{ question.questionStem }}</p>
                  <span class="mt-1 inline-block rounded-full bg-indigo-50 px-2 py-0.5 text-[10px] font-bold text-indigo-500">{{ question.moduleName || '未分类' }}</span>
                </div>
              </div>
            </li>
          </ol>
        </section>
      </template>
    </section>
  </main>
</template>

<style scoped>
.report-markdown :deep(h1),
.report-markdown :deep(h2),
.report-markdown :deep(h3) {
  margin: 0.6rem 0 0.35rem;
  font-weight: 800;
  color: #1f2937;
}

.report-markdown :deep(h1) {
  font-size: 1.05rem;
}

.report-markdown :deep(h2) {
  font-size: 0.95rem;
}

.report-markdown :deep(h3) {
  font-size: 0.85rem;
}

.report-markdown :deep(p) {
  margin: 0.25rem 0;
}

.report-markdown :deep(ul),
.report-markdown :deep(ol) {
  margin: 0.25rem 0;
  padding-left: 1.1rem;
  list-style: disc;
}

.report-markdown :deep(strong) {
  font-weight: 800;
  color: #374151;
}

.report-markdown :deep(code) {
  border-radius: 0.25rem;
  background: #f1f5f9;
  padding: 0.1rem 0.25rem;
  font-size: 0.8em;
}
</style>
