<script setup lang="ts">
import type { RoomQuestion, RoomQuestionResult } from '@/api/modules/room'
import { ROOM_RESULT_LABELS } from '@/api/modules/room'

interface Props {
  questions: RoomQuestion[]
  editable: boolean
  disabled: boolean
  saving: boolean
  answeredCount: number
  totalCount: number
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update-result': [roomQuestionId: number, result: RoomQuestionResult]
  'update-note': [roomQuestionId: number, note: string]
}>()

const resultOptions: RoomQuestionResult[] = [0, 1, 2, 3]

const resultClassMap: Record<RoomQuestionResult, string> = {
  0: 'border-slate-200 bg-slate-50 text-slate-500',
  1: 'border-rose-200 bg-rose-50 text-rose-600',
  2: 'border-amber-200 bg-amber-50 text-amber-700',
  3: 'border-emerald-200 bg-emerald-50 text-emerald-700',
}

function selectResult(question: RoomQuestion, result: RoomQuestionResult) {
  if (!props.editable || props.disabled || question.result === result) {
    return
  }
  emit('update-result', question.id, result)
}

function onNoteBlur(question: RoomQuestion, event: Event) {
  if (!props.editable || props.disabled) {
    return
  }
  const value = (event.target as HTMLTextAreaElement).value.trim()
  if (value !== (question.note || '')) {
    emit('update-note', question.id, value)
  }
}
</script>

<template>
  <section class="rounded-3xl border border-white bg-white/85 p-5 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md">
    <div class="mb-4 flex items-center justify-between gap-3">
      <div>
        <h2 class="text-base font-extrabold text-gray-800">结构化面试单</h2>
        <p class="mt-1 text-xs font-medium text-gray-500">
          已完成 {{ props.answeredCount }}/{{ props.totalCount }} 题
          <span v-if="props.saving" class="ml-2 text-orange-500">保存中...</span>
        </p>
      </div>
      <span class="rounded-full bg-rose-50 px-3 py-1 text-xs font-extrabold text-rose-500">
        {{ props.editable ? '面试官填写' : '只读进度' }}
      </span>
    </div>

    <div class="mb-4 h-2 overflow-hidden rounded-full bg-slate-100">
      <div
        class="h-full rounded-full bg-gradient-to-r from-rose-400 to-orange-400 transition-all"
        :style="{ width: `${props.totalCount ? (props.answeredCount / props.totalCount) * 100 : 0}%` }"
      />
    </div>

    <div v-if="!props.questions.length" class="rounded-2xl border border-dashed border-slate-200 p-6 text-center text-sm text-slate-500">
      暂无题目
    </div>

    <ol v-else class="space-y-4">
      <li
        v-for="question in props.questions"
        :key="question.id"
        class="rounded-2xl border border-slate-100 bg-white p-4"
      >
        <div class="mb-3 flex flex-wrap items-center gap-2">
          <span class="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-slate-800 px-2 text-xs font-extrabold text-white">
            {{ question.orderNo }}
          </span>
          <span class="rounded-full bg-indigo-50 px-2.5 py-1 text-[11px] font-bold text-indigo-500">
            {{ question.moduleName || '未分类' }}
          </span>
          <span class="ml-auto text-[11px] font-bold text-slate-400">
            {{ question.questionStem.length > 80 ? '题干较长' : '' }}
          </span>
        </div>
        <p class="whitespace-pre-wrap text-sm font-semibold leading-relaxed text-gray-800">
          {{ question.questionStem }}
        </p>

        <template v-if="props.editable">
          <div class="mt-4 flex flex-wrap gap-2">
            <button
              v-for="option in resultOptions"
              :key="option"
              type="button"
              class="rounded-xl border px-3 py-1.5 text-xs font-extrabold transition-all"
              :class="[
                resultClassMap[option],
                question.result === option ? 'ring-2 ring-offset-1 ring-rose-300' : 'opacity-80 hover:opacity-100',
                props.disabled ? 'cursor-not-allowed opacity-50' : '',
              ]"
              :disabled="props.disabled"
              @click="selectResult(question, option)"
            >
              {{ ROOM_RESULT_LABELS[option] }}
            </button>
          </div>
          <label class="mt-3 block">
            <span class="mb-1.5 block text-[11px] font-extrabold text-slate-500">面试官备注（选填）</span>
            <textarea
              :value="question.note || ''"
              rows="2"
              maxlength="500"
              class="w-full resize-none rounded-xl border border-slate-200 bg-slate-50/70 px-3 py-2 text-xs font-medium leading-relaxed text-gray-700 outline-none transition-all focus:border-rose-300 focus:bg-white focus:ring-2 focus:ring-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="props.disabled"
              placeholder="记录候选人的具体表现，AI 总结会引用这里的内容"
              @blur="onNoteBlur(question, $event)"
            />
          </label>
        </template>

        <div v-else class="mt-3 flex items-center gap-2">
          <span
            v-if="question.result !== null"
            class="rounded-full px-3 py-1 text-xs font-extrabold"
            :class="resultClassMap[question.result]"
          >
            {{ ROOM_RESULT_LABELS[question.result] }}
          </span>
          <span v-else class="text-xs font-medium text-slate-400">面试官尚未公开逐题评价</span>
        </div>
      </li>
    </ol>
  </section>
</template>
