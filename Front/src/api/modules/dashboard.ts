import { apiClient, unwrap } from '@/api/client'

export type DashboardSummary = {
  doneQuestionCount: number
  correctRate: number
  interviewCount: number
  totalPracticeTimeMinutes: number
  continuousCheckinDays: number
  remainingDailyInterviews: number
  maxDailyInterviews: number
}

export type CheckinResult = {
  checkedIn: boolean
  continuousDays: number
}

export type CheckinHeatmap = {
  month: string
  checkedDays: number[]
  continuousDays: number
}

export type DashboardRecommendedPracticeItem = {
  moduleId: number
  moduleName: string
  description: string
  ctaText: string
  ctaType: 'START' | 'CONTINUE'
  categoryCode: 'backend' | 'frontend' | 'algorithm'
}

export type DashboardRecommendedPractices = {
  list: DashboardRecommendedPracticeItem[]
}

export function getDashboardSummary() {
  return unwrap<DashboardSummary>(apiClient.get('/api/dashboard/summary'))
}

export function checkinToday() {
  return unwrap<CheckinResult>(apiClient.post('/api/checkin'))
}

export function getCheckinHeatmap(month: string) {
  return unwrap<CheckinHeatmap>(apiClient.get('/api/checkin/heatmap', { params: { month } }))
}

export function getRecommendedPractices(limit = 2) {
  return unwrap<DashboardRecommendedPractices>(apiClient.get('/api/dashboard/recommended-practices', { params: { limit } }))
}
