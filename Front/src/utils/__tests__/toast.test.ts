import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  closeToast,
  showToast,
  showSuccessToast,
  showErrorToast,
  showActionSuccessToast,
  toastState,
} from '@/utils/toast'

describe('toast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    toastState.visible = false
    toastState.tone = 'success'
    toastState.title = ''
    toastState.message = ''
    toastState.placement = 'top-center'
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('showToast', () => {
    it('sets toast state to visible with options', () => {
      showToast({ tone: 'error', title: 'Error', message: 'Something went wrong' })
      expect(toastState.visible).toBe(true)
      expect(toastState.tone).toBe('error')
      expect(toastState.title).toBe('Error')
      expect(toastState.message).toBe('Something went wrong')
      expect(toastState.placement).toBe('top-center')
    })

    it('auto-hides after default duration (3200ms)', () => {
      showToast({ tone: 'success', title: 'OK', message: 'Done' })
      expect(toastState.visible).toBe(true)
      vi.advanceTimersByTime(3200)
      expect(toastState.visible).toBe(false)
    })

    it('respects custom duration', () => {
      showToast({ tone: 'success', title: 'OK', message: 'Done', duration: 1000 })
      vi.advanceTimersByTime(1000)
      expect(toastState.visible).toBe(false)
    })

    it('does not auto-hide when duration is 0', () => {
      showToast({ tone: 'success', title: 'OK', message: 'Done', duration: 0 })
      vi.advanceTimersByTime(10000)
      expect(toastState.visible).toBe(true)
    })

    it('respects custom placement', () => {
      showToast({ tone: 'success', title: 'OK', message: 'Done', placement: 'top-right' })
      expect(toastState.placement).toBe('top-right')
    })

    it('cancels previous auto-hide timer on new call', () => {
      showToast({ tone: 'success', title: 'A', message: 'a' })
      vi.advanceTimersByTime(1000)
      showToast({ tone: 'error', title: 'B', message: 'b' })
      vi.advanceTimersByTime(1000)
      expect(toastState.visible).toBe(true)
      expect(toastState.title).toBe('B')
      vi.advanceTimersByTime(2200)
      expect(toastState.visible).toBe(false)
    })
  })

  describe('closeToast', () => {
    it('hides toast immediately', () => {
      showToast({ tone: 'success', title: 'OK', message: 'Done' })
      expect(toastState.visible).toBe(true)
      closeToast()
      expect(toastState.visible).toBe(false)
    })

    it('cancels auto-hide timer', () => {
      showToast({ tone: 'success', title: 'OK', message: 'Done' })
      closeToast()
      vi.advanceTimersByTime(5000)
      expect(toastState.visible).toBe(false)
    })
  })

  describe('showSuccessToast', () => {
    it('shows a success toast with default title', () => {
      showSuccessToast('Saved successfully')
      expect(toastState.visible).toBe(true)
      expect(toastState.tone).toBe('success')
      expect(toastState.title).toBe('操作成功')
      expect(toastState.message).toBe('Saved successfully')
    })
  })

  describe('showErrorToast', () => {
    it('shows an error toast with default title', () => {
      showErrorToast('Network error')
      expect(toastState.tone).toBe('error')
      expect(toastState.title).toBe('操作失败')
      expect(toastState.message).toBe('Network error')
    })
  })

  describe('showActionSuccessToast', () => {
    it('shows success toast with action name', () => {
      showActionSuccessToast('退出登录')
      expect(toastState.tone).toBe('success')
      expect(toastState.message).toBe('退出登录成功')
      expect(toastState.title).toBe('退出登录成功')
    })
  })
})
