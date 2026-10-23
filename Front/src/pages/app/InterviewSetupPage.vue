<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { parseInterviewResume } from '@/api/modules/interview'
import { getDashboardSummary } from '@/api/modules/dashboard'
import { useInterviewStore } from '@/stores/interview'
import { showErrorToast, showSuccessToast } from '@/utils/toast'

const router = useRouter()
const interviewStore = useInterviewStore()
const remainingDailyInterviews = ref<number | null>(null)
const maxDailyInterviews = ref(6)
const continuousCheckinDays = ref(0)
const remainingLoading = ref(true)
const hasCheckinReward = computed(() => continuousCheckinDays.value >= 7)

function isValidRemaining(v: number | null | undefined): v is number {
  return typeof v === 'number' && Number.isFinite(v)
}

const remainingColorClass = computed(() => {
  if (!isValidRemaining(remainingDailyInterviews.value)) return ''
  if (remainingDailyInterviews.value <= 0) return 'text-red-600 bg-red-50 border-red-200'
  if (remainingDailyInterviews.value <= 1) return 'text-orange-600 bg-orange-50 border-orange-200'
  if (remainingDailyInterviews.value <= 3) return 'text-amber-600 bg-amber-50 border-amber-200'
  return 'text-emerald-600 bg-emerald-50 border-emerald-200'
})
const remainingLabel = computed(() => {
  if (!isValidRemaining(remainingDailyInterviews.value)) return ''
  if (remainingDailyInterviews.value <= 0) return '今日面试次数已用完'
  return `今日剩余面试次数 ${remainingDailyInterviews.value}/${maxDailyInterviews.value} 次`
})

const form = reactive({
  jobTitle: '',
  jobDescription: '',
  resumeHighlights: '',
  algorithmDirection: '数组、双指针、动态规划',
  mode: 'ALGORITHM' as 'COMPREHENSIVE' | 'ALGORITHM',
  style: 'GUIDED' as 'GUIDED' | 'PRESSURE',
})
const resumeFile = ref<File | null>(null)
const resumeFileId = ref('')
const extractedResumeText = ref('')
const resumeUploading = ref(false)
const creatingSession = ref(false)
const hasResumeSource = computed(() => !!(resumeFileId.value || extractedResumeText.value.trim()))
const resumeUploadStatusTone = ref<'default' | 'success' | 'error'>('error')
const resumeUploadStatusText = ref('上传后将作为面试上下文直接发送给 AI。')
const isAlgorithmMode = computed(() => form.mode === 'ALGORITHM')
const resumeFileDetail = computed(() => {
  if (!resumeFile.value) {
    return ''
  }
  const typeText = resumeFile.value.type || '未知'
  return `类型：${typeText} · 大小：${formatFileSize(resumeFile.value.size)}`
})
const resumeStatusClass = computed(() => {
  if (resumeUploading.value) {
    return 'text-orange-500'
  }
  if (hasResumeSource.value) {
    return 'text-emerald-500'
  }
  return 'text-rose-500'
})
const resumeStatusText = computed(() => {
  if (resumeUploading.value) {
    return '简历上传识别中，请稍候...'
  }
  if (hasResumeSource.value) {
    return '✓ 简历已上传'
  }
  return '请上传简历文件'
})

function formatFileSize(size: number) {
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function getResumeExtension(fileName: string) {
  const dotIndex = fileName.lastIndexOf('.')
  if (dotIndex < 0) {
    return ''
  }
  return fileName.slice(dotIndex + 1).toLowerCase()
}

const MAX_RESUME_FILE_SIZE = 5 * 1024 * 1024

function isSupportedResumeFile(file: File) {
  const extension = getResumeExtension(file.name)
  return extension === 'pdf' || extension === 'doc' || extension === 'docx'
}

async function onResumeFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) {
    return
  }
  if (file.size > MAX_RESUME_FILE_SIZE) {
    resumeFile.value = null
    resumeFileId.value = ''
    extractedResumeText.value = ''
    resumeUploadStatusTone.value = 'error'
    resumeUploadStatusText.value = '文件过大，仅支持 5MB 以内简历文件。'
    showErrorToast('文件大小不能超过5MB', '上传失败', 'top-right')
    target.value = ''
    return
  }
  if (!isSupportedResumeFile(file)) {
    resumeFile.value = null
    resumeFileId.value = ''
    extractedResumeText.value = ''
    resumeUploadStatusTone.value = 'error'
    resumeUploadStatusText.value = '仅支持 PDF / DOC / DOCX 格式简历。'
    showErrorToast('仅支持上传 PDF / Word（DOC、DOCX）格式', '上传失败', 'top-right')
    target.value = ''
    return
  }
  resumeUploading.value = true
  resumeFile.value = file
  try {
    const data = await parseInterviewResume(file)
    resumeFileId.value = data.resumeFileId
    extractedResumeText.value = data.extractedText?.trim() || ''
    if (data.extractedText?.trim() && !form.resumeHighlights.trim()) {
      form.resumeHighlights = data.extractedText.trim()
    }
    const warningText = data.warnings.filter((item) => item.trim()).join('；')
    if (data.parseStatus === 'FAILED') {
      resumeFileId.value = ''
      extractedResumeText.value = ''
      resumeFile.value = null
      resumeUploadStatusTone.value = 'error'
      resumeUploadStatusText.value = warningText || '简历识别失败，请更换文件重试。'
      showErrorToast(resumeUploadStatusText.value, '上传失败', 'top-right')
      return
    }
    if (data.parseStatus === 'PARTIAL') {
      resumeUploadStatusTone.value = 'success'
      resumeUploadStatusText.value = warningText || '简历上传成功，文本已部分识别。'
      showSuccessToast('简历上传成功，已完成部分识别', '上传成功', 'top-right')
      return
    }
    resumeUploadStatusTone.value = 'success'
    resumeUploadStatusText.value = warningText || '简历上传成功，开始面试后将自动发送给 AI 面试官。'
    showSuccessToast('简历上传并识别成功', '上传成功', 'top-right')
  } catch (error) {
    resumeFileId.value = ''
    extractedResumeText.value = ''
    resumeUploadStatusTone.value = 'error'
    resumeUploadStatusText.value = error instanceof Error ? error.message : '简历上传失败，请稍后重试。'
    showErrorToast(resumeUploadStatusText.value, '上传失败', 'top-right')
    resumeFile.value = null
  } finally {
    resumeUploading.value = false
    target.value = ''
  }
}

async function onCreateSession() {
  if (creatingSession.value) {
    return
  }
  const resumeHighlights = form.resumeHighlights.trim()
  const normalizedJobTitle = form.jobTitle.trim()
  if (!isAlgorithmMode.value && !normalizedJobTitle) {
    showErrorToast('请填写意向岗位', '参数错误', 'top-right')
    return
  }
  const normalizedJdText = form.jobDescription.trim()
  if (!isAlgorithmMode.value && !normalizedJdText) {
    showErrorToast('请填写岗位要求', '参数错误', 'top-right')
    return
  }
  const normalizedAlgorithmDirection = form.algorithmDirection
    .split(/[，,]/)
    .map((item) => item.trim())
    .filter((item) => item)
    .join('，')
  if (isAlgorithmMode.value && !normalizedAlgorithmDirection) {
    showErrorToast('算法模式请填写算法方向', '参数错误', 'top-right')
    return
  }
  if (!resumeFileId.value && !extractedResumeText.value.trim()) {
    showErrorToast('请上传简历文件', '参数错误', 'top-right')
    return
  }
  const normalizedExtractedResumeText = extractedResumeText.value || resumeHighlights
  creatingSession.value = true
  try {
    await interviewStore.initSession({
      jobTitle: isAlgorithmMode.value ? undefined : normalizedJobTitle,
      jdText: isAlgorithmMode.value ? undefined : normalizedJdText,
      resumeFileId: resumeFileId.value || undefined,
      extractedResumeText: normalizedExtractedResumeText || undefined,
      resumeText: resumeHighlights || undefined,
      algorithmDirection: isAlgorithmMode.value ? normalizedAlgorithmDirection : undefined,
      mode: form.mode,
      style: form.style,
    })
    const mode = interviewStore.interviewMode === 'algorithm' ? 'algorithm' : 'comprehensive'
    await router.push(`/app/interview/${interviewStore.sessionId}?mode=${mode}`)
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '创建面试会话失败，请稍后重试。', '创建失败', 'top-right')
    return
  } finally {
    creatingSession.value = false
  }
}

onMounted(async () => {
  window.scrollTo(0, 0)
  try {
    const data = await getDashboardSummary()
    remainingDailyInterviews.value = data.remainingDailyInterviews ?? 6
    maxDailyInterviews.value = data.maxDailyInterviews ?? 6
    continuousCheckinDays.value = data.continuousCheckinDays ?? 0
  } catch {
    remainingDailyInterviews.value = null
  } finally {
    remainingLoading.value = false
  }
})
</script>

<template>
  <main class="mx-auto w-full max-w-2xl flex-grow px-4 py-8 sm:px-6 sm:py-12">
    <div
      class="fade-in-up relative overflow-hidden rounded-3xl border border-white bg-white/80 p-8 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md sm:p-10"
    >
      <div class="absolute -right-16 -top-16 h-48 w-48 rounded-full bg-rose-50 opacity-60 blur-3xl" />
      <div class="absolute -bottom-16 -left-16 h-48 w-48 rounded-full bg-orange-50 opacity-60 blur-3xl" />

      <div class="relative z-10 mb-10 text-center">
        <div
          class="mb-5 inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-tr from-rose-400 to-orange-400 text-white shadow-[0_8px_20px_rgba(244,63,94,0.3)] transition-transform hover:scale-105"
        >
          <svg class="h-8 w-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"
            />
          </svg>
        </div>
        <h1 class="mb-3 text-3xl font-extrabold tracking-tight text-gray-800">配置您的模拟面试</h1>
        <p class="font-medium text-gray-500">提供准确的信息，让 AI 面试官为您量身定制问题。</p>

        <div v-if="!remainingLoading && isValidRemaining(remainingDailyInterviews)" class="relative z-10 mt-6 flex justify-center">
          <div
            class="inline-flex items-center gap-3 rounded-2xl border px-6 py-3 text-sm font-extrabold shadow-sm transition-all"
            :class="remainingColorClass"
          >
            <svg v-if="(remainingDailyInterviews ?? 0) <= 0" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4.5c-.77-.833-2.694-.833-3.464 0L3.34 16.5c-.77.833.192 2.5 1.732 2.5z" />
            </svg>
            <svg v-else-if="(remainingDailyInterviews ?? 0) <= 1" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M13 16h-1v-4h-1m1-4h.01M12 2a10 10 0 100 20 10 10 0 000-20z" />
            </svg>
            <svg v-else class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>{{ remainingLabel }}</span>
          </div>
          <div
            v-if="hasCheckinReward && isValidRemaining(remainingDailyInterviews)"
            class="relative z-10 mt-2 flex justify-center"
          >
            <span class="inline-flex items-center gap-1 rounded-full bg-amber-50 border border-amber-200 px-3 py-1 text-xs font-bold text-amber-700">
              <svg class="h-3.5 w-3.5" fill="currentColor" viewBox="0 0 20 20">
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
              签到奖励 +1 次
            </span>
          </div>
        </div>
        <div v-else-if="remainingLoading" class="relative z-10 mt-6 flex justify-center">
          <div class="inline-flex h-12 w-48 animate-pulse items-center rounded-2xl bg-gray-100" />
        </div>
      </div>

      <form class="fade-in-up delay-100 relative z-10 space-y-6" @submit.prevent="onCreateSession">
        <div v-show="!isAlgorithmMode" class="group">
          <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-rose-500">
            <span class="text-rose-500">*</span> 意向岗位（必填）
          </label>
          <input
            v-model="form.jobTitle"
            type="text"
            class="w-full rounded-2xl border border-white bg-white/60 px-4 py-3.5 text-sm font-medium shadow-sm transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
            placeholder="请输入你面试的目标岗位"
          />
        </div>

        <div v-show="!isAlgorithmMode" class="group">
          <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-rose-500">
            <span class="text-rose-500">*</span> 岗位要求（必填）
          </label>
          <textarea
            v-model="form.jobDescription"
            rows="4"
            class="w-full resize-none rounded-2xl border border-white bg-white/60 px-4 py-3.5 text-sm font-medium leading-relaxed shadow-sm transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
            placeholder="粘贴岗位描述中的核心技能要求，以便 AI 针对性提问。"
          />
        </div>

        <div class="group">
          <label class="mb-2 block text-sm font-extrabold text-gray-700"><span class="text-rose-500">*</span> 上传简历（必填）</label>
          <div class="relative">
            <button
              type="button"
              class="w-full rounded-2xl border border-dashed border-rose-200 bg-rose-50/60 px-4 py-4 text-left transition-all hover:border-rose-300 hover:bg-rose-50"
              :disabled="resumeUploading"
              :class="[resumeUploading ? 'cursor-not-allowed opacity-80' : '', !hasResumeSource && !resumeUploading ? '!border-rose-400 !border-solid !bg-rose-100/80' : '']"
            >
              <span class="flex items-center gap-3">
                <span class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-white text-rose-500 shadow-sm">
                  <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
                  </svg>
                </span>
                <span>
                  <span class="block text-sm font-extrabold text-gray-700">{{ resumeUploading ? '简历识别中...' : '点击上传简历' }}</span>
                  <span class="block text-xs font-medium text-gray-500">仅支持 PDF / Word（DOC、DOCX）</span>
                </span>
              </span>
            </button>
            <input
              type="file"
              accept=".pdf,.doc,.docx"
              class="absolute inset-0 cursor-pointer opacity-0"
              :disabled="resumeUploading"
              @change="onResumeFileChange"
            />
          </div>
          <div
            v-if="resumeFile"
            class="mt-3 rounded-2xl border border-rose-100 bg-white px-4 py-3 text-xs font-bold text-gray-500"
          >
            <p class="truncate text-sm font-extrabold text-gray-700">{{ resumeFile.name }}</p>
            <p class="mt-1">{{ resumeFileDetail }}</p>
          </div>
          <p class="mt-2 text-xs font-bold" :class="resumeStatusClass">{{ resumeStatusText }}</p>
        </div>

        <div class="group">
          <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-rose-500">
            简历亮点（选填）
          </label>
          <textarea
            v-model="form.resumeHighlights"
            rows="3"
            class="w-full resize-none rounded-2xl border border-white bg-white/60 px-4 py-3.5 text-sm font-medium leading-relaxed shadow-sm transition-all duration-300 focus:border-transparent focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-400"
            placeholder="可补充项目成绩、亮点数字和技术栈，帮助 AI 更精准追问。"
          />
        </div>

        <div>
          <label class="mb-3 block text-sm font-extrabold text-gray-700"><span class="text-rose-500">*</span> 面试模式（必填）</label>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <label
              class="style-card relative cursor-pointer rounded-2xl border-2 p-5 shadow-sm"
              :class="form.mode === 'COMPREHENSIVE' ? 'selected border-rose-200 bg-rose-50/60' : 'border-white bg-white/60 hover:shadow-md'"
            >
              <input v-model="form.mode" type="radio" class="absolute opacity-0" value="COMPREHENSIVE" />
              <div class="mb-1 flex items-center gap-3">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-rose-50 text-rose-400">
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M7 8h10M7 12h6m-6 4h10M5 4h14a2 2 0 012 2v12a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2z"
                    />
                  </svg>
                </div>
                <span class="block text-base font-extrabold text-gray-700">综合面试模式</span>
              </div>
              <span class="block pl-11 text-sm font-medium text-gray-500">覆盖基础知识、项目经历、系统设计等综合问题。</span>
            </label>

            <label
              class="style-card selected relative cursor-pointer rounded-2xl border-2 p-5"
              :class="form.mode === 'ALGORITHM' ? 'border-violet-300 bg-violet-50 shadow-[0_4px_6px_-1px_rgba(139,92,246,0.12)]' : 'border-white bg-white/60 shadow-sm hover:shadow-md'"
            >
              <input v-model="form.mode" type="radio" class="absolute opacity-0" value="ALGORITHM" />
              <div class="mb-1 flex items-center gap-3">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-violet-100 text-violet-600">
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6l4 2m-4 8a8 8 0 100-16 8 8 0 000 16z" />
                  </svg>
                </div>
                <span class="block text-base font-extrabold" :class="form.mode === 'ALGORITHM' ? 'text-violet-900' : 'text-gray-700'">算法面试模式</span>
              </div>
              <span class="block pl-11 text-sm font-medium" :class="form.mode === 'ALGORITHM' ? 'text-violet-700/90' : 'text-gray-500'">
                聚焦算法高频题表达、复杂度分析、边界条件与追问变式。
              </span>
              <div v-if="form.mode === 'ALGORITHM'" class="absolute right-5 top-5 text-violet-500">
                <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
                </svg>
              </div>
            </label>
          </div>
          <p v-if="isAlgorithmMode" class="mt-2 text-xs font-bold text-violet-500">算法面试模式下可跳过意向岗位与岗位要求。</p>
        </div>

        <div v-if="form.mode === 'ALGORITHM'" class="rounded-2xl border border-violet-100 bg-violet-50/70 p-4">
          <label class="block text-xs font-extrabold text-violet-700"><span class="text-rose-500">*</span> 算法方向（必填）</label>
          <input
            v-model="form.algorithmDirection"
            type="text"
            class="mt-2 w-full rounded-xl border border-violet-100 bg-white px-3 py-2 text-sm font-bold text-violet-700 outline-none transition-all focus:border-violet-300 focus:ring-2 focus:ring-violet-200"
            placeholder="例如：二叉树、动态规划、滑动窗口"
          />
        </div>

        <div>
          <label class="mb-3 block text-sm font-extrabold text-gray-700"><span class="text-rose-500">*</span> 面试官风格（必填）</label>
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <label
              class="style-card relative cursor-pointer rounded-2xl border-2 p-5"
              :class="form.style === 'GUIDED' ? 'selected border-orange-300 bg-orange-50 shadow-[0_4px_6px_-1px_rgba(251,146,60,0.1)]' : 'border-white bg-white/60 shadow-sm hover:shadow-md'"
            >
              <input v-model="form.style" type="radio" class="absolute opacity-0" value="GUIDED" />
              <div class="mb-1 flex items-center gap-3">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-orange-100 text-orange-500">
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                    />
                  </svg>
                </div>
                <span class="block text-base font-extrabold text-orange-900">循序渐进</span>
              </div>
              <span class="block pl-11 text-sm font-medium text-orange-600/80">导师式提问，给提示方向，先肯定再建议。适合建立信心、系统提升。</span>
              <div v-if="form.style === 'GUIDED'" class="absolute right-5 top-5 text-orange-500">
                <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
                </svg>
              </div>
            </label>

            <label
              class="style-card relative cursor-pointer rounded-2xl border-2 p-5"
              :class="form.style === 'PRESSURE' ? 'selected border-indigo-400 bg-indigo-100/70 shadow-[0_4px_6px_-1px_rgba(99,102,241,0.1)]' : 'border-white bg-white/60 shadow-sm hover:shadow-md'"
            >
              <input v-model="form.style" type="radio" class="absolute opacity-0" value="PRESSURE" />
              <div class="mb-1 flex items-center gap-3">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-rose-50 text-rose-400">
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                </div>
                <span class="block text-base font-extrabold text-gray-700">压力面试</span>
              </div>
              <span class="block pl-11 text-sm font-medium text-gray-500">终面级追问，要量化证据、不给提示。适合模拟大厂高压实战。</span>
              <div v-if="form.style === 'PRESSURE'" class="absolute right-5 top-5 text-indigo-500">
                <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
                </svg>
              </div>
            </label>
          </div>
        </div>

        <div class="mt-8 border-t border-gray-100/50 pt-8">
          <button
            type="submit"
            class="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-rose-400 to-orange-400 py-4 text-lg font-extrabold text-white shadow-[0_8px_20px_rgba(244,63,94,0.3)] transition-all active:scale-95 hover:from-rose-500 hover:to-orange-500 hover:shadow-[0_10px_25px_rgba(244,63,94,0.4)]"
            :disabled="resumeUploading || creatingSession || remainingDailyInterviews === 0"
            :class="(resumeUploading || creatingSession || remainingDailyInterviews === 0) ? 'cursor-not-allowed opacity-70' : ''"
          >
            {{ creatingSession ? '正在创建 Session...' : remainingDailyInterviews === 0 ? '今日面试次数已用完' : '开始面试' }}
            <svg v-if="remainingDailyInterviews !== 0" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </button>
          <p v-if="remainingDailyInterviews === 0" class="mt-3 text-center text-sm font-extrabold text-red-500">
            今日面试次数已用完，明天 0 点重置。连续签到 7 天可每天额外获得 1 次面试机会。
          </p>
          <p v-else class="mt-3 text-center text-xs font-bold text-gray-400">系统将创建 SessionID 并按"问一题 -> 答一题 -> 点评 -> 追问"节奏进行。</p>
          <p class="mt-4 text-center text-xs font-medium text-gray-400">带 <span class="text-rose-500">*</span> 为必填项</p>
        </div>
      </form>
    </div>
  </main>
</template>

<style scoped>
.fade-in-up {
  animation: fadeInUp 0.5s ease-out forwards;
  opacity: 0;
}

.delay-100 {
  animation-delay: 0.1s;
}

.style-card {
  transition: all 0.2s ease-in-out;
}

.style-card:hover:not(.selected) {
  transform: translateY(-2px);
  border-color: #bfdbfe;
  background-color: #f8fafc;
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
