import { apiClient, unwrap } from '@/api/client'
import type { ApiResponse } from '@/api/types'

export type RoomStatus = 0 | 1 | 2
export type RoomQuestionResult = 0 | 1 | 2 | 3
export type RoomRole = 'INTERVIEWER' | 'CANDIDATE'

export type CreateRoomRequest = {
  position: string
  level: string
  questionIds: number[]
}

export type CreateRoomResponse = {
  roomCode: string
  trtcRoomId: number
  status: RoomStatus
}

export type RoomParticipant = {
  id: number
  username: string
  role: RoomRole
}

export type RoomQuestion = {
  id: number
  questionId: number
  questionStem: string
  moduleId: number | null
  moduleName: string | null
  orderNo: number
  result: RoomQuestionResult | null
  note: string | null
}

export type RoomDetail = {
  roomCode: string
  status: RoomStatus
  position: string
  level: string
  trtcRoomId: number
  interviewer: RoomParticipant
  candidate: RoomParticipant | null
  questions: RoomQuestion[]
  answeredCount: number
  totalCount: number
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export type RtcTokenResponse = {
  sdkAppId: number
  userId: string
  userSig: string
  expireSeconds: number
  roomId: number
}

export type SaveRoomQuestionItem = {
  roomQuestionId: number
  result: RoomQuestionResult
  note: string
}

export type SaveRoomQuestionsRequest = {
  questions: SaveRoomQuestionItem[]
}

export type SaveRoomQuestionsResponse = {
  updatedCount: number
  answeredCount: number
  totalCount: number
}

export type FinishRoomResponse = {
  roomCode: string
  roomStatus: RoomStatus
  reportStatus: number
}

export type RoomQuestionOption = {
  id: number
  stem: string
  moduleId: number | null
  moduleName: string | null
  difficulty: number | null
}

export type RoomModuleMastery = {
  moduleId: number | null
  moduleName: string
  level: string
  comment: string
}

export type RoomRecommendedQuestion = {
  questionId: number
  questionStem: string
  moduleName: string | null
}

export type RoomReportPayload = {
  overallScore: number
  summary: string
  moduleMastery: RoomModuleMastery[]
  strengths: string[]
  weaknesses: string[]
  suggestions: string[]
  uncoveredQuestions: string[]
  recommendedQuestions: RoomRecommendedQuestion[]
}

export type RoomReportResponse = {
  roomCode: string
  status: number
  overallScore: number | null
  summary: string | null
  report: RoomReportPayload | null
  errorMessage: string | null
  createdAt: string | null
}

export const ROOM_STATUS_LABELS: Record<RoomStatus, string> = {
  0: '等待候选人',
  1: '面试进行中',
  2: '面试已结束',
}

export const ROOM_RESULT_LABELS: Record<RoomQuestionResult, string> = {
  0: '未评',
  1: '不会',
  2: '一般',
  3: '掌握',
}

export function createRoom(body: CreateRoomRequest) {
  return unwrap<CreateRoomResponse>(
    apiClient.post<ApiResponse<CreateRoomResponse>>('/api/v1/rooms', body),
  )
}

export function joinRoom(roomCode: string) {
  return unwrap<RoomDetail>(
    apiClient.post<ApiResponse<RoomDetail>>(`/api/v1/rooms/${encodeURIComponent(roomCode)}/join`),
  )
}

export function getRoomDetail(roomCode: string) {
  return unwrap<RoomDetail>(
    apiClient.get<ApiResponse<RoomDetail>>(`/api/v1/rooms/${encodeURIComponent(roomCode)}`),
  )
}

export function saveRoomQuestions(roomCode: string, body: SaveRoomQuestionsRequest) {
  return unwrap<SaveRoomQuestionsResponse>(
    apiClient.put<ApiResponse<SaveRoomQuestionsResponse>>(
      `/api/v1/rooms/${encodeURIComponent(roomCode)}/questions`,
      body,
    ),
  )
}

export function getRtcToken(roomCode: string) {
  return unwrap<RtcTokenResponse>(
    apiClient.get<ApiResponse<RtcTokenResponse>>(
      `/api/v1/rooms/${encodeURIComponent(roomCode)}/rtc-token`,
    ),
  )
}

export function finishRoom(roomCode: string) {
  return unwrap<FinishRoomResponse>(
    apiClient.post<ApiResponse<FinishRoomResponse>>(
      `/api/v1/rooms/${encodeURIComponent(roomCode)}/finish`,
    ),
  )
}

export function getRoomReport(roomCode: string) {
  return unwrap<RoomReportResponse>(
    apiClient.get<ApiResponse<RoomReportResponse>>(
      `/api/v1/rooms/${encodeURIComponent(roomCode)}/report`,
    ),
  )
}

export function listQuestionOptions(moduleId?: number) {
  return unwrap<RoomQuestionOption[]>(
    apiClient.get<ApiResponse<RoomQuestionOption[]>>('/api/v1/rooms/question-options', {
      params: moduleId ? { moduleId } : undefined,
    }),
  )
}
