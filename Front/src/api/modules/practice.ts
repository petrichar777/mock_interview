import { apiClient, unwrap } from '@/api/client'

export type PracticeModule = {
  id: string
  name: string
  total: number
  done: number
  categoryCode?: string
  categoryName?: string
  description?: string
  questionTypes?: string[]
  progressPercent?: number
  ctaText?: string
  ctaType?: 'START' | 'CONTINUE' | 'RESTART'
}

export type PracticeDirectionItem = {
  id: number
  code: string
  name: string
  active: boolean
}

export type PracticeCategoryItem = {
  directionId: number | null
  code: string
  name: string
  active: boolean
}

export type PracticeModuleListItem = {
  id: number
  categoryCode: string
  categoryName: string
  name: string
  description: string
  questionTypes: string[]
  totalQuestionCount: number
  doneQuestionCount: number
  progressPercent: number
  ctaText: string
  ctaType: 'START' | 'CONTINUE' | 'RESTART'
}

export type PracticeMistakeEntry = {
  enabled: boolean
  mistakeCount: number
  buttonText: string
}

export type PracticeModuleListResponse = {
  directions: PracticeDirectionItem[]
  categories: PracticeCategoryItem[]
  list: PracticeModuleListItem[]
  mistakeEntry: PracticeMistakeEntry
}

export type QuestionOption = {
  key: string
  text: string
}

export type QuestionDetail = {
  id: number
  moduleId: string
  type: 'SINGLE' | 'MULTI' | 'JUDGE'
  mode?: 'normal' | 'mistake'
  moduleName?: string
  typeText?: string
  difficulty?: number
  difficultyText?: string
  stem: string
  options: QuestionOption[]
  progressCurrent?: number
  progressTotal?: number
  progressPercent?: number
  hasPrev?: boolean
  hasNext?: boolean
}

export type SubmitAnswerRequest = {
  answers: string[]
  mode?: 'normal' | 'mistake'
  timeCostMs?: number
}

export type SubmitAnswerResponse = {
  correct: boolean
  correctAnswers: string[]
  analysis?: string
  explanations?: string[]
  tags: string[]
  autoAddedToMistakes?: boolean
  autoRemovedFromMistakes?: boolean
  hasNext?: boolean
  finished?: boolean
}

export function listPracticeModules(category: PracticeCategoryItem['code'] = 'all') {
  return unwrap<PracticeModuleListResponse>(apiClient.get('/api/practice/modules', { params: { category } }))
}

type PracticeNextQuestionResponse = {
  questionId: number
  moduleId: number
  moduleName: string
  mode: 'normal' | 'mistake'
  type: number
  typeText: string
  difficulty: number
  difficultyText: string
  stem: string
  options: QuestionOption[]
  progressCurrent: number
  progressTotal: number
  progressPercent: number
  hasPrev: boolean
  hasNext: boolean
}

export type AddMistakeResponse = {
  added: boolean
  mistakeCount: number
}

function mapQuestionType(type: number): QuestionDetail['type'] {
  if (type === 2) {
    return 'MULTI'
  }
  if (type === 3) {
    return 'JUDGE'
  }
  return 'SINGLE'
}

function mapPracticeQuestionResponse(data: PracticeNextQuestionResponse): QuestionDetail {
  return {
    id: data.questionId,
    moduleId: String(data.moduleId),
    moduleName: data.moduleName,
    mode: data.mode,
    type: mapQuestionType(data.type),
    typeText: data.typeText,
    difficulty: data.difficulty,
    difficultyText: data.difficultyText,
    stem: data.stem,
    options: data.options,
    progressCurrent: data.progressCurrent,
    progressTotal: data.progressTotal,
    progressPercent: data.progressPercent,
    hasPrev: data.hasPrev,
    hasNext: data.hasNext,
  }
}

export async function getNextQuestion(moduleId: string | number, mode: 'normal' | 'mistake' = 'normal') {
  const data = await unwrap<PracticeNextQuestionResponse>(apiClient.get(`/api/practice/modules/${moduleId}/next`, { params: { mode } }))
  return mapPracticeQuestionResponse(data)
}

export async function getPrevQuestion(moduleId: string | number, mode: 'normal' | 'mistake' = 'normal') {
  const data = await unwrap<PracticeNextQuestionResponse>(apiClient.get(`/api/practice/modules/${moduleId}/prev`, { params: { mode } }))
  return mapPracticeQuestionResponse(data)
}

export function submitAnswer(questionId: number, body: SubmitAnswerRequest) {
  return unwrap<SubmitAnswerResponse>(apiClient.post(`/api/practice/questions/${questionId}/submit`, body))
}

export function addQuestionToMistakes(questionId: number) {
  return unwrap<AddMistakeResponse>(apiClient.post(`/api/practice/questions/${questionId}/mistake`))
}
