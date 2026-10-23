import { beforeEach, describe, expect, it } from 'vitest'
import { closePopup, openPopup, popupState, showErrorPopup, showSuccessPopup } from '@/utils/popup'

describe('popup', () => {
  beforeEach(() => {
    popupState.visible = false
    popupState.tone = 'success'
    popupState.title = ''
    popupState.message = ''
    popupState.confirmText = '知道了'
  })

  describe('openPopup', () => {
    it('sets popup state with all options', () => {
      openPopup({ tone: 'error', title: 'Error', message: 'Something failed', confirmText: '重试' })
      expect(popupState.visible).toBe(true)
      expect(popupState.tone).toBe('error')
      expect(popupState.title).toBe('Error')
      expect(popupState.message).toBe('Something failed')
      expect(popupState.confirmText).toBe('重试')
    })

    it('uses default confirmText when not provided', () => {
      openPopup({ tone: 'success', title: 'Done', message: 'All good' })
      expect(popupState.confirmText).toBe('知道了')
    })
  })

  describe('closePopup', () => {
    it('hides the popup', () => {
      openPopup({ tone: 'success', title: 'Done', message: 'All good' })
      expect(popupState.visible).toBe(true)
      closePopup()
      expect(popupState.visible).toBe(false)
    })

    it('does not throw when called on already closed popup', () => {
      expect(() => closePopup()).not.toThrow()
    })
  })

  describe('showSuccessPopup', () => {
    it('opens a success popup with defaults', () => {
      showSuccessPopup('Operation completed')
      expect(popupState.visible).toBe(true)
      expect(popupState.tone).toBe('success')
      expect(popupState.title).toBe('操作成功')
      expect(popupState.message).toBe('Operation completed')
    })
  })

  describe('showErrorPopup', () => {
    it('opens an error popup with defaults', () => {
      showErrorPopup('Something broke')
      expect(popupState.tone).toBe('error')
      expect(popupState.title).toBe('操作失败')
      expect(popupState.message).toBe('Something broke')
    })
  })
})
