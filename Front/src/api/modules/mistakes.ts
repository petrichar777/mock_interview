import { apiClient, unwrap } from '@/api/client'
import type { PageData } from '@/api/types'

export type MistakeItem = {
  questionId: number
  moduleId: number
  moduleName: string
  type: number
  difficulty: number
  stem: string
  lastWrongAt?: string
}

export type MistakeRandomRequest = {
  count?: number
  moduleId?: number
}

export type MistakeRandomResponse = {
  questionIds: number[]
}

export type MistakeStats = {
  todayResolved: number
  consecutiveReviewDays: number
  weeklyResolved: number
  totalMistakes: number
}

export type MistakeModuleStats = {
  moduleId: number
  moduleName: string
  mistakeCount: number
  totalAttempts: number
  correctAttempts: number
  correctRate: number
}

export type MistakeRemoveResponse = {
  removed: boolean
  mistakeCount: number
}

export type MistakeDetailOption = {
  key: string
  text: string
}

export type MistakeDetail = {
  questionId: number
  moduleId: number
  moduleName: string
  type: number
  difficulty: number
  stem: string
  options: MistakeDetailOption[]
  correctAnswers: string[]
  explanations: string[]
  tags: string[]
  lastWrongAt?: string
  lastWrongAnswers: string[]
}

export function listMistakes(page: number, size: number, moduleId?: number, sortBy?: string) {
  return unwrap<PageData<MistakeItem>>(apiClient.get('/api/mistakes', { params: { page, size, moduleId, sortBy } }))
}

export function randomMistakes(body?: MistakeRandomRequest) {
  return unwrap<MistakeRandomResponse>(apiClient.post('/api/mistakes/random', body ?? {}))
}

export function removeMistake(questionId: number) {
  return unwrap<MistakeRemoveResponse>(apiClient.delete(`/api/mistakes/questions/${questionId}`))
}

export function getMistakeStats() {
  return unwrap<MistakeStats>(apiClient.get('/api/mistakes/stats'))
}

export function getMistakeModuleStats(limit?: number) {
  return unwrap<MistakeModuleStats[]>(apiClient.get('/api/mistakes/module-stats', { params: { limit } }))
}

export function getMistakeDetail(questionId: number) {
  return unwrap<MistakeDetail>(apiClient.get(`/api/mistakes/questions/${questionId}`))
}
