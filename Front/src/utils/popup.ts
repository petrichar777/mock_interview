import { reactive } from 'vue'

export type PopupTone = 'success' | 'error'

type OpenPopupOptions = {
  tone: PopupTone
  title: string
  message: string
  confirmText?: string
}

export const popupState = reactive({
  visible: false,
  tone: 'success' as PopupTone,
  title: '',
  message: '',
  confirmText: '知道了',
})

export function openPopup(options: OpenPopupOptions) {
  popupState.visible = true
  popupState.tone = options.tone
  popupState.title = options.title
  popupState.message = options.message
  popupState.confirmText = options.confirmText || '知道了'
}

export function closePopup() {
  popupState.visible = false
}

export function showSuccessPopup(message: string, title = '操作成功') {
  openPopup({
    tone: 'success',
    title,
    message,
  })
}

export function showErrorPopup(message: string, title = '操作失败') {
  openPopup({
    tone: 'error',
    title,
    message,
  })
}
