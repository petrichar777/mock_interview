<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useInterviewStore } from '@/stores/interview'
import { showErrorToast } from '@/utils/toast'
import { renderMessageContent } from '@/utils/markdown'
import InterviewResultModal from '@/features/interview/InterviewResultModal.vue'

const route = useRoute()
const router = useRouter()
const interviewStore = useInterviewStore()
const inputValue = ref('')
const chatContainerRef = ref<HTMLElement | null>(null)
const inputTextareaRef = ref<HTMLTextAreaElement | null>(null)
const inputMaxHeight = 320
const initialLoadDone = ref(false)

const isAlgorithmMode = computed(() => {
  return interviewStore.interviewMode === 'algorithm'
})
const isPressureStyle = computed(() => {
  return interviewStore.interviewStyle === 'PRESSURE'
})
const styleLabel = computed(() => {
  return isPressureStyle.value ? '压力模式' : '引导模式'
})
const styleBadgeClass = computed(() => {
  if (isPressureStyle.value) {
    return 'border-red-100 bg-red-50 text-red-600'
  }
  return 'border-emerald-100 bg-emerald-50 text-emerald-600'
})
const pageTitle = computed(() => {
  if (isAlgorithmMode.value) {
    return '算法工程师实习生 - 算法面试模式'
  }
  return `${interviewStore.interviewTitle} - 模拟面试`
})
function reviewLabel(type: string) {
  if (type === 'POSITIVE') return '答得不错'
  if (type === 'IMPROVE') return '可以改进'
  return '回答一般'
}

function reviewBadgeClass(type: string) {
  if (type === 'POSITIVE') return 'bg-emerald-500 text-white'
  if (type === 'IMPROVE') return 'bg-amber-500 text-white'
  return 'bg-blue-500 text-white'
}

function reviewBorderClass(type: string) {
  if (type === 'POSITIVE') return 'border-l-emerald-400 border-l-[3px]'
  if (type === 'IMPROVE') return 'border-l-amber-400 border-l-[3px]'
  return 'border-l-blue-400 border-l-[3px]'
}

const welcomeText = computed(() => {
  if (interviewStore.welcomeText) {
    return interviewStore.welcomeText
  }
  if (isAlgorithmMode.value) {
    return '算法面试模式已开启，系统将按照固定追问链路进行逐问式对话。'
  }
  return '面试已开始，本次对话记录将被保存。深呼吸，放轻松。'
})
const algoFlowSteps = computed(() => {
  if (interviewStore.algoFlow?.steps?.length) {
    return interviewStore.algoFlow.steps
  }
  return ['朴素解法', '优化思路', '复杂度', '边界 Case', '同类变式']
})
const algoCurrentStep = computed(() => {
  return interviewStore.algoFlow?.currentStep ?? 1
})
const stageHints = [
  '正在竖起耳朵仔细听...',
  'AI 面试官推了推眼镜，陷入沉思...',
  '正在翻阅面试宝典找茬中...',
  '正在脑海中回溯你的项目经历...',
  '正在对照 JD 逐条打分...',
  '键盘敲得飞起，点评马上到...',
  '正在酝酿一个刁钻的追问...',
  '面试官在笔记本上奋笔疾书...',
  '正在回忆大厂面试题库...',
  '正在组织犀利的点评语言...',
  '思考中，这次追问角度会很刁钻...',
  '面试官露出神秘的微笑...',
]

const showReportTip = ref(false)
let reportTipTimer: ReturnType<typeof setTimeout> | null = null

const isOngoing = computed(() => interviewStore.interviewStatus === 'ongoing')
const hasTypingMessage = computed(() => interviewStore.messages.some((message) => message.typing))
const showStandaloneTyping = computed(() => interviewStore.connecting && !hasTypingMessage.value)

const currentStageHint = ref('')
let stageTimer: ReturnType<typeof setInterval> | null = null

function startStageCycle() {
  if (stageTimer) return
  let index = 0
  currentStageHint.value = stageHints[index]
  stageTimer = setInterval(() => {
    index = (index + 1) % stageHints.length
    currentStageHint.value = stageHints[index]
  }, 2500)
}

function stopStageCycle() {
  if (stageTimer) {
    clearInterval(stageTimer)
    stageTimer = null
  }
  currentStageHint.value = ''
}

watch(hasTypingMessage, (typing) => {
  if (typing) {
    startStageCycle()
  } else {
    stopStageCycle()
  }
})

async function initPage() {
  const targetSessionId = String(route.params.sessionId || '')
  if (!targetSessionId) {
    await router.replace('/app/interview/setup')
    return
  }
  await interviewStore.restoreSession(targetSessionId)
  interviewStore.openStream()
  await nextTick()
  initialLoadDone.value = true
}

async function onSend() {
  if (!inputValue.value.trim() || !isOngoing.value || interviewStore.connecting || interviewStore.finishing) {
    return
  }
  const content = inputValue.value.trim()
  inputValue.value = ''
  try {
    await interviewStore.submitUserMessage(content)
    await nextTick()
    scrollToBottom()
  } catch (error) {
    inputValue.value = content
    showErrorToast(error instanceof Error ? error.message : '提交回答失败，请稍后重试。', '提交失败', 'top-right')
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    onSend()
  }
}

function onEndInterview() {
  interviewStore.endInterview().catch((error) => {
    showErrorToast(error instanceof Error ? error.message : '结束面试失败，请稍后重试。', '结束失败', 'top-right')
  })
}

function backToSetup() {
  router.push('/app/interview/setup')
}

function dismissTip() {
  showReportTip.value = false
  if (reportTipTimer) {
    clearTimeout(reportTipTimer)
    reportTipTimer = null
  }
}

function goToReports() {
  dismissTip()
  router.push('/app/reports')
}

function scrollToBottom() {
  if (!chatContainerRef.value) {
    return
  }
  chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight
}

function resizeInputArea() {
  const textarea = inputTextareaRef.value
  if (!textarea) {
    return
  }
  textarea.style.height = 'auto'
  textarea.style.height = `${Math.min(textarea.scrollHeight, inputMaxHeight)}px`
}

onMounted(() => {
  window.scrollTo(0, 0)
  initPage()
  nextTick().then(resizeInputArea)
})

onBeforeUnmount(() => {
  interviewStore.closeStream()
  stopStageCycle()
  if (reportTipTimer) clearTimeout(reportTipTimer)
})

watch(
  () => interviewStore.showEvaluation,
  (visible, wasVisible) => {
    if (wasVisible && !visible && interviewStore.interviewStatus === 'ended') {
      showReportTip.value = true
      if (reportTipTimer) clearTimeout(reportTipTimer)
      reportTipTimer = setTimeout(() => {
        showReportTip.value = false
      }, 8000)
    }
  },
)

watch(
  () => interviewStore.messages.length,
  () => {
    if (initialLoadDone.value) {
      nextTick().then(scrollToBottom)
    }
  },
)

watch(
  () => inputValue.value,
  () => {
    nextTick().then(resizeInputArea)
  },
)
</script>

<template>
  <div class="relative flex h-screen flex-col overflow-hidden bg-slate-50 text-gray-800">
    <div class="pointer-events-none fixed left-0 top-0 -z-10 h-full w-full overflow-hidden">
      <div class="absolute left-[-10%] top-[-10%] h-[40%] w-[40%] rounded-full bg-rose-400 opacity-20 blur-3xl" />
      <div class="absolute right-[-10%] top-[-10%] h-[40%] w-[40%] rounded-full bg-emerald-400 opacity-20 blur-3xl" />
      <div class="absolute bottom-[-20%] left-[20%] h-[40%] w-[40%] rounded-full bg-orange-400 opacity-20 blur-3xl" />
    </div>

    <header
      class="z-10 flex-shrink-0 border-b border-white/50 shadow-[0_4px_30px_rgba(0,0,0,0.03)] backdrop-blur-xl transition-colors duration-300"
      :class="isPressureStyle ? 'bg-slate-100/70 border-b-slate-200/50' : 'bg-white/70 border-b-white/50'"
    >
      <div class="mx-auto flex h-16 max-w-5xl items-center justify-between px-4">
        <div class="flex min-w-0 items-center">
          <button
            type="button"
            class="mr-2 flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full border border-white bg-white/80 text-gray-500 shadow-sm transition-all hover:-translate-x-1 hover:bg-orange-50 hover:text-orange-500 sm:mr-4"
            @click="backToSetup"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
          </button>
          <div class="min-w-0">
            <div class="flex min-w-0 items-center gap-2">
              <h1 class="min-w-0 truncate font-extrabold text-gray-800">{{ pageTitle }}</h1>
              <span
                class="inline-flex flex-shrink-0 items-center gap-1 rounded-lg border px-2.5 py-0.5 text-xs font-extrabold shadow-sm"
                :class="styleBadgeClass"
              >
                <span class="h-1.5 w-1.5 rounded-full" :class="isPressureStyle ? 'bg-red-500' : 'bg-emerald-500'" />
                {{ styleLabel }}
              </span>
            </div>
            <span v-if="isOngoing" class="mt-0.5 hidden items-center text-xs font-bold text-emerald-500 md:flex">
              <span class="relative mr-1.5 flex h-2 w-2">
                <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                <span class="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
              </span>
              SessionID: {{ interviewStore.sessionId }} · 面试进行中
            </span>
            <span v-else class="mt-0.5 block text-xs font-bold text-gray-400">面试已结束</span>
          </div>
        </div>
        <button
          v-if="isOngoing || interviewStore.finishing"
          type="button"
          class="hidden rounded-xl border border-rose-100 bg-rose-50 px-4 py-1.5 text-sm font-bold text-rose-500 shadow-sm transition-colors hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-70 md:inline-flex md:items-center"
          :disabled="interviewStore.finishing"
          @click="onEndInterview"
        >
          <span v-if="interviewStore.finishing && !isOngoing" class="flex items-center gap-1.5">
            <span class="h-2 w-2 animate-pulse rounded-full bg-rose-400" />
            评分中...
          </span>
          <span v-else-if="interviewStore.finishing">结束中...</span>
          <span v-else>结束面试</span>
        </button>
        <button
          v-if="!isOngoing && !interviewStore.finishing"
          type="button"
          class="rounded-xl border border-violet-100 bg-violet-50 px-4 py-1.5 text-sm font-bold text-violet-500 shadow-sm transition-colors hover:bg-violet-100 disabled:opacity-60"
          :disabled="interviewStore.evaluating"
          @click="interviewStore.fetchEvaluation()"
        >
          {{ interviewStore.evaluating ? '生成中...' : interviewStore.evaluation ? '查看评估' : '生成评估报告' }}
        </button>
        <div v-if="interviewStore.evaluationError" class="mt-1 text-center text-xs text-red-500">
          {{ interviewStore.evaluationError }}
        </div>
      </div>
    </header>

    <main
      ref="chatContainerRef"
      class="relative z-10 mx-auto flex w-full max-w-5xl flex-grow flex-col space-y-6 overflow-y-auto p-4 sm:p-6"
    >
      <Transition name="tip">
        <div
          v-if="showReportTip"
          class="flex items-center justify-between rounded-2xl border border-violet-200 bg-violet-50/80 px-5 py-3 backdrop-blur-sm"
        >
          <span class="text-sm font-bold text-violet-700">
            完整报告与对话回放可在 <span class="text-violet-900">面试记录</span> 中查看
          </span>
          <div class="flex items-center gap-2">
            <button
              type="button"
              class="rounded-xl bg-violet-500 px-4 py-1.5 text-xs font-extrabold text-white shadow-sm transition-all hover:bg-violet-600 active:scale-95"
              @click="goToReports"
            >
              立即查看
            </button>
            <button
              type="button"
              class="flex h-7 w-7 items-center justify-center rounded-full text-violet-400 transition-colors hover:bg-violet-100 hover:text-violet-600"
              @click="dismissTip"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      </Transition>
      <div class="my-2 text-center">
        <span
          class="inline-block rounded-full border border-white bg-white/60 px-4 py-1.5 text-xs font-bold text-gray-500 shadow-sm backdrop-blur-sm"
        >
          {{ welcomeText }}
        </span>
      </div>

      <div v-if="isAlgorithmMode" class="max-w-3xl rounded-2xl border border-violet-100 bg-violet-50/70 p-4">
        <p class="mb-3 text-xs font-extrabold text-violet-700">算法追问链路进度</p>
        <div class="grid grid-cols-2 gap-2 sm:grid-cols-5">
          <div
            v-for="(step, index) in algoFlowSteps"
            :key="step"
            class="rounded-xl border px-2 py-2 text-center text-[11px] font-bold"
            :class="index + 1 <= algoCurrentStep ? 'border-violet-200 bg-violet-100 text-violet-700' : 'border-violet-100 bg-white text-violet-600'"
          >
            {{ step }}
          </div>
        </div>
      </div>

      <div
        v-for="message in interviewStore.messages"
        :key="message.id"
        class="flex max-w-3xl items-start"
        :class="
          message.role === 'USER'
            ? 'msg-enter-user self-end flex-row-reverse'
            : message.role === 'SYSTEM'
              ? 'msg-enter self-center justify-center'
              : 'msg-enter'
        "
      >
        <div
          v-if="message.role === 'AI'"
          class="mt-1 flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-2xl bg-gradient-to-tr from-rose-400 to-orange-400 text-white shadow-[0_4px_10px_rgba(251,146,60,0.3)]"
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
            />
          </svg>
        </div>
        <div
          v-else-if="message.role === 'USER'"
          class="mt-1 flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-2xl border-2 border-white bg-white/80 font-extrabold text-gray-500 shadow-sm backdrop-blur-sm"
        >
          Z
        </div>

        <div
          class="px-6 py-4"
          :class="[
            message.role === 'USER'
              ? 'mr-4 rounded-3xl rounded-tr-none bg-gradient-to-br from-orange-400 to-rose-400 text-white shadow-[0_8px_20px_rgba(244,63,94,0.2)]'
              : message.role === 'SYSTEM'
                ? 'rounded-2xl border border-slate-200 bg-slate-100/80 px-4 py-2 text-center text-xs font-semibold text-slate-500'
                : 'ml-4 rounded-3xl rounded-tl-none border border-white bg-white/80 text-gray-700 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md',
            message.role === 'AI' && message.reviewType && !message.typing ? reviewBorderClass(message.reviewType) : '',
          ]"
        >
          <div
            v-if="message.role === 'AI' && message.reviewType && !message.typing"
            class="-mx-6 -mt-4 mb-3 rounded-t-3xl rounded-tl-none px-6 py-2 text-xs font-extrabold text-white"
            :class="reviewBadgeClass(message.reviewType)"
          >
            {{ reviewLabel(message.reviewType) }}
          </div>
          <div
            v-if="!(message.typing && !message.content)"
            class="text-sm font-medium leading-relaxed sm:text-base"
            :class="[
              message.role === 'AI' && message.typing ? 'typing-cursor' : '',
              message.role === 'SYSTEM' ? 'text-xs sm:text-xs' : '',
              message.role === 'AI' && message.contentFormat === 'MARKDOWN' ? 'markdown-content' : 'whitespace-pre-wrap',
            ]"
            v-html="renderMessageContent(message)"
          />
          <div v-else class="flex items-center gap-2 py-1">
            <span class="text-sm font-bold text-gray-400">{{ currentStageHint || '思考中...' }}</span>
            <span class="flex gap-1">
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-rose-400 [animation-delay:0ms]" />
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-rose-400 [animation-delay:150ms]" />
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-rose-400 [animation-delay:300ms]" />
            </span>
          </div>
          <button
            v-if="message.errorRetryable"
            type="button"
            class="mt-2 inline-flex items-center gap-1 rounded-lg border border-orange-200 bg-orange-50 px-3 py-1 text-xs font-bold text-orange-600 shadow-sm transition-colors hover:bg-orange-100 active:scale-95"
            @click="interviewStore.retryFailedMessage(message.id)"
          >
            <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            点击重试
          </button>
        </div>
      </div>

      <div v-if="showStandaloneTyping" class="flex max-w-3xl items-start">
        <div
          class="mt-1 flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-2xl bg-gradient-to-tr from-rose-400 to-orange-400 text-white shadow-[0_4px_10px_rgba(251,146,60,0.3)]"
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
            />
          </svg>
        </div>
        <div
          class="ml-4 flex items-center gap-2 rounded-3xl rounded-tl-none border border-white bg-white/80 px-6 py-4 text-gray-500 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md"
        >
          <span class="text-sm font-bold text-gray-400">{{ currentStageHint || '思考中...' }}</span>
          <span class="flex gap-1">
            <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-rose-400 [animation-delay:0ms]" />
            <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-rose-400 [animation-delay:150ms]" />
            <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-rose-400 [animation-delay:300ms]" />
          </span>
        </div>
      </div>

      <div class="h-4" />
    </main>

    <footer
      class="z-10 flex-shrink-0 border-t border-white/50 bg-white/70 p-4 shadow-[0_-4px_30px_rgba(0,0,0,0.03)] backdrop-blur-xl"
    >
      <div class="mx-auto max-w-4xl">
        <div
          class="flex items-end overflow-hidden rounded-3xl border-2 border-white bg-white/60 shadow-sm transition-all duration-300 focus-within:border-orange-300 focus-within:bg-white focus-within:shadow-[0_8px_30px_rgb(0,0,0,0.04)]"
        >
          <textarea
            ref="inputTextareaRef"
            v-model="inputValue"
            rows="3"
            class="min-h-[84px] max-h-[320px] w-full resize-none overflow-y-auto bg-transparent px-6 py-4 text-sm font-medium text-gray-700 placeholder-gray-400 focus:outline-none sm:text-base"
            :placeholder="isOngoing ? '输入您的回答...' : '面试已结束，无法继续发送'"
            :disabled="!isOngoing || interviewStore.finishing"
            @keydown="onKeydown"
            @input="resizeInputArea"
          />
          <div class="p-3">
            <button
              type="button"
              class="group rounded-2xl bg-gradient-to-r from-orange-400 to-rose-400 p-3.5 text-white shadow-[0_4px_15px_rgba(251,146,60,0.3)] transition-all active:scale-95 hover:-translate-y-0.5 hover:from-orange-500 hover:to-rose-500 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0"
              :disabled="!isOngoing || !inputValue.trim() || interviewStore.connecting || interviewStore.finishing"
              @click="onSend"
            >
              <svg
                class="h-5 w-5 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
              </svg>
            </button>
          </div>
        </div>
        <div class="mt-3 flex items-center justify-center">
          <button
            v-if="isOngoing || interviewStore.finishing"
            type="button"
            class="w-full rounded-xl border border-rose-100 bg-rose-50 px-4 py-2 text-sm font-bold text-rose-500 shadow-sm transition-colors hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-70 md:hidden"
            :disabled="interviewStore.finishing"
            @click="onEndInterview"
          >
            <span v-if="interviewStore.finishing && !isOngoing" class="flex items-center justify-center gap-1.5">
              <span class="h-2 w-2 animate-pulse rounded-full bg-rose-400" />
              评分中...
            </span>
            <span v-else-if="interviewStore.finishing">结束中...</span>
            <span v-else>结束面试</span>
          </button>
          <div class="hidden md:flex items-center gap-4 text-center">
            <span class="flex items-center gap-1 text-xs font-bold text-gray-400">
              <kbd class="rounded-lg border border-white bg-white/80 px-2 py-0.5 text-gray-500 shadow-sm">Enter</kbd> 发送
            </span>
            <span class="flex items-center gap-1 text-xs font-bold text-gray-400">
              <kbd class="rounded-lg border border-white bg-white/80 px-2 py-0.5 text-gray-500 shadow-sm">Shift</kbd> +
              <kbd class="rounded-lg border border-white bg-white/80 px-2 py-0.5 text-gray-500 shadow-sm">Enter</kbd> 换行
            </span>
          </div>
        </div>
      </div>
    </footer>

    <InterviewResultModal
      :visible="interviewStore.showEvaluation"
      :evaluation="interviewStore.evaluation"
      @close="interviewStore.showEvaluation = false"
    />
  </div>
</template>

<style scoped>
.typing-cursor::after {
  content: '|';
  animation: blink 1s step-end infinite;
  color: #6366f1;
}

.msg-enter {
  animation: msgEnter 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
  opacity: 0;
}

.msg-enter-user {
  animation: msgEnterUser 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
  opacity: 0;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin-top: 0.35rem;
  margin-bottom: 0.35rem;
  color: #0f172a;
  font-weight: 700;
  line-height: 1.4;
}

.markdown-content :deep(h1) {
  font-size: 1.2rem;
}

.markdown-content :deep(h2) {
  font-size: 1.1rem;
}

.markdown-content :deep(h3) {
  font-size: 1rem;
}

.markdown-content :deep(p) {
  margin: 0.2rem 0;
  white-space: pre-wrap;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.35rem 0 0.35rem 1.25rem;
}

.markdown-content :deep(li) {
  margin: 0.15rem 0;
}

.markdown-content :deep(code) {
  border-radius: 0.35rem;
  background: #f1f5f9;
  padding: 0.1rem 0.35rem;
  font-size: 0.85em;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

@keyframes msgEnter {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.95);
    transform-origin: top left;
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes msgEnterUser {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.95);
    transform-origin: top right;
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.tip-enter-active {
  transition: all 0.35s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.tip-leave-active {
  transition: all 0.2s ease-in;
}

.tip-enter-from {
  opacity: 0;
  transform: translateY(-12px) scale(0.95);
}

.tip-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
