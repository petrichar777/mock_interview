import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  addQuestionToMistakes,
  getNextQuestion,
  getPrevQuestion,
  submitAnswer,
  listPracticeModules,
  type PracticeDirectionItem,
  type PracticeCategoryItem,
  type PracticeModule,
  type PracticeMistakeEntry,
  type QuestionDetail,
  type SubmitAnswerResponse,
} from '@/api/modules/practice'
import { removeMistake } from '@/api/modules/mistakes'

export const usePracticeStore = defineStore('practice', () => {
  const modules = ref<PracticeModule[]>([])
  const directions = ref<PracticeDirectionItem[]>([])
  const categories = ref<PracticeCategoryItem[]>([])
  const mistakeEntry = ref<PracticeMistakeEntry>({
    enabled: false,
    mistakeCount: 0,
    buttonText: '暂无错题',
  })
  const currentQuestion = ref<QuestionDetail | null>(null)
  const submitResult = ref<SubmitAnswerResponse | null>(null)
  const loading = ref(false)
  const error = ref('')

  async function fetchModules(category: PracticeCategoryItem['code'] = 'all') {
    loading.value = true
    error.value = ''
    try {
      const data = await listPracticeModules(category)
      directions.value = data.directions
      categories.value = data.categories
      mistakeEntry.value = data.mistakeEntry
      modules.value = data.list.map((item) => ({
        id: String(item.id),
        name: item.name,
        total: item.totalQuestionCount,
        done: item.doneQuestionCount,
        categoryCode: item.categoryCode,
        categoryName: item.categoryName,
        description: item.description,
        questionTypes: item.questionTypes,
        progressPercent: item.progressPercent,
        ctaText: item.ctaText,
        ctaType: item.ctaType,
      }))
    } catch (err) {
      error.value = err instanceof Error ? err.message : '获取题库失败'
    } finally {
      loading.value = false
    }
  }

  async function fetchNextQuestion(moduleId: string, mode: 'normal' | 'mistake' = 'normal') {
    loading.value = true
    error.value = ''
    try {
      currentQuestion.value = await getNextQuestion(moduleId, mode)
      submitResult.value = null
    } catch (err) {
      error.value = err instanceof Error ? err.message : '获取题目失败'
    } finally {
      loading.value = false
    }
  }

  async function fetchPrevQuestion(moduleId: string, mode: 'normal' | 'mistake' = 'normal') {
    loading.value = true
    error.value = ''
    try {
      currentQuestion.value = await getPrevQuestion(moduleId, mode)
      submitResult.value = null
    } catch (err) {
      error.value = err instanceof Error ? err.message : '获取题目失败'
    } finally {
      loading.value = false
    }
  }

  async function submitCurrentAnswer(
    answers: string[],
    extra?: {
      mode?: 'normal' | 'mistake'
      timeCostMs?: number
    },
  ) {
    if (!currentQuestion.value) {
      return null
    }
    loading.value = true
    error.value = ''
    try {
      submitResult.value = await submitAnswer(currentQuestion.value.id, {
        answers,
        mode: extra?.mode,
        timeCostMs: extra?.timeCostMs,
      })
      return submitResult.value
    } catch (err) {
      error.value = err instanceof Error ? err.message : '提交答案失败'
      return null
    } finally {
      loading.value = false
    }
  }

  async function addCurrentQuestionToMistakes() {
    if (!currentQuestion.value) {
      return null
    }
    loading.value = true
    error.value = ''
    try {
      const data = await addQuestionToMistakes(currentQuestion.value.id)
      mistakeEntry.value = {
        enabled: data.mistakeCount > 0,
        mistakeCount: data.mistakeCount,
        buttonText: data.mistakeCount > 0 ? '进入错题重练' : '暂无错题',
      }
      return data
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加入错题本失败'
      return null
    } finally {
      loading.value = false
    }
  }

  async function removeCurrentQuestionFromMistakes() {
    if (!currentQuestion.value) {
      return null
    }
    loading.value = true
    error.value = ''
    try {
      const data = await removeMistake(currentQuestion.value.id)
      mistakeEntry.value = {
        enabled: data.mistakeCount > 0,
        mistakeCount: data.mistakeCount,
        buttonText: data.mistakeCount > 0 ? '进入错题重练' : '暂无错题',
      }
      return data
    } catch (err) {
      error.value = err instanceof Error ? err.message : '移出错题本失败'
      return null
    } finally {
      loading.value = false
    }
  }

  function setCurrentQuestion(question: QuestionDetail | null) {
    currentQuestion.value = question
  }

  function clearSubmitResult() {
    submitResult.value = null
  }

  return {
    modules,
    directions,
    categories,
    mistakeEntry,
    currentQuestion,
    submitResult,
    loading,
    error,
    fetchModules,
    fetchNextQuestion,
    fetchPrevQuestion,
    submitCurrentAnswer,
    addCurrentQuestionToMistakes,
    removeCurrentQuestionFromMistakes,
    setCurrentQuestion,
    clearSubmitResult,
  }
})
