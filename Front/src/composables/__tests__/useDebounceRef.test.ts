import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import { useDebounceRef } from '@/composables/useDebounceRef'

describe('useDebounceRef', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns initial value immediately', () => {
    const source = ref('initial')
    const debounced = useDebounceRef(source, 300)
    expect(debounced.value).toBe('initial')
  })

  it('delays updating debounced value', async () => {
    const source = ref('initial')
    const debounced = useDebounceRef(source, 300)

    source.value = 'updated'
    await nextTick()

    expect(debounced.value).toBe('initial')

    vi.advanceTimersByTime(300)
    await nextTick()
    expect(debounced.value).toBe('updated')
  })

  it('cancels previous timer on rapid changes', async () => {
    const source = ref(0)
    const debounced = useDebounceRef(source, 300)

    source.value = 1
    await nextTick()
    vi.advanceTimersByTime(100)
    source.value = 2
    await nextTick()
    vi.advanceTimersByTime(100)
    source.value = 3
    await nextTick()

    vi.advanceTimersByTime(300)
    await nextTick()
    expect(debounced.value).toBe(3)
  })

  it('works with different types', async () => {
    const source = ref<string[]>([])
    const debounced = useDebounceRef(source, 200)
    source.value = ['a', 'b']
    await nextTick()
    vi.advanceTimersByTime(200)
    await nextTick()
    expect(debounced.value).toEqual(['a', 'b'])
  })

  it('uses default delay of 300ms', async () => {
    const source = ref('old')
    const debounced = useDebounceRef(source)
    source.value = 'new'
    await nextTick()
    vi.advanceTimersByTime(299)
    await nextTick()
    expect(debounced.value).toBe('old')
    vi.advanceTimersByTime(1)
    await nextTick()
    expect(debounced.value).toBe('new')
  })
})
