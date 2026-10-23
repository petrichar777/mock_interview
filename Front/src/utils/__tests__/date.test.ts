import { describe, expect, it } from 'vitest'
import { formatDateTime } from '@/utils/date'

describe('formatDateTime', () => {
  it('formats a date string into zh-CN format', () => {
    const result = formatDateTime('2026-01-15T10:30:45')
    expect(result).toContain('2026')
    expect(result).toContain('01')
    expect(result).toContain('15')
    expect(result).toContain('10')
    expect(result).toContain('30')
    expect(result).toContain('45')
  })

  it('formats a timestamp', () => {
    const ms = new Date(2026, 0, 1, 8, 5, 0).getTime()
    const result = formatDateTime(ms)
    expect(result).toContain('2026')
    expect(result).toContain('01')
    expect(result).toContain('01')
  })

  it('formats a Date object', () => {
    const result = formatDateTime(new Date(2026, 5, 15, 14, 30, 0))
    expect(result).toContain('2026')
    expect(result).toContain('06')
    expect(result).toContain('15')
  })

  it('uses 24-hour format (no AM/PM)', () => {
    const result = formatDateTime('2026-06-01T23:59:59')
    expect(result).not.toMatch(/AM|PM/i)
    expect(result).toContain('23')
  })
})
