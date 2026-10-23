<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import {
  type BatchReviewProgress,
  type DraftQuestion,
  type ReviewDraftQuestionResponse,
  deleteAdminQuestion,
  getBatchReviewProgress,
  listDraftQuestions,
  publishDraftQuestion,
  reviewDraftQuestion,
  startBatchReview,
} from '@/api/modules/admin'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'

type DraftAction = 'PUBLISHED' | 'DELETED' | 'PENDING'

interface DraftItem {
  question: DraftQuestion
  reviewResult?: ReviewDraftQuestionResponse
  action?: DraftAction
  reviewing?: boolean
  publishing?: boolean
  deleting?: boolean
}

interface BatchSummary {
  total: number
  published: number
  deleted: number
  pending: number
  errors: number
}

const items = ref<DraftItem[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const batchReviewing = ref(false)
const batchTotal = ref(0)
const batchCurrent = ref(0)
const batchLiveSummary = ref<BatchSummary>({ total: 0, published: 0, deleted: 0, pending: 0, errors: 0 })
const batchCompleted = ref(false)
const batchSummary = ref<BatchSummary>({ total: 0, published: 0, deleted: 0, pending: 0, errors: 0 })

const elapsedSeconds = ref(0)
let elapsedTimer: ReturnType<typeof setInterval> | null = null

let pollTimer: ReturnType<typeof setInterval> | null = null

function formatElapsed(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function estimatedRemaining(): string {
  if (batchCurrent.value === 0 || batchTotal.value === 0) return '--:--'
  const perItem = elapsedSeconds.value / batchCurrent.value
  const remaining = Math.max(0, Math.round(perItem * (batchTotal.value - batchCurrent.value)))
  return formatElapsed(remaining)
}

async function loadDrafts() {
  loading.value = true
  error.value = null
  try {
    const data = await listDraftQuestions()
    items.value = data.list.map((q) => ({ question: q }))
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载草稿列表失败'
    showErrorToast(error.value, '加载失败', 'top-right')
  } finally {
    loading.value = false
  }
}

async function doSingleReview(item: DraftItem) {
  item.reviewing = true
  try {
    const res = await reviewDraftQuestion(item.question.id)
    item.reviewResult = res
    item.action = res.action
    if (res.action === 'PUBLISHED') {
      showActionSuccessToast('审核通过，已自动上架', 'top-right')
    } else if (res.action === 'DELETED') {
      showActionSuccessToast('审核不通过，已自动删除', 'top-right')
      items.value = items.value.filter((i) => i !== item)
    } else {
      showActionSuccessToast('需要人工复审', 'top-right')
    }
  } catch (e) {
    const message = e instanceof Error ? e.message : '审核失败'
    showErrorToast(message, '审核失败', 'top-right')
  } finally {
    item.reviewing = false
  }
}

async function doBatchReview() {
  batchCompleted.value = false
  batchReviewing.value = true
  batchCurrent.value = 0
  batchTotal.value = 0
  batchLiveSummary.value = { total: 0, published: 0, deleted: 0, pending: 0, errors: 0 }
  elapsedSeconds.value = 0
  elapsedTimer = setInterval(() => { elapsedSeconds.value++ }, 1000)

  try {
    const { batchId } = await startBatchReview()

    pollTimer = setInterval(async () => {
      try {
        const state: BatchReviewProgress = await getBatchReviewProgress(batchId)
        batchTotal.value = state.total
        batchCurrent.value = state.current
        batchLiveSummary.value = {
          total: state.total,
          published: state.published,
          deleted: state.deleted,
          pending: state.pending,
          errors: state.errors,
        }

        for (const result of state.results) {
          const item = items.value.find((i) => i.question.id === result.questionId)
          if (item) {
            item.reviewResult = result
            item.action = result.action
            if (result.action === 'DELETED') {
              items.value = items.value.filter((i) => i !== item)
            }
          }
        }

        if (state.completed) {
          batchSummary.value = {
            total: state.total,
            published: state.published,
            deleted: state.deleted,
            pending: state.pending,
            errors: state.errors,
          }
          stopPollingAndFinish()
        }
      } catch {
        // polling error, will retry next interval
      }
    }, 2000)
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '批量审核失败'
    showErrorToast(message, '审核失败', 'top-right')
    stopPollingAndFinish()
  }
}

function stopPollingAndFinish() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
  batchReviewing.value = false
  batchCompleted.value = true
}

function cancelBatchReview() {
  stopPollingAndFinish()
}

async function doPublish(item: DraftItem) {
  item.publishing = true
  try {
    await publishDraftQuestion(item.question.id)
    item.action = 'PUBLISHED'
    showActionSuccessToast('已发布上架', 'top-right')
    items.value = items.value.filter((i) => i !== item)
  } catch (e) {
    const message = e instanceof Error ? e.message : '发布失败'
    showErrorToast(message, '发布失败', 'top-right')
  } finally {
    item.publishing = false
  }
}

async function doDelete(item: DraftItem) {
  if (!window.confirm('确定删除这道草稿题目吗？')) return
  item.deleting = true
  try {
    await deleteAdminQuestion(item.question.id)
    item.action = 'DELETED'
    showActionSuccessToast('已删除', 'top-right')
    items.value = items.value.filter((i) => i !== item)
  } catch (e) {
    const message = e instanceof Error ? e.message : '删除失败'
    showErrorToast(message, '删除失败', 'top-right')
  } finally {
    item.deleting = false
  }
}

function getTypeLabel(type: number) {
  if (type === 1) return '单选题'
  if (type === 2) return '多选题'
  return '判断题'
}

function getDifficultyLabel(difficulty: number) {
  if (difficulty === 1) return '简单'
  if (difficulty === 2) return '中等'
  return '困难'
}

function getDifficultyClass(difficulty: number) {
  if (difficulty === 1) return 'bg-emerald-50 text-emerald-600 border-emerald-100'
  if (difficulty === 2) return 'bg-amber-50 text-amber-600 border-amber-100'
  return 'bg-rose-50 text-rose-600 border-rose-100'
}

function getConfidenceColor(confidence: number) {
  if (confidence >= 8) return 'text-emerald-600'
  if (confidence >= 5) return 'text-amber-600'
  return 'text-rose-600'
}

function getActionLabel(action: string) {
  if (action === 'PUBLISHED') return '已上架'
  if (action === 'DELETED') return '已删除'
  return '待复审'
}

function getActionClass(action: string) {
  if (action === 'PUBLISHED') return 'bg-emerald-50 text-emerald-600 border-emerald-100'
  if (action === 'DELETED') return 'bg-rose-50 text-rose-600 border-rose-100'
  return 'bg-amber-50 text-amber-600 border-amber-100'
}

onMounted(() => {
  loadDrafts()
})

onUnmounted(() => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  batchReviewing.value = false
  if (elapsedTimer) clearInterval(elapsedTimer)
})
</script>

<template>
  <section class="p-8">
    <div class="fade-in mx-auto max-w-4xl">
      <!-- Header -->
      <div class="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="text-2xl font-extrabold text-gray-800">草稿审核</h1>
          <p class="mt-1 text-sm font-medium text-gray-500">
            AI 生成的题目，审核后上架或删除
          </p>
        </div>
        <div class="flex gap-3">
          <RouterLink
            to="/admin/questions/generate"
            class="rounded-xl border border-purple-200 bg-purple-50 px-4 py-2.5 text-sm font-bold text-purple-600 shadow-sm transition-all hover:bg-purple-100"
          >
            生成新题目
          </RouterLink>
          <button
            class="flex items-center gap-2 rounded-xl bg-gradient-to-r from-sky-500 to-cyan-500 px-6 py-2.5 text-sm font-bold text-white shadow-md transition-all hover:from-sky-600 hover:to-cyan-600 disabled:opacity-50"
            :disabled="batchReviewing || loading || items.length === 0"
            @click="doBatchReview"
          >
            <svg v-if="!batchReviewing" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            <span
              v-if="batchReviewing"
              class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"
            />
            <template v-if="batchReviewing">
              {{ batchCurrent }}/{{ batchTotal }} 审核中...
            </template>
            <template v-else>
              一键全部审核
            </template>
          </button>
        </div>
      </div>

      <!-- Batch progress panel -->
      <div
        v-if="batchReviewing"
        class="mb-6 rounded-2xl border border-sky-200 bg-sky-50/80 px-6 py-5 shadow-sm backdrop-blur-sm"
      >
        <!-- Progress bar -->
        <div class="mb-3">
          <div class="mb-2 flex items-center justify-between text-sm">
            <span class="font-extrabold text-sky-700">
              智能审核进行中 ({{ batchCurrent }}/{{ batchTotal }})
            </span>
            <span class="font-bold text-sky-600">
              已耗时 {{ formatElapsed(elapsedSeconds) }} / 预计剩余 {{ estimatedRemaining() }}
            </span>
          </div>
          <div class="h-2.5 w-full overflow-hidden rounded-full bg-sky-200">
            <div
              class="h-full rounded-full bg-gradient-to-r from-sky-500 to-cyan-500 transition-all duration-500 ease-out"
              :style="{ width: batchTotal > 0 ? `${Math.round((batchCurrent / batchTotal) * 100)}%` : '0%' }"
            />
          </div>
        </div>

        <!-- Live counters -->
        <div class="flex flex-wrap items-center gap-4 text-xs font-bold">
          <span class="flex items-center gap-1 rounded-lg bg-emerald-100 px-2.5 py-1 text-emerald-700">
            通过 {{ batchLiveSummary.published }}
          </span>
          <span class="flex items-center gap-1 rounded-lg bg-rose-100 px-2.5 py-1 text-rose-700">
            删除 {{ batchLiveSummary.deleted }}
          </span>
          <span class="flex items-center gap-1 rounded-lg bg-amber-100 px-2.5 py-1 text-amber-700">
            待复审 {{ batchLiveSummary.pending }}
          </span>
          <span
            v-if="batchLiveSummary.errors > 0"
            class="flex items-center gap-1 rounded-lg bg-gray-100 px-2.5 py-1 text-gray-600"
          >
            失败 {{ batchLiveSummary.errors }}
          </span>
          <button
            class="ml-auto rounded-lg border border-sky-200 bg-white px-3 py-1.5 text-xs font-bold text-sky-600 transition-all hover:bg-sky-100"
            @click="cancelBatchReview"
          >
            取消审核
          </button>
        </div>
      </div>

      <!-- Batch completed banner -->
      <div
        v-if="batchCompleted && !batchReviewing"
        class="mb-6 rounded-2xl border border-emerald-200 bg-emerald-50/90 px-6 py-5 shadow-sm backdrop-blur-sm"
      >
        <div class="flex items-start gap-4">
          <div class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-emerald-500 text-white">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>
          <div class="flex-1">
            <p class="text-base font-extrabold text-emerald-700">批量审核完成</p>
            <p class="mt-0.5 text-sm font-medium text-emerald-600">
              共 {{ batchSummary.total }} 题，通过 {{ batchSummary.published }} 题，删除 {{ batchSummary.deleted }} 题，待复审 {{ batchSummary.pending }} 题
              <template v-if="batchSummary.errors > 0">，失败 {{ batchSummary.errors }} 题</template>
            </p>
            <p class="mt-1 text-xs text-emerald-500">
              总耗时 {{ formatElapsed(elapsedSeconds) }}
            </p>
          </div>
          <button
            class="rounded-lg border border-emerald-200 bg-white px-3 py-1.5 text-xs font-bold text-emerald-600 transition-all hover:bg-emerald-100"
            @click="batchCompleted = false"
          >
            关闭
          </button>
        </div>
      </div>

      <!-- Error -->
      <div
        v-if="error"
        class="mb-6 rounded-2xl border border-rose-200 bg-rose-50 p-6 text-center"
      >
        <p class="text-sm font-bold text-rose-600">{{ error }}</p>
        <button
          class="mt-3 rounded-xl bg-rose-500 px-5 py-2 text-sm font-bold text-white transition-all hover:bg-rose-600"
          @click="loadDrafts"
        >
          重试
        </button>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="h-8 w-8 animate-spin rounded-full border-4 border-purple-200 border-t-purple-500" />
      </div>

      <!-- Empty -->
      <div
        v-if="!loading && !error && items.length === 0"
        class="rounded-3xl border border-white bg-white/80 py-16 text-center shadow-sm backdrop-blur-sm"
      >
        <p class="text-sm font-extrabold text-gray-400">暂无待审核的草稿</p>
        <RouterLink
          to="/admin/questions/generate"
          class="mt-3 inline-block rounded-xl bg-purple-500 px-5 py-2.5 text-sm font-bold text-white shadow-sm transition-all hover:bg-purple-600"
        >
          去生成题目
        </RouterLink>
      </div>

      <!-- Draft Cards -->
      <div v-if="!loading && !error && items.length > 0" class="space-y-4">
        <div
          v-for="item in items"
          :key="item.question.id"
          class="rounded-2xl border border-white bg-white/80 p-5 shadow-sm backdrop-blur-sm transition-all hover:shadow-md"
        >
          <!-- Question Info -->
          <div class="mb-2 flex flex-wrap items-center gap-2">
            <span class="rounded-lg border px-2 py-0.5 text-xs font-bold" :class="getDifficultyClass(item.question.difficulty)">
              {{ getDifficultyLabel(item.question.difficulty) }}
            </span>
            <span class="rounded-lg border border-gray-100 bg-gray-50 px-2 py-0.5 text-xs font-bold text-gray-500">
              {{ getTypeLabel(item.question.type) }}
            </span>
            <span class="text-xs font-medium text-gray-400">#{{ item.question.id }}</span>

            <!-- Batch-waiting indicator -->
            <span
              v-if="batchReviewing && !item.reviewResult"
              class="ml-auto inline-flex items-center gap-1.5 rounded-lg border border-sky-200 bg-sky-50 px-2 py-0.5 text-xs font-bold text-sky-600"
            >
              <span class="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-sky-400" />
              等待审核
            </span>

            <!-- Action badge (after review) -->
            <span
              v-if="item.action && !batchReviewing"
              class="ml-auto rounded-lg border px-2 py-0.5 text-xs font-bold"
              :class="getActionClass(item.action)"
            >
              {{ getActionLabel(item.action) }}
            </span>
          </div>

          <!-- Stem -->
          <p class="mb-3 text-sm font-bold leading-relaxed text-gray-800">
            {{ item.question.stem }}
          </p>

          <!-- Options -->
          <div class="mb-3 space-y-1 rounded-xl bg-gray-50 p-3">
            <div
              v-for="opt in item.question.options"
              :key="opt.key"
              class="flex items-start gap-2 text-sm"
            >
              <span
                class="mt-0.5 inline-flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full text-xs font-extrabold"
                :class="
                  opt.correct
                    ? 'bg-emerald-500 text-white'
                    : 'bg-gray-200 text-gray-500'
                "
              >
                {{ opt.key }}
              </span>
              <span :class="opt.correct ? 'font-bold text-emerald-700' : 'font-medium text-gray-600'">
                {{ opt.text }}
              </span>
            </div>
          </div>

          <!-- Analysis -->
          <div v-if="item.question.analysis" class="mb-3 text-xs font-medium leading-relaxed text-gray-500">
            <span class="font-extrabold text-gray-600">解析：</span>{{ item.question.analysis }}
          </div>

          <!-- Review Result -->
          <div
            v-if="item.reviewResult"
            class="mb-3 rounded-xl border p-3"
            :class="
              item.reviewResult.action === 'PUBLISHED'
                ? 'border-emerald-200 bg-emerald-50/50'
                : item.reviewResult.action === 'DELETED'
                  ? 'border-rose-200 bg-rose-50/50'
                  : 'border-amber-200 bg-amber-50/50'
            "
          >
            <div class="mb-1 flex items-center gap-3">
              <span class="text-xs font-extrabold text-gray-500">AI 审核结果</span>
              <span class="text-xs font-bold" :class="getConfidenceColor(item.reviewResult.confidence)">
                置信度 {{ item.reviewResult.confidence }}/10
              </span>
              <span
                class="rounded-lg border px-1.5 py-0.5 text-xs font-extrabold"
                :class="item.reviewResult.correct ? 'border-emerald-200 bg-emerald-50 text-emerald-600' : 'border-rose-200 bg-rose-50 text-rose-600'"
              >
                {{ item.reviewResult.correct ? '通过' : '不通过' }}
              </span>
            </div>
            <ul v-if="item.reviewResult.issues.length > 0" class="space-y-0.5">
              <li
                v-for="(issue, idx) in item.reviewResult.issues"
                :key="idx"
                class="text-xs font-medium text-gray-600"
              >
                - {{ issue }}
              </li>
            </ul>
          </div>

          <!-- Actions -->
          <div
            v-if="!batchReviewing && (!item.action || item.action === 'PENDING')"
            class="flex gap-2 border-t border-gray-100 pt-3"
          >
            <button
              class="flex items-center gap-1.5 rounded-lg bg-emerald-500 px-4 py-2 text-xs font-bold text-white shadow-sm transition-all hover:bg-emerald-600 disabled:opacity-50"
              :disabled="item.publishing || item.reviewing"
              @click="doPublish(item)"
            >
              <span v-if="item.publishing" class="inline-block h-3 w-3 animate-spin rounded-full border-2 border-white/30 border-t-white" />
              通过上架
            </button>
            <button
              class="flex items-center gap-1.5 rounded-lg bg-purple-500 px-4 py-2 text-xs font-bold text-white shadow-sm transition-all hover:bg-purple-600 disabled:opacity-50"
              :disabled="item.reviewing"
              @click="doSingleReview(item)"
            >
              <span v-if="item.reviewing" class="inline-block h-3 w-3 animate-spin rounded-full border-2 border-white/30 border-t-white" />
              AI 审核
            </button>
            <button
              class="flex items-center gap-1.5 rounded-lg border border-rose-200 bg-white px-4 py-2 text-xs font-bold text-rose-600 shadow-sm transition-all hover:bg-rose-50 disabled:opacity-50"
              :disabled="item.deleting || item.reviewing"
              @click="doDelete(item)"
            >
              删除
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.fade-in {
  animation: fadeIn 0.4s ease-out forwards;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
