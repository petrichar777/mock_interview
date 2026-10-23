<script setup lang="ts">
import type { InterviewMessage } from '@/api/modules/interview'
import { renderMessageContent } from '@/utils/markdown'

defineProps<{
  messages: InterviewMessage[]
}>()

function reviewLabel(type: string) {
  if (type === 'POSITIVE') return '答得不错'
  if (type === 'IMPROVE') return '可以改进'
  return '回答一般'
}

function reviewBadgeClass(type: string) {
  if (type === 'POSITIVE') return 'bg-emerald-500 text-white'
  if (type === 'IMPROVE') return 'bg-amber-500 text-white'
  return 'bg-blue-500 text-white'
}

function reviewBorderClass(type: string) {
  if (type === 'POSITIVE') return 'border-l-emerald-400 border-l-[3px]'
  if (type === 'IMPROVE') return 'border-l-amber-400 border-l-[3px]'
  return 'border-l-blue-400 border-l-[3px]'
}
</script>

<template>
  <div v-if="messages.length === 0" class="flex h-full items-center justify-center">
    <div class="rounded-2xl border border-white bg-white/60 px-6 py-10 text-center shadow-sm backdrop-blur-sm">
      <svg class="mx-auto mb-3 h-10 w-10 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
      </svg>
      <p class="text-sm font-bold text-gray-400">该面试暂无对话记录</p>
    </div>
  </div>

  <div v-else class="replay-container flex flex-col space-y-5 overflow-y-auto px-4 py-6 sm:px-6">
    <div
      v-for="message in messages"
      :key="message.id"
      class="flex items-start"
      :class="message.role === 'USER' ? 'flex-row-reverse' : message.role === 'SYSTEM' ? 'justify-center' : ''"
    >
      <!-- AI avatar -->
      <div v-if="message.role === 'AI'" class="mr-3 flex-shrink-0">
        <div class="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-tr from-rose-400 to-orange-400 text-white shadow-sm">
          <svg class="h-4.5 w-4.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
        </div>
      </div>

      <!-- USER avatar -->
      <div v-else-if="message.role === 'USER'" class="ml-3 flex-shrink-0">
        <div class="flex h-9 w-9 items-center justify-center rounded-xl border-2 border-white bg-white/80 font-extrabold text-gray-500 shadow-sm backdrop-blur-sm">
          <span class="text-sm">Z</span>
        </div>
      </div>

      <!-- SYSTEM message (no avatar) -->
      <div
        v-if="message.role === 'SYSTEM'"
        class="max-w-sm rounded-2xl bg-slate-100/80 px-4 py-2 text-center text-xs font-bold text-gray-500 backdrop-blur-sm"
      >
        {{ message.content }}
      </div>

      <!-- AI / USER message bubble -->
      <div
        v-else
        class="max-w-[80%] px-5 py-3.5 text-sm leading-relaxed"
        :class="[
          message.role === 'AI'
            ? 'rounded-3xl rounded-tl-none border border-white bg-white/80 text-gray-700 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md'
            : 'rounded-3xl rounded-tr-none bg-gradient-to-br from-orange-400 to-rose-400 text-white shadow-[0_8px_20px_rgba(244,63,94,0.2)]',
          message.role === 'AI' && message.reviewType ? reviewBorderClass(message.reviewType) : '',
        ]"
      >
        <div
          v-if="message.role === 'AI' && message.reviewType"
          class="-mx-5 -mt-3.5 mb-3 rounded-t-3xl rounded-tl-none px-5 py-2 text-xs font-extrabold text-white"
          :class="reviewBadgeClass(message.reviewType)"
        >
          {{ reviewLabel(message.reviewType) }}
        </div>
        <div
          :class="message.role === 'AI' && message.contentFormat === 'MARKDOWN' ? 'markdown-content' : 'whitespace-pre-wrap'"
          v-html="renderMessageContent(message)"
        />
      </div>
    </div>

    <div class="h-4 flex-shrink-0" />
  </div>
</template>

<style scoped>
.replay-container {
  max-height: 65vh;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin-top: 0.35rem;
  margin-bottom: 0.35rem;
  color: #0f172a;
  font-weight: 700;
  line-height: 1.4;
}

.markdown-content :deep(h1) {
  font-size: 1.2rem;
}

.markdown-content :deep(h2) {
  font-size: 1.1rem;
}

.markdown-content :deep(h3) {
  font-size: 1rem;
}

.markdown-content :deep(p) {
  margin: 0.2rem 0;
  white-space: pre-wrap;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.35rem 0 0.35rem 1.25rem;
}

.markdown-content :deep(li) {
  margin: 0.15rem 0;
}

.markdown-content :deep(code) {
  border-radius: 0.35rem;
  background: #f1f5f9;
  padding: 0.1rem 0.35rem;
  font-size: 0.85em;
}
</style>
