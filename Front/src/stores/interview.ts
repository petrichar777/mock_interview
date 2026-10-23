import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  createInterviewSession,
  type CreateInterviewSessionRequest,
  finishInterviewSession,
  type InterviewAlgoFlow,
  type InterviewMessage,
  getInterviewSessionInfo,
  type InterviewSessionInfoResponse,
  type InterviewSessionMessage,
  listInterviewMessages,
  streamSubmitAnswer,
  type StreamDoneEvent,
  evaluateInterview,
  type EvaluateResult,
} from '@/api/modules/interview'
import type { ApiError } from '@/api/client'
import { showWarningToast } from '@/utils/toast'

function createMessage(role: InterviewMessage['role'], content: string): InterviewMessage {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role,
    content,
    createdAt: new Date().toISOString(),
  }
}

export const useInterviewStore = defineStore('interview', () => {
  const sessionId = ref('')
  const messages = ref<InterviewMessage[]>([])
  const connecting = ref(false)
  const finishing = ref(false)
  const interviewTitle = ref('')
  const interviewMode = ref<'comprehensive' | 'algorithm'>('comprehensive')
  const interviewStyle = ref<'GUIDED' | 'PRESSURE'>('GUIDED')
  const interviewStatus = ref<'ongoing' | 'ended'>('ongoing')
  const welcomeText = ref('')
  const algoFlow = ref<InterviewAlgoFlow | null>(null)
  const currentTurnNo = ref(1)
  const evaluation = ref<EvaluateResult | null>(null)
  const showEvaluation = ref(false)
  const evaluating = ref(false)
  const evaluationError = ref<string | null>(null)

  function mapInterviewMode(mode?: string): 'comprehensive' | 'algorithm' {
    return mode === 'ALGORITHM' ? 'algorithm' : 'comprehensive'
  }

  function mapInterviewStatus(status?: string): 'ongoing' | 'ended' {
    return status === 'FINISHED' ? 'ended' : 'ongoing'
  }

  function normalizeMessageRole(role?: string): InterviewMessage['role'] {
    if (role?.toLowerCase() === 'user') {
      return 'USER'
    }
    if (role?.toLowerCase() === 'system') {
      return 'SYSTEM'
    }
    return 'AI'
  }

  function applySessionInfo(data: InterviewSessionInfoResponse) {
    const mode = mapInterviewMode(data.mode)
    interviewMode.value = mode
    interviewStyle.value = data.style
    interviewTitle.value = data.title || (mode === 'algorithm' ? '算法工程师实习生' : '未命名面试')
    interviewStatus.value = mapInterviewStatus(data.status)
    welcomeText.value = data.welcomeText || ''
    algoFlow.value = data.algoFlow
  }

  function mapServerMessage(message: InterviewSessionMessage): InterviewMessage {
    return {
      id: String(message.id),
      role: normalizeMessageRole(message.role),
      content: message.content,
      createdAt: message.createdAt,
      kind: message.kind,
      contentFormat: message.contentFormat,
      turnNo: message.turnNo,
      typing: message.typing,
      reviewTitle: message.reviewTitle ?? null,
      reviewType: message.reviewType ?? null,
    }
  }


  function deriveCurrentTurnNoFromMessages(list: InterviewMessage[]) {
    const turnCandidates = list
      .map((item) => item.turnNo)
      .filter((turnNo): turnNo is number => typeof turnNo === 'number' && turnNo >= 1)
    if (turnCandidates.length === 0) {
      currentTurnNo.value = 1
      return
    }
    currentTurnNo.value = Math.max(...turnCandidates)
  }

  function getLastMessageId() {
    const idCandidates = messages.value
      .map((item) => Number(item.id))
      .filter((item) => Number.isFinite(item) && item > 0)
    if (!idCandidates.length) {
      return 0
    }
    return Math.max(...idCandidates)
  }

  const currentAbortController = ref<AbortController | null>(null)

  function closeStream() {
    if (currentAbortController.value) {
      currentAbortController.value.abort()
      currentAbortController.value = null
    }
    connecting.value = false
  }

  async function initSession(payload: CreateInterviewSessionRequest) {
    const data = await createInterviewSession(payload)
    sessionId.value = String(data.sessionId)
    interviewMode.value = mapInterviewMode(data.mode)
    interviewStyle.value = payload.style
    interviewTitle.value = data.title || (interviewMode.value === 'algorithm' ? '算法工程师实习生' : '未命名面试')
    interviewStatus.value = mapInterviewStatus(data.status)
    welcomeText.value = data.welcomeText || ''
    if (data.quotaExhausted) {
      showWarningToast('今日面试次数已用完，本次面试为超额使用，请明日再使用', '额度用尽')
    }
    algoFlow.value = data.algoFlow
    messages.value = data.firstMessage
      ? [
          {
            id: String(data.firstMessage.messageId),
            role: normalizeMessageRole(data.firstMessage.role),
            content: data.firstMessage.content,
            createdAt: data.firstMessage.createdAt,
            kind: data.firstMessage.kind,
            turnNo: data.firstMessage.turnNo,
            typing: false,
          },
        ]
      : []
    deriveCurrentTurnNoFromMessages(messages.value)
  }

  async function restoreSession(targetSessionId: string) {
    if (!targetSessionId) {
      return
    }
    const [sessionInfo, historyMessages] = await Promise.all([
      getInterviewSessionInfo(targetSessionId),
      listInterviewMessages(targetSessionId),
    ])
    sessionId.value = String(sessionInfo.sessionId)
    applySessionInfo(sessionInfo)
    messages.value = historyMessages.map(mapServerMessage)
    messages.value = messages.value.map((message) => ({
      ...message,
      typing: false,
    }))
    deriveCurrentTurnNoFromMessages(messages.value)
  }

  function openStream() {
    connecting.value = false
  }

  async function submitUserMessage(content: string, retryCount = 0, reuseRequestId?: string) {
    if (!content.trim() || interviewStatus.value === 'ended' || connecting.value) {
      return
    }
    connecting.value = true
    const normalizedContent = content.trim()
    const turnNo = currentTurnNo.value

    // 复用 requestId 以保持幂等性，重试时传入相同的 requestId
    const requestId = reuseRequestId || `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`

    const userMsgId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    messages.value.push({
      ...createMessage('USER', normalizedContent),
      id: userMsgId,
      kind: 'ANSWER',
      turnNo,
      typing: false,
    })

    const placeholderId = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    const aiPlaceholder: InterviewMessage = {
      id: placeholderId,
      role: 'AI',
      content: '',
      createdAt: new Date().toISOString(),
      kind: 'ASSISTANT',
      contentFormat: 'MARKDOWN',
      turnNo: turnNo + 1,
      typing: true,
    }
    messages.value.push(aiPlaceholder)

    const controller = new AbortController()
    currentAbortController.value = controller
    try {
      await streamSubmitAnswer(sessionId.value, {
        content: normalizedContent,
        turnNo,
        requestId,
      }, {
        onDelta: (delta) => {
          const idx = messages.value.findIndex((m) => m.id === placeholderId)
          if (idx >= 0) {
            messages.value[idx] = {
              ...messages.value[idx],
              content: messages.value[idx].content + delta,
            }
          }
        },
        onDone: (result: StreamDoneEvent) => {
          const idx = messages.value.findIndex((m) => m.id === placeholderId)
          if (idx >= 0) {
            messages.value[idx] = {
              ...messages.value[idx],
              id: String(result.messageId),
              content: result.content || messages.value[idx].content || '（AI 未返回内容）',
              turnNo: result.turnNo,
              typing: false,
              reviewType: (result.reviewType as 'POSITIVE' | 'NEUTRAL' | 'IMPROVE') ?? null,
            }
          }
          algoFlow.value = result.algoFlow ?? null
          interviewStatus.value = result.isFinished ? 'ended' : 'ongoing'
          currentTurnNo.value = result.turnNo
        },
        onError: () => {
          // Handled in catch block via recoverStream
        },
      }, controller)
    } catch (err) {
      // SSE 连接失败，尝试从服务器恢复消息
      await recoverStream(placeholderId, turnNo)

      // 如果恢复后仍然没有消息内容，且未超过最大重试次数，则自动重连
      const idx = messages.value.findIndex((m) => m.id === placeholderId)
      const hasContent = idx >= 0 && messages.value[idx].typing === false && messages.value[idx].content !== ''
      if (!hasContent && retryCount < 1) {
        // 清理本次失败尝试的消息，避免 UI 出现重复消息
        messages.value = messages.value.filter(
          (m) => m.id !== userMsgId && m.id !== placeholderId,
        )
        await new Promise((resolve) => setTimeout(resolve, 1000))
        await submitUserMessage(normalizedContent, retryCount + 1, requestId)
        return
      }
    } finally {
      connecting.value = false
      currentAbortController.value = null
    }
  }

  async function recoverStream(placeholderId: string, userTurnNo: number) {
    try {
      const latestMessages = await listInterviewMessages(sessionId.value, { limit: 10 })

      // 通过 turnNo 找到服务器返回的 AI 回复
      const aiMessage = latestMessages.find(
        (m) => m.role === 'assistant' && m.turnNo === userTurnNo + 1,
      )

      if (aiMessage) {
        const idx = messages.value.findIndex((m) => m.id === placeholderId)

        // 关键检查：如果 placeholder 已经被 onDone 更新为真实 ID，则跳过
        // 防止 onDone 和 recoverStream 竞态时重复更新
        if (idx >= 0 && messages.value[idx].id === placeholderId) {
          messages.value[idx] = { ...mapServerMessage(aiMessage), typing: false }
        }
        deriveCurrentTurnNoFromMessages(messages.value)
        algoFlow.value = latestMessages.some((m) => m.kind === 'REVIEW' && m.content?.includes('面试结束'))
          ? null
          : algoFlow.value
        return
      }
    } catch {
      // Recovery fetch failed, fall through to error display
    }
    // 如果 placeholder 消息仍然存在（未被 onDone 更新），显示错误
    const idx = messages.value.findIndex((m) => m.id === placeholderId)
    if (idx >= 0) {
      messages.value[idx] = {
        ...messages.value[idx],
        content: '网络连接中断，请稍后重试',
        typing: false,
        errorRetryable: true,
      }
    }
  }

  async function retryFailedMessage(messageId: string) {
    const msg = messages.value.find((m) => m.id === messageId)
    if (!msg || !msg.errorRetryable) {
      return
    }
    const userTurnNo = (msg.turnNo ?? 1) - 1
    messages.value = messages.value.map((m) =>
      m.id === messageId
        ? { ...m, content: '', typing: true, errorRetryable: false }
        : m,
    )
    await recoverStream(messageId, userTurnNo)
  }

  async function endInterview(reason?: string) {
    if (!sessionId.value || finishing.value) {
      return
    }
    finishing.value = true
    try {
      const data = await finishInterviewSession(sessionId.value, reason?.trim() ? { reason: reason.trim() } : undefined)
      interviewStatus.value = data.status === 'FINISHED' ? 'ended' : 'ongoing'
      const lastMessageId = getLastMessageId()
      const latestMessages = await listInterviewMessages(sessionId.value, { sinceId: lastMessageId, limit: 10 })
      if (latestMessages.length) {
        messages.value.push(...latestMessages.map(mapServerMessage))
        deriveCurrentTurnNoFromMessages(messages.value)
      }
      closeStream()
    } finally {
      finishing.value = false
    }
    // Trigger evaluation in background — modal auto-shows when ready, user can leave anytime
    fetchEvaluation()
  }

  async function fetchEvaluation() {
    if (!sessionId.value || evaluating.value) return
    evaluationError.value = null
    evaluating.value = true
    try {
      let result = await evaluateInterview(sessionId.value)
      evaluation.value = result
      showEvaluation.value = true
    } catch (err) {
      const code = (err as ApiError).code
      if (code === 202) {
        // Poll until evaluation is ready
        let retries = 0
        const maxRetries = 10
        while (retries < maxRetries) {
          await new Promise((resolve) => setTimeout(resolve, 3000))
          try {
            const result = await evaluateInterview(sessionId.value)
            evaluation.value = result
            showEvaluation.value = true
            break
          } catch (retryErr) {
            const retryCode = (retryErr as ApiError).code
            if (retryCode !== 202) {
              evaluationError.value = '评估获取失败，请稍后重试'
              console.warn('Interview evaluation failed on retry', retryErr)
              break
            }
          }
          retries++
        }
        // 超时后仍未获取到评估结果
        if (!evaluation.value && retries >= maxRetries) {
          evaluationError.value = '评估生成超时（超过30秒），请稍后在我的面试历史中查看完整评估'
        }
      } else {
        evaluationError.value = '评估获取失败，请稍后重试'
        console.warn('Interview evaluation failed', err)
      }
    } finally {
      evaluating.value = false
    }
  }

  return {
    sessionId,
    messages,
    connecting,
    finishing,
    evaluating,
    interviewTitle,
    interviewMode,
    interviewStyle,
    interviewStatus,
    welcomeText,
    algoFlow,
    currentTurnNo,
    evaluation,
    showEvaluation,
    evaluationError,
    initSession,
    restoreSession,
    openStream,
    closeStream,
    submitUserMessage,
    endInterview,
    fetchEvaluation,
    retryFailedMessage,
  }
})
