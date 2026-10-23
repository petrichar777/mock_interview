<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { EvaluateResult } from '@/api/modules/interview'

const props = defineProps<{
  visible: boolean
  evaluation: EvaluateResult | null
}>()

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()

function goToSetup() {
  router.push('/app/interview/setup')
}

function goToReport() {
  emit('close')
  router.push('/app/reports')
}

const animatedScore = ref(0)
const barsVisible = ref(false)

function computeBarColor(score: number, maxScore: number) {
  const ratio = score / maxScore
  if (ratio >= 0.75) return '#10b981'
  if (ratio >= 0.5) return '#f59e0b'
  return '#ef4444'
}

watch(
  () => props.visible,
  (val) => {
    if (val && props.evaluation) {
      animatedScore.value = 0
      barsVisible.value = false
      const target = props.evaluation.totalScore
      const duration = 800
      const start = performance.now()
      const tick = (now: number) => {
        const elapsed = now - start
        const progress = Math.min(elapsed / duration, 1)
        const eased = 1 - Math.pow(1 - progress, 3)
        animatedScore.value = Math.round(eased * target)
        if (progress < 1) {
          requestAnimationFrame(tick)
        }
      }
      requestAnimationFrame(tick)
      setTimeout(() => {
        barsVisible.value = true
      }, 400)
    }
  },
  { immediate: true },
)

function getScoreLabel(score: number) {
  if (score >= 90) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '一般'
  return '需努力'
}

function getScoreColor(score: number) {
  if (score >= 90) return '#10b981'
  if (score >= 75) return '#3b82f6'
  if (score >= 60) return '#f59e0b'
  return '#ef4444'
}

function onBack() {
  emit('close')
  goToSetup()
}

function onClose() {
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="visible && evaluation"
        class="fixed inset-0 z-[10000] flex items-center justify-center p-4"
        @click.self="onClose"
      >
        <div class="absolute inset-0 bg-gray-900/50 backdrop-blur-md" />

        <div
          class="relative flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-3xl border border-white/30 bg-white/95 shadow-2xl backdrop-blur-xl"
          @click.stop
        >
          <!-- Header with score -->
          <div
            class="flex flex-col items-center bg-gradient-to-b from-slate-50 to-white px-6 pb-6 pt-10"
          >
            <div
              class="flex h-28 w-28 items-center justify-center rounded-full border-4 text-4xl font-black shadow-lg transition-colors duration-700"
              :style="{
                borderColor: getScoreColor(animatedScore),
                color: getScoreColor(animatedScore),
              }"
            >
              {{ animatedScore }}
            </div>
            <span
              class="mt-2 rounded-full px-3 py-0.5 text-sm font-bold text-white"
              :style="{ backgroundColor: getScoreColor(animatedScore) }"
            >
              {{ getScoreLabel(animatedScore) }}
            </span>
            <h2 class="mt-3 text-xl font-extrabold text-gray-800">面试评估报告</h2>
          </div>

          <!-- Scrollable body -->
          <div class="flex-1 overflow-y-auto px-6 py-4 space-y-5">
            <!-- Dimension score bars -->
            <div>
              <h3 class="mb-3 text-sm font-extrabold text-gray-500">各维度得分</h3>
              <div class="space-y-3">
                <div
                  v-for="(dim, index) in evaluation.dimensions"
                  :key="dim.name"
                  class="space-y-1"
                >
                  <div class="flex items-center justify-between text-xs font-bold">
                    <span class="text-gray-600">{{ dim.name }}</span>
                    <span class="text-gray-800">{{ dim.score }} / {{ dim.maxScore }}</span>
                  </div>
                  <div class="h-2.5 w-full overflow-hidden rounded-full bg-gray-100">
                    <div
                      class="h-full rounded-full transition-all duration-700 ease-out"
                      :style="{
                        width: barsVisible ? (dim.score / dim.maxScore) * 100 + '%' : '0%',
                        backgroundColor: computeBarColor(dim.score, dim.maxScore),
                        transitionDelay: index * 100 + 'ms',
                      }"
                    />
                  </div>
                  <p class="text-xs leading-relaxed text-gray-500">{{ dim.comment }}</p>
                </div>
              </div>
            </div>

            <!-- Summary -->
            <div>
              <h3 class="mb-2 text-sm font-extrabold text-gray-500">总体评价</h3>
              <p class="rounded-xl bg-blue-50/70 px-4 py-3 text-sm leading-relaxed text-gray-700">
                {{ evaluation.summary }}
              </p>
            </div>

            <!-- Highlights -->
            <div v-if="evaluation.highlights">
              <h3 class="mb-2 text-sm font-extrabold text-emerald-600">亮点</h3>
              <div class="rounded-xl bg-emerald-50/70 px-4 py-3">
                <p class="text-sm leading-relaxed text-gray-700">{{ evaluation.highlights }}</p>
              </div>
            </div>

            <!-- Improvements -->
            <div v-if="evaluation.improvements">
              <h3 class="mb-2 text-sm font-extrabold text-amber-600">改进方向</h3>
              <div class="rounded-xl bg-amber-50/70 px-4 py-3">
                <p class="text-sm leading-relaxed text-gray-700">{{ evaluation.improvements }}</p>
              </div>
            </div>

            <!-- Suggestion -->
            <div v-if="evaluation.suggestion">
              <h3 class="mb-2 text-sm font-extrabold text-violet-600">学习建议</h3>
              <div class="rounded-xl bg-violet-50/70 px-4 py-3">
                <p class="text-sm leading-relaxed text-gray-700">{{ evaluation.suggestion }}</p>
              </div>
            </div>

            <!-- Resume Advice -->
            <div v-if="evaluation.resumeAdvice">
              <h3 class="mb-2 text-sm font-extrabold text-teal-600">简历优化建议</h3>
              <div class="rounded-xl border border-teal-200 bg-teal-50/70 px-4 py-3">
                <p class="text-sm leading-relaxed text-gray-700">{{ evaluation.resumeAdvice }}</p>
              </div>
            </div>
          </div>

          <!-- Footer -->
          <div class="flex-shrink-0 border-t border-gray-100 px-6 py-4 space-y-2">
            <button
              type="button"
              class="w-full rounded-2xl bg-gradient-to-r from-orange-400 to-rose-400 py-3 text-sm font-extrabold text-white shadow-lg transition-all active:scale-95 hover:-translate-y-0.5 hover:from-orange-500 hover:to-rose-500"
              @click="onBack"
            >
              返回面试设置
            </button>
            <button
              type="button"
              class="w-full rounded-2xl border border-violet-200 bg-violet-50 py-2.5 text-sm font-bold text-violet-600 shadow-sm transition-all hover:bg-violet-100 hover:border-violet-300 active:scale-95"
              @click="goToReport"
            >
              查看完整报告与对话回放 →
            </button>
            <button
              type="button"
              class="w-full rounded-2xl border border-gray-200 bg-white py-2.5 text-sm font-bold text-gray-400 transition-colors hover:bg-gray-50 hover:text-gray-500"
              @click="onClose"
            >
              继续查看对话
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active {
  transition: all 0.35s ease-out;
}
.modal-leave-active {
  transition: all 0.2s ease-in;
}
.modal-enter-from {
  opacity: 0;
}
.modal-enter-from > div:last-child {
  transform: scale(0.92) translateY(20px);
  opacity: 0;
}
.modal-leave-to {
  opacity: 0;
}
.modal-leave-to > div:last-child {
  transform: scale(0.95) translateY(10px);
  opacity: 0;
}
</style>
