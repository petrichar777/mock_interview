import { describe, expect, it } from 'vitest'
import { escapeHtml, renderMarkdownSafe, renderMessageContent } from '@/utils/markdown'
import type { InterviewMessage } from '@/api/modules/interview'

describe('escapeHtml', () => {
  it('escapes HTML special characters', () => {
    const result = escapeHtml('<script>alert("XSS")</script>')
    expect(result).not.toContain('<script>')
    expect(result).toContain('&lt;script&gt;')
    expect(result).toContain('&quot;XSS&quot;')
  })

  it('escapes ampersands', () => {
    expect(escapeHtml('A & B')).toContain('&amp;')
  })

  it('escapes single quotes', () => {
    expect(escapeHtml("it's")).toContain('&#39;')
  })

  it('returns plain text unchanged', () => {
    expect(escapeHtml('hello world')).toBe('hello world')
  })
})

describe('renderMarkdownSafe', () => {
  it('renders markdown while escaping raw HTML', () => {
    const result = renderMarkdownSafe('## 总结\n\n<script>alert(1)</script>\n\n- **重点**')
    expect(result).toContain('<h2>总结</h2>')
    expect(result).toContain('<strong>重点</strong>')
    expect(result).not.toContain('<script>')
    expect(result).toContain('&lt;script&gt;')
  })
})

describe('renderMessageContent', () => {
  it('escapes user messages regardless of format', () => {
    const msg: InterviewMessage = {
      createdAt: '',
      id: '1',
      role: 'USER',
      content: '<b>hello</b>',
      contentFormat: 'MARKDOWN',
      turnNo: 0,
    }
    const result = renderMessageContent(msg)
    expect(result).not.toContain('<b>')
    expect(result).toContain('&lt;b&gt;')
  })

  it('renders AI message with markdown formatting', () => {
    const msg: InterviewMessage = {
      createdAt: '',
      id: '2',
      role: 'AI',
      content: '# Heading\n\nNormal paragraph with **bold** and `code`.',
      contentFormat: 'MARKDOWN',
      turnNo: 1,
    }
    const result = renderMessageContent(msg)
    expect(result).toContain('<h1>Heading</h1>')
    expect(result).toContain('<strong>bold</strong>')
    expect(result).toContain('<code>code</code>')
    expect(result).toContain('<p>')
  })

  it('renders unordered lists in AI markdown messages', () => {
    const msg: InterviewMessage = {
      createdAt: '',
      id: '3',
      role: 'AI',
      content: '- Item one\n- Item two',
      contentFormat: 'MARKDOWN',
      turnNo: 2,
    }
    const result = renderMessageContent(msg)
    expect(result).toContain('<ul>')
    expect(result).toContain('<li>Item one</li>')
    expect(result).toContain('<li>Item two</li>')
    expect(result).toContain('</ul>')
  })

  it('renders ordered lists in AI markdown messages', () => {
    const msg: InterviewMessage = {
      createdAt: '',
      id: '4',
      role: 'AI',
      content: '1. First\n2. Second',
      contentFormat: 'MARKDOWN',
      turnNo: 3,
    }
    const result = renderMessageContent(msg)
    expect(result).toContain('<ol>')
    expect(result).toContain('<li>First</li>')
    expect(result).toContain('<li>Second</li>')
    expect(result).toContain('</ol>')
  })

  it('escapes AI messages without MARKDOWN format', () => {
    const msg: InterviewMessage = {
      createdAt: '',
      id: '5',
      role: 'AI',
      content: '<script>bad</script>',
      contentFormat: 'PLAIN',
      turnNo: 4,
    }
    const result = renderMessageContent(msg)
    expect(result).toContain('&lt;script&gt;')
  })

  it('handles mixed list types by closing previous list', () => {
    const msg: InterviewMessage = {
      createdAt: '',
      id: '6',
      role: 'AI',
      content: '- ul item\n1. ol item',
      contentFormat: 'MARKDOWN',
      turnNo: 5,
    }
    const result = renderMessageContent(msg)
    expect(result).toContain('<ul>')
    expect(result).toContain('</ul>')
    expect(result).toContain('<ol>')
    expect(result).toContain('</ol>')
  })

  it('handles empty content', () => {
    const msg: InterviewMessage = {
      createdAt: '',
      id: '7',
      role: 'AI',
      content: '',
      contentFormat: 'MARKDOWN',
      turnNo: 6,
    }
    const result = renderMessageContent(msg)
    expect(result).toBe('')
  })

  it('handles inline markdown combinations', () => {
    const msg: InterviewMessage = {
      createdAt: '',
      id: '8',
      role: 'AI',
      content: 'Text with **bold** and *italic* and `code`.',
      contentFormat: 'MARKDOWN',
      turnNo: 7,
    }
    const result = renderMessageContent(msg)
    expect(result).toContain('<strong>bold</strong>')
    expect(result).toContain('<em>italic</em>')
    expect(result).toContain('<code>code</code>')
  })
})
