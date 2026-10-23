import type { InterviewMessage } from '@/api/modules/interview'

export function escapeHtml(raw: string) {
  return raw
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function renderInlineMarkdown(raw: string) {
  return escapeHtml(raw)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
}

function renderMarkdown(raw: string) {
  const lines = raw.replaceAll('\r\n', '\n').split('\n')
  const html: string[] = []
  let inUl = false
  let inOl = false

  const closeLists = () => {
    if (inUl) {
      html.push('</ul>')
      inUl = false
    }
    if (inOl) {
      html.push('</ol>')
      inOl = false
    }
  }

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed) {
      closeLists()
      continue
    }

    const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/)
    if (headingMatch) {
      closeLists()
      const level = Math.min(6, headingMatch[1].length)
      html.push(`<h${level}>${renderInlineMarkdown(headingMatch[2])}</h${level}>`)
      continue
    }

    const ulMatch = trimmed.match(/^[-*]\s+(.+)$/)
    if (ulMatch) {
      if (inOl) {
        html.push('</ol>')
        inOl = false
      }
      if (!inUl) {
        html.push('<ul>')
        inUl = true
      }
      html.push(`<li>${renderInlineMarkdown(ulMatch[1])}</li>`)
      continue
    }

    const olMatch = trimmed.match(/^\d+\.\s+(.+)$/)
    if (olMatch) {
      if (inUl) {
        html.push('</ul>')
        inUl = false
      }
      if (!inOl) {
        html.push('<ol>')
        inOl = true
      }
      html.push(`<li>${renderInlineMarkdown(olMatch[1])}</li>`)
      continue
    }

    closeLists()
    html.push(`<p>${renderInlineMarkdown(trimmed)}</p>`)
  }

  closeLists()
  return html.join('')
}

export function renderMarkdownSafe(raw: string) {
  return renderMarkdown(raw)
}

export function renderMessageContent(message: InterviewMessage) {
  if (message.role !== 'AI') {
    return escapeHtml(message.content)
  }
  if (message.contentFormat === 'MARKDOWN') {
    return renderMarkdown(message.content)
  }
  return escapeHtml(message.content)
}
