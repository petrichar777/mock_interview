import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/modules/practice', () => ({
  listPracticeModules: vi.fn(),
  getNextQuestion: vi.fn(),
  getPrevQuestion: vi.fn(),
  submitAnswer: vi.fn(),
  addQuestionToMistakes: vi.fn(),
}))

import { usePracticeStore } from '@/stores/practice'
import * as practiceApi from '@/api/modules/practice'
import type { SubmitAnswerResponse } from '@/api/modules/practice'

function mockSubmitResult(overrides: Partial<SubmitAnswerResponse> = {}): SubmitAnswerResponse {
  return {
    correct: true,
    correctAnswers: ['A'],
    tags: [],
    ...overrides,
  }
}

function mockModuleListResponse() {
  return {
    directions: [],
    categories: [{ directionId: null, code: 'backend' as const, name: '后端', active: true }],
    mistakeEntry: { enabled: false, mistakeCount: 0, buttonText: '暂无错题' },
    list: [
      {
        id: 1,
        name: 'Java 核心基础',
        totalQuestionCount: 50,
        doneQuestionCount: 20,
        categoryCode: 'backend',
        categoryName: '后端',
        description: 'Java基础',
        questionTypes: ['单选', '多选'],
        progressPercent: 40,
        ctaText: '继续',
        ctaType: 'CONTINUE' as const,
      },
    ],
  }
}

function mockQuestionDetail(overrides = {}) {
  return {
    id: 101,
    moduleId: '1',
    moduleName: 'Java 核心基础',
    type: 'SINGLE' as const,
    typeText: '单选题',
    difficulty: 1,
    difficultyText: '简单',
    stem: 'What is Java?',
    options: [
      { key: 'A', text: 'A language' },
      { key: 'B', text: 'A coffee' },
    ],
    progressCurrent: 1,
    progressTotal: 50,
    progressPercent: 2,
    hasPrev: false,
    hasNext: true,
    ...overrides,
  }
}

describe('practice store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('has empty modules and no current question', () => {
      const store = usePracticeStore()
      expect(store.modules).toEqual([])
      expect(store.currentQuestion).toBeNull()
      expect(store.submitResult).toBeNull()
      expect(store.loading).toBe(false)
      expect(store.error).toBe('')
    })
  })

  describe('fetchModules', () => {
    it('loads and transforms module list', async () => {
      vi.mocked(practiceApi.listPracticeModules).mockResolvedValue(mockModuleListResponse())

      const store = usePracticeStore()
      await store.fetchModules('all')

      expect(store.categories).toHaveLength(1)
      expect(store.modules).toHaveLength(1)
      expect(store.modules[0].name).toBe('Java 核心基础')
      expect(store.modules[0].id).toBe('1')
      expect(store.mistakeEntry.enabled).toBe(false)
      expect(store.loading).toBe(false)
    })

    it('sets error on API failure', async () => {
      vi.mocked(practiceApi.listPracticeModules).mockRejectedValue(new Error('Network error'))

      const store = usePracticeStore()
      await store.fetchModules()

      expect(store.error).toBe('Network error')
      expect(store.loading).toBe(false)
    })

    it('sets generic error for non-Error exceptions', async () => {
      vi.mocked(practiceApi.listPracticeModules).mockRejectedValue('unknown')

      const store = usePracticeStore()
      await store.fetchModules()

      expect(store.error).toBe('获取题库失败')
    })
  })

  describe('fetchNextQuestion', () => {
    it('loads and sets current question', async () => {
      vi.mocked(practiceApi.getNextQuestion).mockResolvedValue(mockQuestionDetail())

      const store = usePracticeStore()
      await store.fetchNextQuestion('1')

      expect(store.currentQuestion).not.toBeNull()
      expect(store.currentQuestion!.stem).toBe('What is Java?')
      expect(store.submitResult).toBeNull()
    })

    it('clears previous submit result', async () => {
      vi.mocked(practiceApi.getNextQuestion).mockResolvedValue(mockQuestionDetail())
      const store = usePracticeStore()
      store.submitResult = mockSubmitResult()

      await store.fetchNextQuestion('1')
      expect(store.submitResult).toBeNull()
    })
  })

  describe('fetchPrevQuestion', () => {
    it('loads previous question', async () => {
      vi.mocked(practiceApi.getPrevQuestion).mockResolvedValue(
        mockQuestionDetail({ id: 100, stem: 'Previous Q' }),
      )

      const store = usePracticeStore()
      await store.fetchPrevQuestion('1')

      expect(store.currentQuestion!.stem).toBe('Previous Q')
    })
  })

  describe('submitCurrentAnswer', () => {
    it('returns null when no current question', async () => {
      const store = usePracticeStore()
      const result = await store.submitCurrentAnswer(['A'])
      expect(result).toBeNull()
    })

    it('submits answer and stores result', async () => {
      vi.mocked(practiceApi.submitAnswer).mockResolvedValue(
        mockSubmitResult({ analysis: 'Good job!' }),
      )

      const store = usePracticeStore()
      store.currentQuestion = mockQuestionDetail()
      const result = await store.submitCurrentAnswer(['A'], { mode: 'normal', timeCostMs: 5000 })

      expect(practiceApi.submitAnswer).toHaveBeenCalledWith(101, {
        answers: ['A'],
        mode: 'normal',
        timeCostMs: 5000,
      })
      expect(result).not.toBeNull()
      expect(store.submitResult).not.toBeNull()
      expect(store.submitResult!.correct).toBe(true)
    })
  })

  describe('addCurrentQuestionToMistakes', () => {
    it('returns null when no current question', async () => {
      const store = usePracticeStore()
      const result = await store.addCurrentQuestionToMistakes()
      expect(result).toBeNull()
    })

    it('adds question to mistakes and updates mistake entry', async () => {
      vi.mocked(practiceApi.addQuestionToMistakes).mockResolvedValue({
        mistakeCount: 3,
        added: true,
      })

      const store = usePracticeStore()
      store.currentQuestion = mockQuestionDetail()
      const result = await store.addCurrentQuestionToMistakes()

      expect(result).toEqual({ mistakeCount: 3, added: true })
      expect(store.mistakeEntry.enabled).toBe(true)
      expect(store.mistakeEntry.mistakeCount).toBe(3)
      expect(store.mistakeEntry.buttonText).toBe('进入错题重练')
    })

    it('sets mistake entry disabled when count is 0', async () => {
      vi.mocked(practiceApi.addQuestionToMistakes).mockResolvedValue({
        mistakeCount: 0,
        added: false,
      })

      const store = usePracticeStore()
      store.currentQuestion = mockQuestionDetail()
      await store.addCurrentQuestionToMistakes()

      expect(store.mistakeEntry.enabled).toBe(false)
      expect(store.mistakeEntry.buttonText).toBe('暂无错题')
    })
  })

  describe('setCurrentQuestion', () => {
    it('manually sets the current question', () => {
      const store = usePracticeStore()
      const q = mockQuestionDetail()
      store.setCurrentQuestion(q)
      expect(store.currentQuestion).toEqual(q)
    })

    it('sets null', () => {
      const store = usePracticeStore()
      store.setCurrentQuestion(mockQuestionDetail())
      store.setCurrentQuestion(null)
      expect(store.currentQuestion).toBeNull()
    })
  })

  describe('clearSubmitResult', () => {
    it('clears submit result', () => {
      const store = usePracticeStore()
      store.submitResult = mockSubmitResult({ correct: false })
      store.clearSubmitResult()
      expect(store.submitResult).toBeNull()
    })
  })
})
