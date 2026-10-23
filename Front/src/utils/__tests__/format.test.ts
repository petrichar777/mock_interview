import { describe, expect, it } from 'vitest'
import { formatPercent } from '@/utils/format'

describe('formatPercent', () => {
  it('returns 50% for half done', () => {
    expect(formatPercent(5, 10)).toBe('50%')
  })

  it('returns 100% when all done', () => {
    expect(formatPercent(10, 10)).toBe('100%')
  })

  it('returns 0% when none done', () => {
    expect(formatPercent(0, 10)).toBe('0%')
  })

  it('rounds to nearest integer', () => {
    expect(formatPercent(1, 3)).toBe('33%')
    expect(formatPercent(2, 3)).toBe('67%')
  })

  it('returns 0% when total is 0', () => {
    expect(formatPercent(0, 0)).toBe('0%')
    expect(formatPercent(5, 0)).toBe('0%')
  })
})
