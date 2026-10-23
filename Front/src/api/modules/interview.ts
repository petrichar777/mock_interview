import { apiClient, unwrap } from '@/api/client'
import { getAuthToken } from '@/utils/guard'
import { BACKEND_BASE_URL } from '@/config/backend'

export type InterviewMessage = {
  id: string
  role: 'USER' | 'AI' | 'SYSTEM'
  content: string
  createdAt: string
  kind?: string
  contentFormat?: 'MARKDOWN' | string
  turnNo?: number
  typing?: boolean
  reviewTitle?: string | null
  reviewType?: 'POSITIVE' | 'NEUTRAL' | 'IMPROVE' | null
  errorRetryable?: boolean
}

export type CreateInterviewSessionRequest = {
  jobTitle?: string
  jdText?: string
  resumeFileId?: string
  extractedResumeText?: string
  resumeText?: string
  algorithmDirection?: string
  mode: 'COMPREHENSIVE' | 'ALGORITHM'
  style: 'GUIDED' | 'PRESSURE'
}

export type InterviewAlgoFlow = {
  enabled: boolean
  steps: string[]
  currentStep: number
}

export type InterviewSessionInfoResponse = {
  sessionId: number
  sessionCode: string
  title: string
  mode: 'COMPREHENSIVE' | 'ALGORITHM'
  style: 'GUIDED' | 'PRESSURE'
  status: 'IN_PROGRESS' | 'FINISHED'
  welcomeText: string
  algoFlow: InterviewAlgoFlow | null
  startedAt: string
  finishedAt: string | null
}

export type InterviewFirstMessage = {
  messageId: number
  role: 'assistant'
  kind: 'QUESTION'
  turnNo: number
  content: string
  createdAt: string
}

export type CreateInterviewSessionResponse = {
  sessionId: number
  sessionCode: string
  title: string
  mode: 'COMPREHENSIVE' | 'ALGORITHM'
  status: 'IN_PROGRESS'
  quotaExhausted: boolean
  welcomeText: string
  algoFlow: InterviewAlgoFlow | null
  firstMessage: InterviewFirstMessage | null
}

export type ResumeParseResult = {
  resumeFileId: string
  fileName: string
  fileSize: number
  parseStatus: 'SUCCESS' | 'PARTIAL' | 'FAILED'
  extractedText: string
  warnings: string[]
}

export type InterviewSessionMessage = {
  id: number
  role: 'user' | 'assistant' | 'system'
  kind: 'WELCOME' | 'QUESTION' | 'ANSWER' | 'ASSISTANT' | 'SYSTEM' | 'REVIEW' | 'FOLLOWUP'
  turnNo: number
  content: string
  contentFormat?: 'MARKDOWN' | string
  reviewTitle?: string | null
  reviewType?: 'POSITIVE' | 'NEUTRAL' | 'IMPROVE' | null
  typing: boolean
  createdAt: string
}

export type SubmitInterviewAnswerRequest = {
  content: string
  turnNo: number
  /**
   * 请求唯一标识，用于幂等性保护
   */
  requestId?: string
}

export type SubmitInterviewAssistantMessage = {
  id: number
  role: 'assistant'
  kind: 'ASSISTANT'
  turnNo: number
  content: string
  contentFormat: 'MARKDOWN' | string
  typing: boolean
  createdAt: string
}

export type SubmitInterviewAnswerResponse = {
  assistantMessage: SubmitInterviewAssistantMessage
  algoFlow: InterviewAlgoFlow | null
  isFinished: boolean
}

export type FinishInterviewRequest = {
  reason?: string
}

export type FinishInterviewResponse = {
  sessionId: number
  status: 'FINISHED'
  finishedAt: string
}

export function createInterviewSession(body: CreateInterviewSessionRequest) {
  return unwrap<CreateInterviewSessionResponse>(apiClient.post('/api/interviews/sessions', body))
}

export function getInterviewSessionInfo(sessionId: string | number) {
  return unwrap<InterviewSessionInfoResponse>(apiClient.get(`/api/interviews/sessions/${sessionId}`))
}

export function listInterviewMessages(sessionId: string | number, params?: { sinceId?: number; limit?: number }) {
  return unwrap<InterviewSessionMessage[]>(apiClient.get(`/api/interviews/sessions/${sessionId}/messages`, { params }))
}

export function submitInterviewAnswer(sessionId: string | number, body: SubmitInterviewAnswerRequest) {
  return unwrap<SubmitInterviewAnswerResponse>(apiClient.post(`/api/interviews/sessions/${sessionId}/answer`, body))
}

export function finishInterviewSession(sessionId: string | number, body?: FinishInterviewRequest) {
  return unwrap<FinishInterviewResponse>(apiClient.post(`/api/interviews/sessions/${sessionId}/finish`, body))
}

export function parseInterviewResume(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return unwrap<ResumeParseResult>(
    apiClient.post('/api/interviews/resume/parse', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }),
  )
}

export type StreamDeltaEvent = { content: string }
export type StreamDoneEvent = {
  messageId: number
  turnNo: number
  content?: string
  isFinished: boolean
  reviewType?: string
  algoFlow?: InterviewAlgoFlow | null
}

export async function streamSubmitAnswer(
  sessionId: string | number,
  body: SubmitInterviewAnswerRequest,
  callbacks: {
    onDelta: (content: string) => void
    onDone: (result: StreamDoneEvent) => void
    onError: (error: Error) => void
  },
  abortController: AbortController,
) {
  const token = getAuthToken()
  const url = `${BACKEND_BASE_URL}/api/interviews/sessions/${sessionId}/stream-answer`

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: token ? `Bearer ${token}` : '',
      },
      body: JSON.stringify(body),
      signal: abortController.signal,
    })

    if (!response.ok) {
      let message = '请求失败'
      try {
        const errorText = await response.text()
        const errorJson = JSON.parse(errorText)
        message = errorJson.message || message
      } catch {}
      throw new Error(message)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('浏览器不支持流式读取')
    }

    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''
    let doneReceived = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const rawLine of lines) {
        const line = rawLine.trimEnd()
        if (!line) continue
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
          continue
        }
        if (line.startsWith('data:')) {
          const dataStr = line.slice(5).trim()
          if (!dataStr) continue
          try {
            const data = JSON.parse(dataStr)
            if (currentEvent === 'delta') {
              callbacks.onDelta(data.content || '')
            } else if (currentEvent === 'done') {
              doneReceived = true
              callbacks.onDone(data)
            }
          } catch {
            // skip unparseable lines
          }
        }
      }
    }

    if (!doneReceived) {
      throw new Error('流式连接意外关闭')
    }
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') return
    callbacks.onError(error instanceof Error ? error : new Error(String(error)))
    throw error instanceof Error ? error : new Error(String(error))
  }
}

export type DimensionScore = {
  name: string
  score: number
  maxScore: number
  comment: string
}

export type EvaluateResult = {
  totalScore: number
  summary: string
  dimensions: DimensionScore[]
  highlights: string
  improvements: string
  suggestion: string
  resumeAdvice?: string
}

export function evaluateInterview(sessionId: string | number) {
  return unwrap<EvaluateResult>(apiClient.post(`/api/interviews/sessions/${sessionId}/evaluate`))
}

export type ReportItem = {
  sessionId: number
  title: string
  mode: 'COMPREHENSIVE' | 'ALGORITHM'
  style: 'GUIDED' | 'PRESSURE'
  totalScore: number | null
  finishedAt: string
}

export type ReportListResponse = {
  list: ReportItem[]
  page: number
  size: number
  total: number
}

export function getReportList(params?: { page?: number; size?: number; mode?: string }) {
  return unwrap<ReportListResponse>(apiClient.get('/api/interviews/reports', { params }))
}

export function getReportDetail(sessionId: string | number) {
  return unwrap<EvaluateResult>(apiClient.get(`/api/interviews/reports/${sessionId}`))
}

// Resume optimization types
export type ResumeDimension = {
  name: string
  score: number
  maxScore: number
  comment: string
}

export type SectionAdvice = {
  sectionName: string
  originalSummary: string
  suggestion: string
}

export type ResumeOptimizeResult = {
  overallScore: number
  summary: string
  dimensions: ResumeDimension[]
  jdMatch?: string
  sectionAdvice: SectionAdvice[]
  interviewInsights: string[]
}

export function optimizeResume(resumeText: string, jobDescription?: string) {
  return unwrap<ResumeOptimizeResult>(
    apiClient.post('/api/interviews/resume/optimize', { resumeText, jobDescription }, {
      timeout: 120000,
    }),
  )
}

export function downloadReportPdf(sessionId: number) {
  const token = getAuthToken()
  const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
  const url = `${BACKEND_BASE_URL}/api/interviews/reports/${sessionId}/pdf`
  return fetch(url, { headers }).then(async (res) => {
    if (!res.ok) {
      const text = await res.text().catch(() => '导出失败')
      throw new Error(text)
    }
    const blob = await res.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = downloadUrl
    a.download = `interview-report-${sessionId}.pdf`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(downloadUrl)
  })
}

export function getTrendScores(limit?: number) {
  return unwrap<ReportItem[]>(apiClient.get('/api/interviews/reports/trend', { params: limit ? { limit } : undefined }))
}

export type ResumeQuotaInfo = {
  remainingResumeOptimizations: number
  maxResumeOptimizations: number
}

export function getResumeQuota() {
  return unwrap<ResumeQuotaInfo>(apiClient.get('/api/interviews/resume/quota'))
}

// Async resume optimization
export type OptimizeAsyncResponse = {
  taskId: string
}

export type OptimizeResultResponse = {
  status: 'PROCESSING' | 'DONE' | 'FAILED' | 'NOT_FOUND'
  result?: ResumeOptimizeResult
  errorMessage?: string
  resumeText?: string
  jobDescription?: string
}

export function optimizeResumeAsync(resumeText: string, jobDescription?: string) {
  return unwrap<OptimizeAsyncResponse>(
    apiClient.post('/api/interviews/resume/optimize-async', { resumeText, jobDescription }),
  )
}

export function getResumeOptimizeResult(taskId: string) {
  return unwrap<OptimizeResultResponse>(apiClient.get(`/api/interviews/resume/optimize-result/${taskId}`))
}

export function getResumeOptimizeLatest() {
  return unwrap<OptimizeResultResponse | null>(apiClient.get('/api/interviews/resume/optimize-latest'))
}
