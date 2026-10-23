<script setup lang="ts">
import { ref, watch, nextTick, computed, onMounted, onUnmounted } from 'vue'
import {
  parseInterviewResume,
  optimizeResumeAsync,
  getResumeOptimizeResult,
  getResumeOptimizeLatest,
  getResumeQuota,
  type ResumeOptimizeResult,
} from '@/api/modules/interview'
import { showErrorToast, showSuccessToast, showWarningToast } from '@/utils/toast'

const resumeText = ref('')
const jobDescription = ref('')
const uploading = ref(false)
const analyzing = ref(false)
const result = ref<ResumeOptimizeResult | null>(null)
const error = ref<string | null>(null)

// Quota
const quotaRemaining = ref<number | null>(null)
const quotaMax = ref(0)

async function fetchQuota() {
  try {
    const q = await getResumeQuota()
    quotaRemaining.value = q.remainingResumeOptimizations
    quotaMax.value = q.maxResumeOptimizations
  } catch {
    // Silently fail — quota badge just won't show
  }
}

// Async polling
const processingTaskId = ref<string | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null
const PENDING_TASK_KEY = 'resume_optimize_pending_task'

function savePendingTask(taskId: string) {
  localStorage.setItem(PENDING_TASK_KEY, taskId)
}

function clearPendingTask() {
  localStorage.removeItem(PENDING_TASK_KEY)
}

function startPolling(taskId: string) {
  stopPolling()
  pollTimer = setInterval(() => pollResult(taskId), 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollResult(taskId: string) {
  try {
    const res = await getResumeOptimizeResult(taskId)
    if (res.status === 'DONE' && res.result) {
      stopPolling()
      clearPendingTask()
      processingTaskId.value = null
      analyzing.value = false
      result.value = res.result
      fetchQuota()
      await nextTick()
      startScoreAnimation()
      startBarAnimation()
      showSuccessToast('简历分析完成')
    } else if (res.status === 'FAILED') {
      stopPolling()
      clearPendingTask()
      processingTaskId.value = null
      analyzing.value = false
      error.value = res.errorMessage || '分析失败，请稍后重试'
    }
    // PROCESSING or NOT_FOUND: keep polling
  } catch {
    // Keep polling on network error
  }
}

async function loadLatestOptimization() {
  try {
    const latest = await getResumeOptimizeLatest()
    if (!latest) return
    if (latest.resumeText) resumeText.value = latest.resumeText
    if (latest.jobDescription) jobDescription.value = latest.jobDescription
    if (latest.status === 'DONE' && latest.result) {
      clearPendingTask()
      result.value = latest.result
      await nextTick()
      startScoreAnimation()
      startBarAnimation()
    } else if (latest.status === 'PROCESSING') {
      // Resume polling — taskId is inferred from pending localStorage
      const taskId = localStorage.getItem(PENDING_TASK_KEY)
      if (taskId) {
        processingTaskId.value = taskId
        analyzing.value = true
        error.value = null
        startPolling(taskId)
      }
    }
    // FAILED: show error but keep inputs for retry
    if (latest.status === 'FAILED') {
      clearPendingTask()
      if (latest.errorMessage) error.value = latest.errorMessage
    }
  } catch {
    // Silently fail — user can start fresh
  }
}

onMounted(() => {
  fetchQuota()
  loadLatestOptimization()
})

onUnmounted(() => {
  stopPolling()
})

// File upload
const uploadedFile = ref<File | null>(null)
const hasResumeSource = computed(() => resumeText.value.trim().length > 0)

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  uploading.value = true
  error.value = null
  try {
    const parsed = await parseInterviewResume(file)
    if (parsed.parseStatus === 'FAILED') {
      const warningText = parsed.warnings?.filter((item: string) => item.trim()).join('；') || '简历识别失败，请更换文件重试。'
      showErrorToast(warningText, '上传失败')
      return
    }
    if (parsed.parseStatus === 'PARTIAL') {
      const warningText = parsed.warnings?.filter((item: string) => item.trim()).join('；') || '简历部分识别，内容可能不完整。'
      showWarningToast(warningText, '上传提醒')
    }
    resumeText.value = parsed.extractedText || ''
    uploadedFile.value = file
  } catch (err) {
    const msg = err instanceof Error ? err.message : '简历解析失败'
    showErrorToast(msg, '上传失败')
  } finally {
    uploading.value = false
    if (input) input.value = ''
  }
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

// Analyze (async)
async function onAnalyze() {
  if (!resumeText.value.trim()) {
    error.value = '请先输入或上传简历内容'
    return
  }
  analyzing.value = true
  error.value = null
  result.value = null
  try {
    const { taskId } = await optimizeResumeAsync(
      resumeText.value.trim(),
      jobDescription.value.trim() || undefined,
    )
    processingTaskId.value = taskId
    savePendingTask(taskId)
    startPolling(taskId)
  } catch (err) {
    analyzing.value = false
    const msg = err instanceof Error ? err.message : '提交失败，请稍后重试'
    error.value = msg
  }
}

// Score animation
const animatedScore = ref(0)
let scoreAnimId = 0
function startScoreAnimation() {
  cancelAnimationFrame(scoreAnimId)
  animatedScore.value = 0
  const target = result.value?.overallScore ?? 0
  const duration = 1000
  const start = performance.now()
  const tick = (now: number) => {
    const elapsed = now - start
    const progress = Math.min(elapsed / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    animatedScore.value = Math.round(eased * target)
    if (progress < 1) {
      scoreAnimId = requestAnimationFrame(tick)
    }
  }
  scoreAnimId = requestAnimationFrame(tick)
}

// Bar animation
const barsVisible = ref(false)
function startBarAnimation() {
  barsVisible.value = false
  setTimeout(() => {
    barsVisible.value = true
  }, 400)
}

// Helpers
function getScoreColor(score: number) {
  if (score >= 80) return '#10b981'
  if (score >= 60) return '#3b82f6'
  if (score >= 40) return '#f59e0b'
  return '#ef4444'
}

function getScoreLabel(score: number) {
  if (score >= 80) return '优秀'
  if (score >= 60) return '良好'
  if (score >= 40) return '一般'
  return '需改进'
}

function getScoreGradient(score: number) {
  if (score >= 80) return 'bg-gradient-to-r from-emerald-400 to-teal-500'
  if (score >= 60) return 'bg-gradient-to-r from-blue-400 to-indigo-500'
  if (score >= 40) return 'bg-gradient-to-r from-amber-400 to-orange-500'
  return 'bg-gradient-to-r from-red-400 to-rose-500'
}

function computeBarColor(score: number, maxScore: number) {
  const ratio = score / maxScore
  if (ratio >= 0.75) return '#10b981'
  if (ratio >= 0.5) return '#f59e0b'
  return '#ef4444'
}

function computeBarBg(score: number, maxScore: number) {
  const ratio = score / maxScore
  if (ratio >= 0.75) return 'bg-emerald-500'
  if (ratio >= 0.5) return 'bg-amber-500'
  return 'bg-red-500'
}

// Reset result when inputs change
watch([resumeText, jobDescription], () => {
  result.value = null
  error.value = null
})
</script>

<template>
  <div class="mx-auto max-w-4xl space-y-8 px-4 py-8 sm:px-6 lg:px-8">
    <!-- Hero header -->
    <div class="text-center">
      <div class="mx-auto mb-4 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-rose-400 to-pink-500 shadow-lg shadow-rose-200">
        <svg class="h-7 w-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
      </div>
      <h1 class="text-2xl font-extrabold text-gray-800 sm:text-3xl">简历优化</h1>
      <p class="mt-2 text-sm leading-relaxed text-gray-500">
        AI 将从<strong class="text-gray-700">完整性、关键词、结构、表达</strong>四个维度分析您的简历，<br class="hidden sm:inline" />
        给出逐段修改建议，并结合面试表现反向复盘
      </p>
    </div>

    <!-- Input card -->
    <div class="overflow-hidden rounded-3xl border border-gray-100 bg-white shadow-lg shadow-gray-100/50">
      <!-- Quota banner -->
      <div
        v-if="quotaRemaining !== null"
        class="border-b border-gray-100 px-5 py-4 sm:px-8 sm:py-5"
        :class="quotaRemaining > 0 ? 'bg-gradient-to-r from-emerald-50/60 to-teal-50/40' : 'bg-gradient-to-r from-red-50/60 to-rose-50/40'"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <span
              class="inline-flex h-10 w-10 items-center justify-center rounded-xl shadow-sm"
              :class="quotaRemaining > 0 ? 'bg-emerald-100 text-emerald-600' : 'bg-red-100 text-red-500'"
            >
              <svg v-if="quotaRemaining > 0" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              <svg v-else class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </span>
            <div>
              <p class="text-sm font-extrabold" :class="quotaRemaining > 0 ? 'text-emerald-800' : 'text-red-700'">
                今日剩余 {{ quotaRemaining }}/{{ quotaMax }} 次
              </p>
              <p class="text-xs" :class="quotaRemaining > 0 ? 'text-emerald-600/80' : 'text-red-500/80'">
                {{ quotaRemaining > 0 ? '每天凌晨重置，请合理使用' : '今日次数已用完，明天再来吧' }}
              </p>
            </div>
          </div>
          <!-- Progress dots -->
          <div class="hidden items-center gap-1 sm:flex">
            <span
              v-for="i in quotaMax"
              :key="i"
              class="h-2 w-2 rounded-full transition-colors"
              :class="i <= quotaRemaining ? 'bg-emerald-400 shadow-sm shadow-emerald-300' : 'bg-gray-200'"
            />
          </div>
        </div>
      </div>

      <!-- Upload zone -->
      <div class="border-b border-gray-50 bg-gradient-to-b from-rose-50/40 to-white px-5 py-6 sm:px-8 sm:py-8">
        <label class="mb-3 block text-sm font-extrabold text-gray-700">
          <span class="inline-flex items-center gap-1.5">
            <svg class="h-4 w-4 text-rose-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
            </svg>
            上传简历
          </span>
        </label>
        <div class="relative">
          <button
            type="button"
            class="w-full rounded-2xl border-2 border-dashed border-rose-200 bg-white px-6 py-6 text-left transition-all hover:border-rose-300 hover:bg-rose-50/60 hover:shadow-md active:scale-[0.99]"
            :class="[uploading ? 'cursor-not-allowed opacity-70' : '', hasResumeSource ? 'border-emerald-300 bg-emerald-50/60' : '']"
            :disabled="uploading"
          >
            <span class="flex items-center gap-4">
              <span
                class="inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-xl shadow-sm transition-colors"
                :class="hasResumeSource ? 'bg-emerald-100 text-emerald-500' : 'bg-rose-100 text-rose-500'"
              >
                <svg v-if="uploading" class="h-6 w-6 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                <svg v-else-if="hasResumeSource" class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M5 13l4 4L19 7" />
                </svg>
                <svg v-else class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
                </svg>
              </span>
              <span>
                <span class="block text-base font-extrabold text-gray-700">
                  {{ uploading ? '简历识别中...' : hasResumeSource ? '简历已上传，点击可重新上传' : '点击上传简历文件' }}
                </span>
                <span class="mt-0.5 block text-sm text-gray-500">支持 PDF、Word（DOC、DOCX），最大 5MB</span>
              </span>
            </span>
          </button>
          <input
            type="file"
            accept=".pdf,.doc,.docx"
            class="absolute inset-0 cursor-pointer opacity-0"
            :disabled="uploading"
            @change="onFileChange"
          />
        </div>

        <!-- Uploaded file info -->
        <div
          v-if="uploadedFile"
          class="mt-3 flex items-center gap-3 rounded-xl border border-emerald-200 bg-emerald-50/70 px-4 py-3"
        >
          <svg class="h-5 w-5 shrink-0 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-extrabold text-gray-700">{{ uploadedFile.name }}</p>
            <p class="text-xs text-gray-500">{{ formatFileSize(uploadedFile.size) }}</p>
          </div>
        </div>
      </div>

      <!-- Resume textarea -->
      <div class="px-5 py-6 sm:px-8 sm:py-6">
        <label class="mb-2 block text-sm font-extrabold text-gray-700">
          简历内容
          <span class="font-normal text-gray-400">— 上传文件后自动填充，也可直接粘贴</span>
        </label>
        <textarea
          v-model="resumeText"
          class="w-full rounded-2xl border border-gray-200 bg-gray-50 p-5 text-sm leading-relaxed text-gray-800 placeholder-gray-400 transition-all focus:border-rose-300 focus:bg-white focus:shadow-md focus:shadow-rose-100 focus:outline-none focus:ring-2 focus:ring-rose-100"
          rows="14"
          placeholder="上传 PDF/Word 文件后这里会自动填充简历文本，您也可以直接在此粘贴简历内容...&#10;&#10;示例结构：&#10;教育背景：XX大学 计算机科学 本科 2019-2023&#10;技术栈：Java、Spring Boot、MySQL、Redis、Docker...&#10;&#10;项目经历：&#10;1. XX商城后台系统 — 负责核心交易链路开发，基于 Spring Boot + MyBatis，日均处理订单 10w+&#10;2. ..."
        />
      </div>

      <!-- JD input -->
      <div class="border-t border-gray-50 px-5 py-6 sm:px-8 sm:py-6">
        <label class="mb-2 block text-sm font-extrabold text-gray-700">
          岗位描述（JD）
          <span class="font-normal text-gray-400">— 可选，填写后可获得匹配度分析</span>
        </label>
        <textarea
          v-model="jobDescription"
          class="w-full rounded-2xl border border-gray-200 bg-gray-50 p-4 text-sm leading-relaxed text-gray-800 placeholder-gray-400 transition-all focus:border-rose-300 focus:bg-white focus:shadow-md focus:shadow-rose-100 focus:outline-none focus:ring-2 focus:ring-rose-100"
          rows="4"
          placeholder="粘贴目标岗位的 JD 描述，AI 将分析简历与岗位的匹配程度，并指出需要加强的方向..."
        />
      </div>

      <!-- Error -->
      <div
        v-if="error"
        class="mx-5 mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-600 sm:mx-8 sm:mb-6"
      >
        <span class="flex items-center gap-2">
          <svg class="h-4 w-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {{ error }}
        </span>
      </div>

      <!-- Analyze button -->
      <div class="border-t border-gray-50 px-5 py-5 sm:px-8 sm:py-6">
        <button
          type="button"
          class="group relative w-full overflow-hidden rounded-2xl bg-gradient-to-r from-rose-400 to-pink-500 py-4 text-base font-extrabold text-white shadow-lg shadow-rose-200 transition-all active:scale-[0.98] hover:-translate-y-0.5 hover:shadow-xl hover:shadow-rose-300 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 disabled:hover:shadow-lg disabled:hover:shadow-rose-200 disabled:active:scale-100"
          :disabled="analyzing || !resumeText.trim()"
          @click="onAnalyze"
        >
          <span
            v-if="analyzing"
            class="flex items-center justify-center gap-2"
          >
            <svg class="h-5 w-5 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            AI 正在分析简历...
          </span>
          <span v-else class="flex items-center justify-center gap-2">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            开始分析
          </span>
        </button>
      </div>
    </div>

    <!-- Loading / processing state -->
    <div
      v-if="analyzing && !result"
      class="flex flex-col items-center justify-center rounded-3xl border border-gray-100 bg-white py-20 shadow-lg shadow-gray-100/50"
    >
      <div class="relative">
        <div class="h-16 w-16 animate-spin rounded-full border-[3px] border-rose-100 border-t-rose-500" />
        <div class="absolute inset-0 flex items-center justify-center">
          <svg class="h-7 w-7 text-rose-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        </div>
      </div>
      <p class="mt-6 text-base font-extrabold text-gray-700">AI 正在分析简历</p>
      <p class="mt-1.5 text-sm text-gray-500">从四个维度综合评估，大约需要 10-20 秒</p>
      <p class="mt-4 rounded-2xl bg-rose-50 px-5 py-3 text-xs font-bold text-rose-600">
        分析在后台进行，您可以离开此页面，完成后将自动显示结果
      </p>
    </div>

    <!-- Results -->
    <div v-if="result" class="space-y-6">
      <!-- Score hero card -->
      <div class="overflow-hidden rounded-3xl border border-gray-100 bg-white shadow-lg shadow-gray-100/50">
        <div class="flex flex-col items-center gap-6 p-6 sm:flex-row sm:p-8">
          <!-- Score ring -->
          <div class="flex flex-col items-center">
            <div class="relative">
              <svg class="h-32 w-32 -rotate-90" viewBox="0 0 120 120">
                <circle cx="60" cy="60" r="52" fill="none" stroke="#f1f5f9" stroke-width="8" />
                <circle
                  cx="60" cy="60" r="52"
                  fill="none"
                  :stroke="getScoreColor(animatedScore)"
                  stroke-width="8"
                  stroke-linecap="round"
                  :stroke-dasharray="2 * Math.PI * 52"
                  :stroke-dashoffset="2 * Math.PI * 52 * (1 - animatedScore / 100)"
                  class="transition-all duration-700 ease-out"
                />
              </svg>
              <div class="absolute inset-0 flex flex-col items-center justify-center">
                <span class="text-[2.5rem] font-black leading-none text-gray-800 transition-colors duration-700" :style="{ color: getScoreColor(animatedScore) }">
                  {{ animatedScore }}
                </span>
                <span class="text-xs font-bold text-gray-400">/ 100</span>
              </div>
            </div>
            <span
              class="mt-2 inline-flex items-center gap-1 rounded-full px-3 py-1 text-sm font-extrabold text-white shadow-md"
              :class="getScoreGradient(animatedScore)"
            >
              {{ getScoreLabel(animatedScore) }}
            </span>
          </div>

          <!-- Summary -->
          <div class="flex-1 text-center sm:text-left">
            <h2 class="text-xl font-extrabold text-gray-800">简历综合评分</h2>
            <p class="mt-2 text-sm leading-relaxed text-gray-600">{{ result.summary }}</p>

            <!-- Mini dimension dots -->
            <div class="mt-4 flex flex-wrap justify-center gap-3 sm:justify-start">
              <span
                v-for="dim in result.dimensions"
                :key="dim.name"
                class="inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-bold"
                :style="{ borderColor: computeBarColor(dim.score, dim.maxScore), color: computeBarColor(dim.score, dim.maxScore) }"
              >
                <span class="h-1.5 w-1.5 rounded-full" :style="{ backgroundColor: computeBarColor(dim.score, dim.maxScore) }" />
                {{ dim.name }} {{ dim.score }}
              </span>
            </div>
          </div>
        </div>

        <!-- Dimension bars -->
        <div class="border-t border-gray-50 bg-gray-50/50 px-6 py-5 sm:px-8 sm:py-6">
          <div class="space-y-4">
            <div
              v-for="(dim, index) in result.dimensions"
              :key="dim.name"
              class="space-y-1.5"
            >
              <div class="flex items-center justify-between text-sm font-bold">
                <span class="text-gray-700">{{ dim.name }}</span>
                <span class="tabular-nums text-gray-500">{{ dim.score }}<span class="text-gray-300">/{{ dim.maxScore }}</span></span>
              </div>
              <div class="h-3 w-full overflow-hidden rounded-full bg-gray-200/80">
                <div
                  class="h-full rounded-full transition-all duration-700 ease-out"
                  :class="computeBarBg(dim.score, dim.maxScore)"
                  :style="{
                    width: barsVisible ? (dim.score / dim.maxScore) * 100 + '%' : '0%',
                    transitionDelay: index * 120 + 'ms',
                  }"
                />
              </div>
              <p class="text-xs leading-relaxed text-gray-500">{{ dim.comment }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- JD Match -->
      <div
        v-if="result.jdMatch"
        class="rounded-3xl border border-blue-200 bg-gradient-to-br from-blue-50 to-indigo-50/50 p-6 shadow-md shadow-blue-100/50 sm:p-8"
      >
        <h3 class="flex items-center gap-2 text-base font-extrabold text-blue-700">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          JD 匹配分析
        </h3>
        <p class="mt-2 text-sm leading-relaxed text-gray-700">{{ result.jdMatch }}</p>
      </div>

      <!-- Section-by-section advice -->
      <div
        v-if="result.sectionAdvice && result.sectionAdvice.length > 0"
        class="rounded-3xl border border-gray-100 bg-white p-6 shadow-lg shadow-gray-100/50 sm:p-8"
      >
        <h3 class="flex items-center gap-2 text-base font-extrabold text-gray-700">
          <svg class="h-5 w-5 text-teal-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
          </svg>
          逐段优化建议
        </h3>
        <div class="mt-4 grid gap-4 sm:grid-cols-2">
          <div
            v-for="section in result.sectionAdvice"
            :key="section.sectionName"
            class="rounded-2xl border border-teal-100 bg-gradient-to-b from-teal-50/60 to-white p-5 transition-all hover:shadow-md hover:shadow-teal-100/50"
          >
            <h4 class="flex items-center gap-1.5 text-sm font-extrabold text-teal-700">
              <span class="text-teal-400">#</span>
              {{ section.sectionName }}
            </h4>
            <p class="mt-2 text-xs leading-relaxed text-gray-500">
              <span class="font-bold text-gray-400">当前内容</span>
              <br />
              {{ section.originalSummary }}
            </p>
            <div class="mt-3 rounded-xl border border-teal-100 bg-white p-3">
              <p class="text-sm leading-relaxed text-gray-700">
                <span class="font-bold text-teal-600">修改建议</span>
                <br />
                {{ section.suggestion }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Interview insights -->
      <div
        v-if="result.interviewInsights && result.interviewInsights.length > 0"
        class="rounded-3xl border border-violet-200 bg-gradient-to-br from-violet-50 to-purple-50/50 p-6 shadow-md shadow-violet-100/50 sm:p-8"
      >
        <h3 class="flex items-center gap-2 text-base font-extrabold text-violet-700">
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
          </svg>
          面试联动复盘
        </h3>
        <p class="mt-1 text-xs text-violet-500">
          基于您过往模拟面试中暴露的弱点，建议在简历中重点关注以下方面
        </p>
        <ul class="mt-4 space-y-2">
          <li
            v-for="(insight, index) in result.interviewInsights"
            :key="index"
            class="flex gap-3 rounded-xl bg-white/80 px-4 py-3 text-sm leading-relaxed text-gray-700 shadow-sm"
          >
            <span class="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-violet-200 text-xs font-extrabold text-violet-600">
              {{ index + 1 }}
            </span>
            <span>{{ insight }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
