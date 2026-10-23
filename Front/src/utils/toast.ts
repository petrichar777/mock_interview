import { reactive } from 'vue'

export type ToastTone = 'success' | 'error' | 'warning'
export type ToastPlacement = 'top-center' | 'top-right'

type ShowToastOptions = {
  tone: ToastTone
  title: string
  message: string
  duration?: number
  placement?: ToastPlacement
}

export const toastState = reactive({
  visible: false,
  tone: 'success' as ToastTone,
  title: '',
  message: '',
  placement: 'top-center' as ToastPlacement,
})

let closeTimer: ReturnType<typeof setTimeout> | null = null

function clearTimer() {
  if (!closeTimer) {
    return
  }
  clearTimeout(closeTimer)
  closeTimer = null
}

export function closeToast() {
  toastState.visible = false
  clearTimer()
}

export function showToast(options: ShowToastOptions) {
  clearTimer()
  toastState.visible = true
  toastState.tone = options.tone
  toastState.title = options.title
  toastState.message = options.message
  toastState.placement = options.placement || 'top-center'
  const duration = options.duration ?? 3200
  if (duration > 0) {
    closeTimer = setTimeout(() => {
      toastState.visible = false
      closeTimer = null
    }, duration)
  }
}

export function showSuccessToast(message: string, title = '操作成功', placement: ToastPlacement = 'top-center') {
  showToast({
    tone: 'success',
    title,
    message,
    placement,
  })
}

export function showErrorToast(message: string, title = '操作失败', placement: ToastPlacement = 'top-center') {
  showToast({
    tone: 'error',
    title,
    message,
    placement,
  })
}

export function showWarningToast(message: string, title = '温馨提示', placement: ToastPlacement = 'top-center') {
  showToast({
    tone: 'warning',
    title,
    message,
    placement,
  })
}

export function showActionSuccessToast(action: string, placement: ToastPlacement = 'top-center') {
  showSuccessToast(`${action}成功`, `${action}成功`, placement)
}
