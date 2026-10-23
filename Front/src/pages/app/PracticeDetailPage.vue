<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppErrorState from '@/components/common/AppErrorState.vue'
import AppLoading from '@/components/common/AppLoading.vue'
import { getMistakeDetail } from '@/api/modules/mistakes'
import type { QuestionDetail, SubmitAnswerResponse } from '@/api/modules/practice'
import { usePracticeStore } from '@/stores/practice'
import { showErrorToast, showSuccessToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const practiceStore = usePracticeStore()
const answers = ref<string[]>([])
const favorite = ref(false)
const feedbackText = ref('请选择一个选项并提交答案')
const feedbackTone = ref<'gray' | 'rose' | 'emerald' | 'orange'>('gray')
const questionStartedAt = ref(0)
const submissionCache = new Map<number, { answers: string[]; submitResult: SubmitAnswerResponse }>()
const favoritedIds = new Set<number>()
const navigationSeq = ref(0)
const mistakeMode = computed(() => route.query.mode === 'mistake')
const mistakeLoading = ref(false)
const mistakeError = ref('')
const mistakeQuestionIds = computed(() => {
  const raw = route.query.questionIds
  const text = Array.isArray(raw) ? raw[0] : raw
  if (!text) {
    return []
  }
  return text
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isFinite(item) && item > 0)
    .map((item) => Math.floor(item))
})
const strictMistakeMode = computed(() => mistakeMode.value && mistakeQuestionIds.value.length > 0)
const mistakeIndex = computed(() => {
  const raw = Number(route.query.index || 0)
  if (!Number.isFinite(raw)) {
    return 0
  }
  const normalized = Math.max(0, Math.floor(raw))
  if (!strictMistakeMode.value) {
    return normalized
  }
  return Math.min(mistakeQuestionIds.value.length - 1, normalized)
})
const currentQuestion = computed(() => practiceStore.currentQuestion)
const isSubmitted = computed(() => Boolean(practiceStore.submitResult))
const questionTypeText = computed(() => {
  if (practiceStore.currentQuestion?.typeText) {
    return practiceStore.currentQuestion.typeText
  }
  const type = practiceStore.currentQuestion?.type || 'SINGLE'
  if (type === 'MULTI') {
    return '多选题'
  }
  if (type === 'JUDGE') {
    return '判断题'
  }
  return '单选题'
})
const correctAnswers = computed(() => {
  return new Set(practiceStore.submitResult?.correctAnswers || [])
})
const explanationVisible = computed(() => isSubmitted.value)
const explanationLines = computed(() => {
  if (!practiceStore.submitResult) {
    return []
  }
  if (practiceStore.submitResult.explanations?.length) {
    return practiceStore.submitResult.explanations
  }
  if (practiceStore.submitResult.analysis) {
    return [practiceStore.submitResult.analysis]
  }
  return []
})
const explanationTags = computed(() => {
  return practiceStore.submitResult?.tags || []
})
const answerText = computed(() => {
  return practiceStore.submitResult?.correctAnswers.join('、') || '-'
})
const difficultyText = computed(() => {
  return practiceStore.currentQuestion?.difficultyText || '中等'
})
const progressCurrent = computed(() => {
  if (strictMistakeMode.value) {
    return mistakeIndex.value + 1
  }
  return practiceStore.currentQuestion?.progressCurrent || 1
})
const progressTotal = computed(() => {
  if (strictMistakeMode.value) {
    return mistakeQuestionIds.value.length
  }
  return practiceStore.currentQuestion?.progressTotal || 1
})
const canGoPrevInMistake = computed(() => {
  if (!mistakeMode.value) {
    return false
  }
  if (strictMistakeMode.value) {
    return mistakeIndex.value > 0
  }
  return Boolean(practiceStore.currentQuestion?.hasPrev)
})
const canGoPrevInNormal = computed(() => Boolean(practiceStore.currentQuestion?.hasPrev))
const pageLoading = computed(() => {
  if (!mistakeMode.value) {
    return practiceStore.loading
  }
  return strictMistakeMode.value ? mistakeLoading.value : practiceStore.loading
})
const pageError = computed(() => {
  if (!mistakeMode.value) {
    return practiceStore.error
  }
  return strictMistakeMode.value ? mistakeError.value : practiceStore.error
})

function toTypeText(type: number) {
  if (type === 2) {
    return '多选题'
  }
  if (type === 3) {
    return '判断题'
  }
  return '单选题'
}

function toQuestionType(type: number): QuestionDetail['type'] {
  if (type === 2) {
    return 'MULTI'
  }
  if (type === 3) {
    return 'JUDGE'
  }
  return 'SINGLE'
}

function resetQuestionState() {
  answers.value = []
  favorite.value = false
  feedbackText.value = '请选择一个选项并提交答案'
  feedbackTone.value = 'gray'
  practiceStore.clearSubmitResult()
}

function restoreSubmissionIfCached() {
  const qid = practiceStore.currentQuestion?.id
  if (qid && submissionCache.has(qid)) {
    const cached = submissionCache.get(qid)!
    practiceStore.submitResult = cached.submitResult
    answers.value = [...cached.answers]
    applySubmitFeedback()
  } else {
    resetQuestionState()
  }
  favorite.value = qid ? favoritedIds.has(qid) : false
}

async function initNormalModePage() {
  const moduleId = String(route.params.moduleId || '')
  if (!moduleId) {
    return
  }
  resetQuestionState()
  const seq = navigationSeq.value
  await practiceStore.fetchNextQuestion(moduleId, 'normal')
  if (seq !== navigationSeq.value) return
  restoreSubmissionIfCached()
  questionStartedAt.value = Date.now()
}

async function loadStrictMistakeQuestion() {
  const questionId = mistakeQuestionIds.value[mistakeIndex.value]
  if (!questionId) {
    practiceStore.setCurrentQuestion(null)
    mistakeError.value = '错题队列为空，无法开始练习'
    return
  }
  mistakeLoading.value = true
  mistakeError.value = ''
  const seq = navigationSeq.value
  try {
    const detail = await getMistakeDetail(questionId)
    if (seq !== navigationSeq.value) return
    const question: QuestionDetail = {
      id: detail.questionId,
      moduleId: String(detail.moduleId),
      moduleName: detail.moduleName,
      mode: 'mistake',
      type: toQuestionType(detail.type),
      typeText: toTypeText(detail.type),
      difficulty: detail.difficulty,
      difficultyText: detail.difficulty === 3 ? '困难' : detail.difficulty === 2 ? '中等' : '简单',
      stem: detail.stem,
      options: detail.options,
      progressCurrent: mistakeIndex.value + 1,
      progressTotal: mistakeQuestionIds.value.length,
      progressPercent: Math.round(((mistakeIndex.value + 1) / Math.max(1, mistakeQuestionIds.value.length)) * 100),
      hasPrev: mistakeIndex.value > 0,
      hasNext: mistakeIndex.value < mistakeQuestionIds.value.length - 1,
    }
    practiceStore.setCurrentQuestion(question)
    restoreSubmissionIfCached()
    questionStartedAt.value = Date.now()
  } catch (err) {
    practiceStore.setCurrentQuestion(null)
    mistakeError.value = err instanceof Error ? err.message : '获取错题失败'
  } finally {
    mistakeLoading.value = false
  }
}

async function initMistakeModePage() {
  resetQuestionState()
  mistakeError.value = ''
  const seq = navigationSeq.value
  if (strictMistakeMode.value) {
    await loadStrictMistakeQuestion()
    return
  }
  const moduleId = String(route.params.moduleId || '')
  if (!moduleId) {
    return
  }
  await practiceStore.fetchNextQuestion(moduleId, 'mistake')
  if (seq !== navigationSeq.value) return
  restoreSubmissionIfCached()
  questionStartedAt.value = Date.now()
}

async function initPage() {
  if (mistakeMode.value) {
    initMistakeModePage()
    return
  }
  await initNormalModePage()
}

function onSelectOption(optionKey: string) {
  if (isSubmitted.value || !currentQuestion.value) {
    return
  }
  const isMulti = currentQuestion.value.type === 'MULTI'
  if (isMulti) {
    if (answers.value.includes(optionKey)) {
      answers.value = answers.value.filter((item) => item !== optionKey)
      return
    }
    answers.value = [...answers.value, optionKey]
    return
  }
  answers.value = [optionKey]
}

function isSelected(optionKey: string) {
  return answers.value.includes(optionKey)
}

function isCorrect(optionKey: string) {
  return correctAnswers.value.has(optionKey)
}

function optionClass(optionKey: string) {
  if (!isSubmitted.value) {
    return isSelected(optionKey)
      ? 'selected border-orange-300 bg-orange-50'
      : 'border-white bg-white/60 shadow-sm hover:shadow-md'
  }
  if (isCorrect(optionKey)) {
    return 'selected option-correct'
  }
  if (isSelected(optionKey) && !isCorrect(optionKey)) {
    return 'selected option-wrong'
  }
  return 'option-disabled border-white bg-white/60 opacity-80'
}

function getFeedbackClass() {
  if (feedbackTone.value === 'rose') {
    return 'text-rose-500'
  }
  if (feedbackTone.value === 'emerald') {
    return 'text-emerald-600'
  }
  if (feedbackTone.value === 'orange') {
    return 'text-orange-500'
  }
  return 'text-gray-500'
}

function showCollectFeedback() {
  feedbackTone.value = 'orange'
  feedbackText.value = '已加入错题本，稍后可在错题本重练'
}

async function onCollectMistake() {
  if (practiceStore.loading || !practiceStore.currentQuestion) {
    return
  }
  const qid = practiceStore.currentQuestion.id

  if (favorite.value) {
    // Remove from mistakes
    const data = await practiceStore.removeCurrentQuestionFromMistakes()
    if (!data) {
      showErrorToast(practiceStore.error || '移出错题本失败', '操作失败', 'top-right')
      return
    }
    favorite.value = false
    favoritedIds.delete(qid)
    if (!mistakeMode.value) {
      feedbackTone.value = 'gray'
      feedbackText.value = '已移出错题本'
    }
    showSuccessToast('已移出错题本', '已取消收藏', 'top-right')
    return
  }

  // Add to mistakes
  if (mistakeMode.value) return
  const data = await practiceStore.addCurrentQuestionToMistakes()
  if (!data) {
    feedbackTone.value = 'rose'
    feedbackText.value = practiceStore.error || '加入错题本失败'
    showErrorToast(practiceStore.error || '加入错题本失败', '收藏失败', 'top-right')
    return
  }
  favorite.value = true
  favoritedIds.add(qid)
  showCollectFeedback()
  showSuccessToast('已加入错题本，可在错题本中重练', '收藏成功', 'top-right')
}

function applySubmitFeedback() {
  if (!practiceStore.submitResult) {
    return
  }
  const answerSet = correctAnswers.value
  const allMatched = answers.value.length > 0 && answers.value.every((item) => answerSet.has(item))
  const exactMatched = allMatched && answers.value.length === answerSet.size
  if (exactMatched) {
    feedbackTone.value = 'emerald'
    if (practiceStore.submitResult.autoRemovedFromMistakes) {
      feedbackText.value = '回答正确，已从错题本移除'
    } else if (practiceStore.submitResult.finished || practiceStore.submitResult.hasNext === false) {
      feedbackText.value = '回答正确，已完成本轮练习'
    } else {
      feedbackText.value = '回答正确，继续下一题'
    }
  } else {
    feedbackTone.value = practiceStore.submitResult.autoAddedToMistakes ? 'orange' : 'rose'
    feedbackText.value = practiceStore.submitResult.autoAddedToMistakes ? '回答错误，已自动加入错题本' : '回答错误，先看解析再继续'
  }
}

function buildStrictMistakeRoute(index: number) {
  if (!strictMistakeMode.value) {
    return '/app/mistakes'
  }
  const moduleId = String(route.params.moduleId || practiceStore.currentQuestion?.moduleId || '')
  if (!moduleId) {
    return '/app/mistakes'
  }
  return {
    path: `/app/practice/${moduleId}`,
    query: {
      mode: 'mistake',
      index: String(index),
      questionIds: mistakeQuestionIds.value.join(','),
    },
  }
}

async function submitCurrentAnswer() {
  if (!practiceStore.submitResult && answers.value.length === 0) {
    feedbackTone.value = 'rose'
    feedbackText.value = '请先选择答案'
    return
  }
  if (practiceStore.submitResult) {
    if (!mistakeMode.value) {
      if (practiceStore.submitResult.finished || practiceStore.submitResult.hasNext === false) {
        router.push('/app/practice')
        return
      }
      await initNormalModePage()
      return
    }
    if (strictMistakeMode.value) {
      if (mistakeIndex.value < mistakeQuestionIds.value.length - 1) {
        router.push(buildStrictMistakeRoute(mistakeIndex.value + 1))
        return
      }
      router.push('/app/mistakes')
      return
    }
    if (practiceStore.submitResult.finished || practiceStore.submitResult.hasNext === false) {
      router.push('/app/mistakes')
      return
    }
    const moduleId = String(route.params.moduleId || '')
    if (!moduleId) {
      router.push('/app/mistakes')
      return
    }
    await practiceStore.fetchNextQuestion(moduleId, 'mistake')
    resetQuestionState()
    questionStartedAt.value = Date.now()
    return
  }
  const timeCostMs = questionStartedAt.value > 0 ? Math.max(0, Date.now() - questionStartedAt.value) : undefined
  const submitResult = await practiceStore.submitCurrentAnswer(answers.value, {
    mode: mistakeMode.value ? 'mistake' : 'normal',
    timeCostMs,
  })
  if (!submitResult) {
    return
  }
  if (currentQuestion.value) {
    submissionCache.set(currentQuestion.value.id, {
      answers: [...answers.value],
      submitResult: { ...submitResult },
    })
  }
  applySubmitFeedback()
}

async function goPrev() {
  if (mistakeMode.value) {
    if (strictMistakeMode.value) {
      if (mistakeIndex.value > 0) {
        router.push(buildStrictMistakeRoute(mistakeIndex.value - 1))
        return
      }
      router.push('/app/mistakes')
      return
    }
    if (!practiceStore.currentQuestion?.hasPrev) {
      router.push('/app/mistakes')
      return
    }
    const moduleId = String(route.params.moduleId || '')
    if (!moduleId) {
      router.push('/app/mistakes')
      return
    }
    await practiceStore.fetchPrevQuestion(moduleId, 'mistake')
    restoreSubmissionIfCached()
    questionStartedAt.value = Date.now()
    return
  }
  if (!canGoPrevInNormal.value) {
    return
  }
  const moduleId = String(route.params.moduleId || '')
  if (!moduleId) {
    router.push('/app/practice')
    return
  }
  await practiceStore.fetchPrevQuestion(moduleId, 'normal')
  restoreSubmissionIfCached()
  questionStartedAt.value = Date.now()
}

async function onPrimaryAction() {
  await submitCurrentAnswer()
}

const primaryButtonText = computed(() => {
  if (practiceStore.loading) {
    return '处理中...'
  }
  if (!practiceStore.submitResult) {
    return '提交答案'
  }
  if (!mistakeMode.value) {
    if (practiceStore.submitResult.finished) {
      return '完成练习'
    }
    return '下一题'
  }
  if (strictMistakeMode.value && mistakeIndex.value >= mistakeQuestionIds.value.length - 1) {
    return '完成练习'
  }
  if (!strictMistakeMode.value && (practiceStore.submitResult.finished || practiceStore.submitResult.hasNext === false)) {
    return '完成练习'
  }
  return '下一题'
})

onMounted(() => {
  initPage()
})

watch(
  () => [route.params.moduleId, route.query.mode, route.query.index, route.query.questionIds],
  () => {
    navigationSeq.value++
    initPage()
  },
)
</script>

<template>
  <main class="mx-auto flex w-full max-w-3xl flex-grow px-4 py-8 sm:px-6 sm:py-12">
    <AppLoading v-if="pageLoading && !currentQuestion" class="w-full" />
    <AppErrorState v-else-if="pageError && !currentQuestion" class="w-full" :message="pageError" @retry="initPage" />

    <section v-else-if="currentQuestion" class="w-full">
      <!-- 题目信息卡片 -->
      <div class="mb-6 flex items-center gap-3">
        <span class="inline-flex items-center rounded-xl border border-orange-100 bg-orange-50 px-3 py-1.5 text-sm font-bold text-orange-500 shadow-sm">
          {{ questionTypeText }}
        </span>
        <span class="inline-flex items-center gap-1.5 rounded-xl border border-violet-100 bg-violet-50 px-3 py-1.5 text-sm font-bold text-violet-500 shadow-sm">
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
          {{ difficultyText }}
        </span>
        <span class="inline-flex items-center gap-1.5 rounded-xl border border-white bg-white px-3 py-1.5 text-sm font-bold text-gray-500 shadow-sm">
          <span class="text-base font-extrabold text-orange-500">{{ progressCurrent }}</span>
          <span class="text-gray-400">/</span>
          <span>{{ progressTotal }}</span>
        </span>
        <button
          type="button"
          class="ml-auto transition-colors"
          :class="practiceStore.loading ? 'cursor-wait opacity-50 text-gray-300' : favorite ? 'text-rose-500 hover:text-rose-600' : 'text-gray-400 hover:text-rose-500'"
          :disabled="practiceStore.loading"
          @click="onCollectMistake"
        >
          <svg class="h-6 w-6" :fill="favorite ? 'currentColor' : 'none'" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
          </svg>
        </button>
      </div>

      <div class="fade-in-up relative mb-6 overflow-hidden rounded-3xl border border-white bg-white/80 p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-lg sm:p-10">
        <div class="absolute -right-10 -top-10 h-32 w-32 rounded-full bg-orange-50 opacity-60 blur-3xl" />
        <h2 class="relative z-10 mb-8 text-2xl font-extrabold leading-relaxed text-gray-800">
          {{ currentQuestion.stem }}
        </h2>

        <div class="relative z-10 space-y-4">
          <label
            v-for="option in currentQuestion.options"
            :key="option.key"
            class="option-label block cursor-pointer rounded-2xl border-2 p-3.5 transition-all sm:p-5"
            :class="optionClass(option.key)"
          >
            <input
              class="absolute opacity-0"
              :type="!mistakeMode && currentQuestion.type === 'MULTI' ? 'checkbox' : 'radio'"
              name="answer"
              :checked="isSelected(option.key)"
              @change="onSelectOption(option.key)"
            />
            <div class="flex items-start">
              <div
                class="radio-custom mt-0.5 flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full border-2"
                :class="
                  isSubmitted
                    ? isCorrect(option.key)
                      ? 'border-emerald-400 bg-emerald-400 text-white shadow-sm'
                      : isSelected(option.key)
                        ? 'border-rose-300 bg-rose-300 text-white shadow-sm'
                        : 'border-gray-200 bg-white'
                    : isSelected(option.key)
                      ? 'border-orange-400 bg-orange-400 text-white'
                      : 'border-gray-200 bg-white'
                "
              >
                <span v-if="(isSubmitted && isCorrect(option.key)) || (!isSubmitted && isSelected(option.key))" class="text-sm font-bold text-white">√</span>
                <span v-else-if="isSubmitted && isSelected(option.key)" class="text-sm font-bold text-white">✗</span>
                <span v-else class="text-sm font-bold text-gray-400">{{ option.key }}</span>
              </div>
              <span class="ml-4 text-base leading-relaxed" :class="isSubmitted && isCorrect(option.key) ? 'font-bold text-emerald-800' : 'font-semibold text-gray-600'">
                {{ option.text }}
              </span>
            </div>
          </label>
        </div>
      </div>

      <div
        v-if="explanationVisible"
        class="fade-in delay-100 mb-8 rounded-3xl border border-white bg-white/80 p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md sm:p-8"
      >
        <h3 class="mb-4 flex items-center text-lg font-extrabold text-gray-800">
          <div class="mr-3 flex h-8 w-8 items-center justify-center rounded-xl border border-rose-100 bg-rose-50 text-rose-500 shadow-sm">
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2.5"
                d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          </div>
          解析与知识点
        </h3>
        <div class="text-sm font-medium text-gray-600 sm:text-base">
          <div class="mb-4 rounded-2xl border border-white bg-white/60 p-4 shadow-sm">
            <p class="mb-0 flex items-center gap-2 font-bold text-emerald-500">
              <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              正确答案：{{ answerText }}
            </p>
          </div>
          <div class="space-y-3 px-2">
            <p v-for="line in explanationLines" :key="line">{{ line }}</p>
          </div>
          <div class="mt-6 border-t border-gray-100 pt-5">
            <span class="mb-3 block text-xs font-extrabold uppercase tracking-wider text-gray-400">相关知识点标签</span>
            <div class="flex flex-wrap gap-2">
              <span
                v-for="tag in explanationTags"
                :key="tag"
                class="inline-flex cursor-pointer items-center rounded-xl border border-white bg-white px-3 py-1.5 text-sm font-bold text-orange-500 shadow-sm transition-colors hover:bg-orange-50"
              >
                # {{ tag }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="fade-in-up delay-200 flex items-center justify-between">
        <button
          type="button"
          class="flex items-center rounded-2xl border border-white bg-white/80 px-4 py-3 font-bold text-gray-600 shadow-sm backdrop-blur-sm transition-all hover:bg-white active:scale-95 sm:px-6 sm:py-3.5"
          :disabled="mistakeMode ? !canGoPrevInMistake && mistakeIndex === 0 : !canGoPrevInNormal"
          :class="mistakeMode ? (!canGoPrevInMistake && mistakeIndex === 0 ? 'cursor-not-allowed opacity-50' : '') : (!canGoPrevInNormal ? 'cursor-not-allowed opacity-50' : '')"
          @click="goPrev"
        >
          <svg class="h-5 w-5 sm:mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          <span class="hidden sm:inline">上一题</span>
        </button>
        <button
          type="button"
          :disabled="!mistakeMode && practiceStore.loading"
          class="flex items-center rounded-2xl bg-gradient-to-r from-orange-400 to-rose-400 px-5 py-3 font-bold text-white shadow-[0_8px_20px_rgba(251,146,60,0.3)] transition-all hover:-translate-y-0.5 hover:from-orange-500 hover:to-rose-500 active:scale-95 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0 sm:px-8 sm:py-3.5"
          @click="onPrimaryAction"
        >
          {{ primaryButtonText }}
          <svg class="ml-2 h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
      <p class="mt-4 min-h-[20px] text-sm font-bold" :class="getFeedbackClass()">{{ feedbackText }}</p>
    </section>
  </main>
</template>

<style scoped>
.fade-in-up {
  animation: fadeInUp 0.5s ease-out forwards;
  opacity: 0;
}

.fade-in {
  animation: fadeIn 0.5s ease-out forwards;
  opacity: 0;
}

.delay-100 {
  animation-delay: 0.1s;
}

.delay-200 {
  animation-delay: 0.2s;
}

.option-label {
  transition: all 0.2s ease-in-out;
}

.option-label:hover:not(.selected) {
  transform: translateX(4px);
  border-color: #bfdbfe;
  background-color: #f8fafc;
}

.option-label.option-correct {
  border-color: #34d399;
  background-color: rgba(16, 185, 129, 0.08);
}

.option-label.option-wrong {
  border-color: #fb7185;
  background-color: rgba(251, 113, 133, 0.08);
}

.option-label.option-disabled {
  pointer-events: none;
}

.radio-custom {
  transition: all 0.2s ease-in-out;
}

.option-label:hover:not(.selected) .radio-custom {
  border-color: #60a5fa;
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

@keyframes fadeIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}
</style>
