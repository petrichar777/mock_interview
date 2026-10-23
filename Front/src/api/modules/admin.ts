import { apiClient, unwrap } from '@/api/client'
import type { PageData } from '@/api/types'

export type AdminUser = {
  id: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  status: number
  createdAt: string
  extraInterviewQuota: number
  extraResumeQuota: number
}

export type ListAdminUsersParams = {
  keyword?: string
  role?: 'USER' | 'ADMIN'
  status?: 0 | 1
  page: number
  size: number
}

export type UpdateAdminUserStatusRequest = {
  status: 0 | 1
}

export type UpdateAdminUserStatusResponse = {
  userId: number
  status: number
}

export type GrantQuotaRequest = {
  interviewQuota: number
  resumeQuota: number
}

export function listAdminUsers(params: ListAdminUsersParams) {
  return unwrap<PageData<AdminUser>>(apiClient.get('/api/admin/users', { params }))
}

export function updateAdminUserStatus(userId: number, body: UpdateAdminUserStatusRequest) {
  return unwrap<UpdateAdminUserStatusResponse>(apiClient.patch(`/api/admin/users/${userId}/status`, body))
}

export function grantQuota(userId: number, body: GrantQuotaRequest) {
  return unwrap<void>(apiClient.post(`/api/admin/users/${userId}/quota`, body))
}

export function clearQuota(userId: number) {
  return unwrap<void>(apiClient.delete(`/api/admin/users/${userId}/quota`))
}

export type AdminDirection = {
  id: number
  code: string
  name: string
  sort: number
  status: number
  createdAt?: string
}

export type AdminCategory = {
  id: number
  directionId: number
  code: string
  name: string
  sort: number
  status: number
  createdAt?: string
}

export type AdminModule = {
  id: number
  categoryId: number
  categoryName: string
  categoryCode: string
  directionId: number
  directionName: string
  directionCode: string
  name: string
  status: number
  sort: number
  description?: string
  questionCount: number
}

export type ListAdminModulesParams = {
  categoryId?: number
  categoryName?: string
  status?: 0 | 1
}

export type ListAdminModulesResponse = {
  list: AdminModule[]
}

export type SaveAdminModuleRequest = {
  categoryId: number
  name: string
  description?: string
  sort?: number
  status?: 0 | 1
}

export type SaveDirectionRequest = {
  code: string
  name: string
  sort?: number
  status?: number
}

export type SaveCategoryRequest = {
  directionId: number
  code: string
  name: string
  sort?: number
  status?: number
}

export function listAdminDirections() {
  return unwrap<AdminDirection[]>(apiClient.get('/api/admin/directions'))
}

export function createDirection(body: SaveDirectionRequest) {
  return unwrap<{ id: number }>(apiClient.post('/api/admin/directions', body))
}

export function updateDirection(directionId: number, body: SaveDirectionRequest) {
  return unwrap<{ directionId: number }>(apiClient.put(`/api/admin/directions/${directionId}`, body))
}

export function updateDirectionStatus(directionId: number, status: number) {
  return unwrap<{ directionId: number; status: number }>(
    apiClient.patch(`/api/admin/directions/${directionId}/status`, { status }),
  )
}

export function deleteDirection(directionId: number) {
  return unwrap<{ directionId: number }>(apiClient.delete(`/api/admin/directions/${directionId}`))
}

export function listAdminCategories() {
  return unwrap<AdminCategory[]>(apiClient.get('/api/admin/categories'))
}

export function createCategory(body: SaveCategoryRequest) {
  return unwrap<{ id: number }>(apiClient.post('/api/admin/categories', body))
}

export function updateCategory(categoryId: number, body: SaveCategoryRequest) {
  return unwrap<{ categoryId: number }>(apiClient.put(`/api/admin/categories/${categoryId}`, body))
}

export function updateCategoryStatus(categoryId: number, status: number) {
  return unwrap<{ categoryId: number; status: number }>(
    apiClient.patch(`/api/admin/categories/${categoryId}/status`, { status }),
  )
}

export function deleteCategory(categoryId: number) {
  return unwrap<{ categoryId: number }>(apiClient.delete(`/api/admin/categories/${categoryId}`))
}

export function listAdminModules(params: ListAdminModulesParams) {
  return unwrap<ListAdminModulesResponse>(apiClient.get('/api/admin/modules', { params }))
}

export function createAdminModule(body: SaveAdminModuleRequest) {
  return unwrap<{ moduleId: number }>(apiClient.post('/api/admin/modules', body))
}

export function updateAdminModule(moduleId: number, body: SaveAdminModuleRequest) {
  return unwrap<{ moduleId: number }>(apiClient.put(`/api/admin/modules/${moduleId}`, body))
}

export function deleteAdminModule(moduleId: number) {
  return unwrap<{ moduleId: number }>(apiClient.delete(`/api/admin/modules/${moduleId}`))
}

export type AdminQuestion = {
  id: number
  stem: string
  type: number
  difficulty: number
  moduleId: number
  moduleName: string
  status: number
  updatedAt: string
}

export type ListAdminQuestionsParams = {
  keyword?: string
  moduleId?: number
  type?: 1 | 2 | 3
  difficulty?: 1 | 2 | 3
  status?: 0 | 1 | 2
  page: number
  size: number
}

export function listAdminQuestions(params: ListAdminQuestionsParams) {
  return unwrap<PageData<AdminQuestion>>(apiClient.get('/api/admin/questions', { params }))
}

export type AdminQuestionOption = {
  key: string
  text: string
  correct: boolean
  sort: number
}

export type AdminQuestionDetail = {
  id: number
  moduleId: number
  type: number
  difficulty: number
  stem: string
  analysis: string
  tags: string
  status: number
  options: AdminQuestionOption[]
}

export type CreateAdminQuestionRequest = {
  moduleId: number
  type: number
  difficulty: number
  stem: string
  analysis?: string
  tags?: string
  status?: number
  options: AdminQuestionOption[]
}

export type UpdateAdminQuestionRequest = CreateAdminQuestionRequest

export type UpdateAdminQuestionStatusRequest = {
  status: 0 | 1
}

export type AdminImportFailedItem = {
  index: number
  stem: string
  reason: string
}

export type AdminImportQuestionsResponse = {
  total: number
  successCount: number
  failedCount: number
  failedItems: AdminImportFailedItem[]
}

export function getAdminQuestionDetail(questionId: number) {
  return unwrap<AdminQuestionDetail>(apiClient.get(`/api/admin/questions/${questionId}`))
}

export function createAdminQuestion(body: CreateAdminQuestionRequest) {
  return unwrap<void>(apiClient.post('/api/admin/questions', body))
}

export function updateAdminQuestion(questionId: number, body: UpdateAdminQuestionRequest) {
  return unwrap<void>(apiClient.put(`/api/admin/questions/${questionId}`, body))
}

export function deleteAdminQuestion(questionId: number) {
  return unwrap<void>(apiClient.delete(`/api/admin/questions/${questionId}`))
}

export function updateAdminQuestionStatus(questionId: number, body: UpdateAdminQuestionStatusRequest) {
  return unwrap<void>(apiClient.patch(`/api/admin/questions/${questionId}/status`, body))
}

export function importAdminQuestions(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return unwrap<AdminImportQuestionsResponse>(
    apiClient.post('/api/admin/questions/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }),
  )
}

// ── AI 出题 ──

export type GenerateQuestionsRequest = {
  moduleId: number
  count?: number
  difficulty?: 1 | 2 | 3
  type?: 1 | 2 | 3
}

export type GenerateQuestionsResponse = {
  generatedCount: number
  questionIds: number[]
}

export type ReviewDraftQuestionResponse = {
  questionId: number
  stem: string
  correct: boolean
  confidence: number
  issues: string[]
  action: 'PUBLISHED' | 'DELETED' | 'PENDING'
}

export type DraftQuestion = {
  id: number
  moduleId: number
  type: number
  difficulty: number
  stem: string
  analysis: string
  tags: string
  status: number
  updatedAt: string
  options: AdminQuestionOption[]
}

export function generateQuestions(body: GenerateQuestionsRequest) {
  return unwrap<GenerateQuestionsResponse>(
    apiClient.post('/api/admin/questions/generate', body, { timeout: 180000 }),
  )
}

export function reviewDraftQuestion(questionId: number) {
  return unwrap<ReviewDraftQuestionResponse>(
    apiClient.post(`/api/admin/questions/${questionId}/review`, null, { timeout: 120000 }),
  )
}

export type BatchReviewProgress = {
  batchId: string
  total: number
  current: number
  published: number
  deleted: number
  pending: number
  errors: number
  completed: boolean
  results: ReviewDraftQuestionResponse[]
}

export function startBatchReview() {
  return unwrap<{ batchId: string }>(
    apiClient.post('/api/admin/questions/drafts/review'),
  )
}

export function getBatchReviewProgress(batchId: string) {
  return unwrap<BatchReviewProgress>(
    apiClient.get(`/api/admin/questions/drafts/review/${batchId}`),
  )
}

export function publishDraftQuestion(questionId: number) {
  return unwrap<{ questionId: number; status: number }>(
    apiClient.post(`/api/admin/questions/${questionId}/publish`),
  )
}

export function listDraftQuestions(params?: { page?: number; size?: number }) {
  return unwrap<{ list: DraftQuestion[]; total: number }>(
    apiClient.get('/api/admin/questions/drafts', { params }),
  )
}
