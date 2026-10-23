<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showErrorToast } from '@/utils/toast'
import AppSelect from '@/components/common/AppSelect.vue'
import {
  getMistakeDetail,
  getMistakeStats,
  listMistakes,
  randomMistakes,
  removeMistake,
  type MistakeDetail,
  type MistakeItem,
} from '@/api/modules/mistakes'

const retryCountOptions: { value: SelectValue; label: string }[] = [
  { value: 3, label: '3 题' },
  { value: 5, label: '5 题' },
  { value: 10, label: '10 题' },
  { value: 20, label: '20 题' },
]
const retryCount = ref<SelectValue>(10)

const sortBy = ref<SelectValue>('')
const sortOptions: { value: SelectValue; label: string }[] = [
  { value: '', label: '最近错题' },
  { value: 'module', label: '按模块' },
  { value: 'difficulty_desc', label: '难度高→低' },
  { value: 'difficulty_asc', label: '难度低→高' },
]

type MistakeTone = 'rose' | 'orange' | 'emerald'
type SelectValue = string | number

const router = useRouter()
const page = ref(1)
const pageSize = ref(4)
const selectedModuleId = ref<SelectValue | null>(null)
const loading = ref(false)
const error = ref('')
const parseOpen = ref(false)
const parseLoading = ref(false)
const parseError = ref('')
const resolvedToday = ref(0)
const weeklyResolved = ref(0)
const consecutiveReviewDays = ref(0)
const totalMistakesInStats = ref(0)
const statsLoading = ref(false)
const feedbackText = ref('')
const feedbackTone = ref<'gray' | 'rose'>('gray')
const parseTarget = ref<MistakeItem | null>(null)
const parseDetail = ref<MistakeDetail | null>(null)
const mistakes = ref<MistakeItem[]>([])
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pagedMistakes = computed(() => mistakes.value)
const canPrev = computed(() => page.value > 1)
const canNext = computed(() => page.value < totalPages.value)
const empty = computed(() => total.value === 0)
const moduleOptions = computed(() => {
  const map = new Map<number, string>()
  for (const item of mistakes.value) {
    map.set(item.moduleId, item.moduleName)
  }
  const list: { value: SelectValue; label: string }[] = [{ value: '', label: '全部模块' }]
  for (const [value, label] of map.entries()) {
    list.push({ value, label })
  }
  return list
})
const filterModuleId = computed<number | undefined>(() => {
  return typeof selectedModuleId.value === 'number' ? selectedModuleId.value : undefined
})

async function fetchMistakes() {
  loading.value = true
  error.value = ''
  try {
    const data = await listMistakes(page.value, pageSize.value, filterModuleId.value, sortBy.value ? String(sortBy.value) : undefined)
    mistakes.value = data.list
    total.value = data.total
    page.value = data.page
  } catch (err) {
    error.value = err instanceof Error ? err.message : '获取错题列表失败'
  } finally {
    loading.value = false
  }
}

async function openParseModal(item: MistakeItem) {
  parseTarget.value = item
  parseDetail.value = null
  parseError.value = ''
  parseLoading.value = true
  parseOpen.value = true
  try {
    parseDetail.value = await getMistakeDetail(item.questionId)
  } catch (err) {
    parseError.value = err instanceof Error ? err.message : '获取错题详情失败'
  } finally {
    parseLoading.value = false
  }
}

function closeParseModal() {
  parseOpen.value = false
  parseDetail.value = null
  parseError.value = ''
  parseLoading.value = false
}

async function removeItem(id: number) {
  if (!id) {
    return
  }
  try {
    const data = await removeMistake(id)
    feedbackTone.value = 'rose'
    feedbackText.value = data.removed ? '已从错题本移除该题' : '该题已不在错题本中'
    if (mistakes.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await fetchMistakes()
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '移除失败，请稍后重试', '移除失败', 'top-right')
  }
}

function goRetry(item: MistakeItem) {
  router.push({
    path: `/app/practice/${item.moduleId}`,
    query: {
      mode: 'mistake',
      index: '0',
      questionIds: String(item.questionId),
    },
  })
}

async function startRetry() {
  if (loading.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await randomMistakes({
      count: typeof retryCount.value === 'number' ? retryCount.value : 10,
      moduleId: filterModuleId.value,
    })
    if (!data.questionIds.length) {
      feedbackTone.value = 'gray'
      feedbackText.value = '当前没有可重练错题'
      return
    }
    const firstQuestionId = data.questionIds[0]
    const match = mistakes.value.find((item) => item.questionId === firstQuestionId)
    const moduleId = match?.moduleId ?? (await getMistakeDetail(firstQuestionId)).moduleId
    feedbackTone.value = 'gray'
    feedbackText.value = `已随机抽取 ${data.questionIds.length} 题，开始重练`
    router.push({
      path: `/app/practice/${moduleId}`,
      query: {
        mode: 'mistake',
        index: '0',
        questionIds: data.questionIds.join(','),
      },
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '随机抽题失败'
  } finally {
    loading.value = false
  }
}

function prevPage() {
  if (canPrev.value) {
    page.value -= 1
    fetchMistakes()
  }
}

function nextPage() {
  if (canNext.value) {
    page.value += 1
    fetchMistakes()
  }
}

function getTypeLabel(type: number) {
  if (type === 2) {
    return '多选题'
  }
  if (type === 3) {
    return '判断题'
  }
  return '单选题'
}

function getTypeTone(type: number): MistakeTone {
  if (type === 2) {
    return 'orange'
  }
  if (type === 3) {
    return 'emerald'
  }
  return 'rose'
}

function getTypeClass(type: number) {
  const tone = getTypeTone(type)
  if (tone === 'orange') {
    return 'bg-orange-50 text-orange-500 border-orange-100'
  }
  if (tone === 'emerald') {
    return 'bg-emerald-50 text-emerald-500 border-emerald-100'
  }
  return 'bg-rose-50 text-rose-500 border-rose-100'
}

function getFeedbackClass() {
  if (feedbackTone.value === 'rose') {
    return 'text-rose-500'
  }
  return 'text-gray-500'
}

function formatAnswers(answers: string[]) {
  if (!answers.length) {
    return '暂无'
  }
  return answers.join('、')
}

const parseExplanations = computed(() => {
  return parseDetail.value?.explanations?.length ? parseDetail.value.explanations : ['暂无解析']
})

const parseTags = computed(() => {
  return parseDetail.value?.tags?.length ? parseDetail.value.tags : []
})

const weeklyRate = computed(() => {
  const denominator = weeklyResolved.value + totalMistakesInStats.value
  if (denominator === 0) {
    return '100%'
  }
  const rate = Math.min(100, Math.round((weeklyResolved.value / denominator) * 100))
  return `${rate}%`
})

function getDifficultyText(difficulty: number) {
  if (difficulty === 3) {
    return '困难'
  }
  if (difficulty === 2) {
    return '中等'
  }
  return '简单'
}

function formatTime(value?: string) {
  if (!value) {
    return '暂无记录'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

async function fetchStats() {
  statsLoading.value = true
  try {
    const stats = await getMistakeStats()
    resolvedToday.value = stats.todayResolved
    consecutiveReviewDays.value = stats.consecutiveReviewDays
    weeklyResolved.value = stats.weeklyResolved
    totalMistakesInStats.value = stats.totalMistakes
  } catch {
    // stats failure is non-blocking, keep default zeros
  } finally {
    statsLoading.value = false
  }
}

onMounted(() => {
  fetchMistakes()
  fetchStats()
})
</script>

<template>
  <main class="mx-auto max-w-4xl px-4 py-8 sm:px-6 sm:py-12">
    <div class="fade-in-up relative z-30 mb-8 flex flex-col items-start justify-between gap-4 border-b border-gray-200/50 pb-6 sm:flex-row sm:items-end">
      <div>
        <div class="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-rose-200 bg-rose-100 text-rose-500 shadow-sm">
          <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"
            />
          </svg>
        </div>
        <h1 class="mb-2 text-3xl font-extrabold text-gray-800">错题本</h1>
        <p class="font-medium text-gray-500">
          你有 <span class="mx-1 rounded-lg bg-rose-50 px-2 py-0.5 font-extrabold text-rose-500 shadow-sm">{{ total }}</span> 道错题待复习。坚持就是胜利！
        </p>
      </div>
      <div class="flex items-center gap-3">
        <div class="w-28">
          <AppSelect v-model="retryCount" :options="retryCountOptions" variant="rose" />
        </div>
        <button
          type="button"
          class="flex items-center gap-2 rounded-2xl bg-gradient-to-r from-rose-400 to-orange-400 px-6 py-3 text-sm font-bold text-white shadow-[0_8px_20px_rgba(244,63,94,0.3)] transition-all hover:from-rose-500 hover:to-orange-500 hover:shadow-[0_10px_25px_rgba(244,63,94,0.4)] active:scale-95"
          :disabled="loading"
          @click="startRetry"
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"
            />
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          开始错题重练
        </button>
      </div>
    </div>

    <section class="fade-in-up delay-100 relative z-10 mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
      <div class="rounded-2xl border border-white bg-white/80 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-md">
        <p class="mb-2 text-xs font-bold text-gray-400">本周重练完成率</p>
        <p class="text-2xl font-extrabold text-rose-500">{{ weeklyRate }}</p>
      </div>
      <div class="rounded-2xl border border-white bg-white/80 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-md">
        <p class="mb-2 text-xs font-bold text-gray-400">连续复习天数</p>
        <p class="text-2xl font-extrabold text-emerald-500">{{ consecutiveReviewDays }} 天</p>
      </div>
      <div class="rounded-2xl border border-white bg-white/80 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-md">
        <p class="mb-2 text-xs font-bold text-gray-400">今日已解决错题</p>
        <p class="text-2xl font-extrabold text-orange-500">{{ resolvedToday }} 题</p>
      </div>
    </section>

    <div class="relative z-30 mb-4 flex justify-end gap-3">
      <div class="w-36">
        <AppSelect
          v-model="sortBy"
          :options="sortOptions"
          placeholder="最近错题"
          variant="rose"
          @change="page = 1; fetchMistakes()"
        />
      </div>
      <div class="w-48">
        <AppSelect
          v-model="selectedModuleId"
          :options="moduleOptions"
          placeholder="全部模块"
          variant="rose"
          @change="page = 1; fetchMistakes()"
        />
      </div>
    </div>

    <div
      v-if="error"
      class="relative z-10 mb-4 rounded-2xl border border-rose-100 bg-rose-50/80 p-4 text-sm font-bold text-rose-500"
    >
      {{ error }}
    </div>

    <div
      v-if="loading"
      class="relative z-10 mb-4 rounded-2xl border border-white bg-white/80 p-4 text-sm font-bold text-gray-500"
    >
      正在加载错题列表...
    </div>

    <div class="relative z-10 space-y-4">
      <article
        v-for="(item, index) in pagedMistakes"
        :key="item.questionId"
        class="mistake-item fade-in-up group relative flex flex-col justify-between gap-5 overflow-hidden rounded-3xl border border-white bg-white/80 p-5 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md sm:flex-row sm:items-center sm:p-6"
        :class="index % 2 === 0 ? 'delay-100' : 'delay-200'"
      >
        <div class="absolute bottom-0 left-0 top-0 w-1.5 bg-rose-400 opacity-0 transition-opacity group-hover:opacity-100" />
        <div class="flex-grow">
          <div class="mb-3 flex items-center gap-3">
            <span class="rounded-xl border px-2.5 py-1 text-xs font-bold shadow-sm" :class="getTypeClass(item.type)">
              {{ getTypeLabel(item.type) }}
            </span>
            <span class="flex items-center gap-1 text-xs font-bold text-gray-400">
              <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z"
                />
              </svg>
              {{ item.moduleName }}
            </span>
          </div>
          <h3 class="mb-2 line-clamp-2 text-base font-extrabold leading-relaxed text-gray-800">{{ item.stem }}</h3>
          <p class="flex w-fit items-center gap-1 rounded-xl border border-rose-100 bg-rose-50 px-2 py-1 text-sm font-bold text-rose-500 shadow-sm">
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l2.5 2.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            最近错题时间: {{ formatTime(item.lastWrongAt) }}
          </p>
          <p class="mt-2 text-xs font-bold text-gray-400">难度：{{ getDifficultyText(item.difficulty) }}</p>
          <p class="mt-1 text-xs font-bold text-gray-400">题目ID：{{ item.questionId }}</p>
          <p class="mt-1 text-xs font-bold text-gray-400">模块ID：{{ item.moduleId }}</p>
          <p class="mt-1 text-xs font-bold text-gray-400">支持查看解析、正确答案与最近错答记录</p>
        </div>
        <div class="flex flex-shrink-0 gap-2 sm:flex-col">
          <button
            type="button"
            class="w-full rounded-2xl border border-orange-100 bg-orange-50 px-4 py-2 text-sm font-bold text-orange-500 shadow-sm transition-colors hover:bg-orange-100 sm:w-auto"
            @click="openParseModal(item)"
          >
            查看解析
          </button>
          <button
            type="button"
            class="w-full rounded-2xl border border-rose-100 bg-rose-50 px-4 py-2 text-sm font-bold text-rose-500 shadow-sm transition-colors hover:bg-rose-100 sm:w-auto"
            @click="goRetry(item)"
          >
            错题跟练
          </button>
          <button
            type="button"
            class="w-full rounded-2xl border border-gray-100 bg-white/60 px-4 py-2 text-sm font-bold text-gray-500 shadow-sm transition-colors hover:bg-rose-50 hover:text-rose-500 sm:w-auto"
            :disabled="loading"
            @click="removeItem(item.questionId)"
          >
            移除
          </button>
        </div>
      </article>
    </div>

    <div
      v-if="empty"
      class="relative z-10 mt-4 rounded-2xl border border-white bg-white/80 p-6 text-center text-sm font-bold text-gray-500 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-sm"
    >
      当前没有错题，继续保持，去题库挑战新题吧。
    </div>

    <p class="relative z-10 mt-4 min-h-[20px] text-sm font-bold" :class="getFeedbackClass()">
      {{ feedbackText }}
    </p>

    <div v-if="!empty" class="fade-in-up delay-200 relative z-10 mt-10 flex justify-center">
      <nav class="flex items-center space-x-2 rounded-2xl border border-white bg-white/80 p-1.5 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-sm">
        <button
          type="button"
          class="rounded-xl p-2 transition-colors"
          :class="canPrev ? 'text-gray-600 hover:bg-rose-50 hover:text-rose-500' : 'cursor-not-allowed text-gray-400'"
          :disabled="!canPrev"
          @click="prevPage"
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <button
          v-for="n in totalPages"
          :key="n"
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-xl text-sm font-bold transition-colors"
          :class="
            page === n
              ? 'bg-gradient-to-r from-rose-400 to-orange-400 text-white shadow-[0_4px_10px_rgba(244,63,94,0.3)]'
              : 'text-gray-600 hover:bg-rose-50 hover:text-rose-500'
          "
          @click="page = n; fetchMistakes()"
        >
          {{ n }}
        </button>
        <button
          type="button"
          class="rounded-xl p-2 transition-colors"
          :class="canNext ? 'text-gray-600 hover:bg-rose-50 hover:text-rose-500' : 'cursor-not-allowed text-gray-400'"
          :disabled="!canNext"
          @click="nextPage"
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </nav>
    </div>
  </main>

  <div v-if="parseOpen && parseTarget" class="fixed inset-0 z-[80] flex items-end justify-center sm:items-center" @click.self="closeParseModal">
    <div class="absolute inset-0 bg-black/35 backdrop-blur-[2px]" />
    <div class="parse-modal-panel opened relative mx-0 w-full rounded-t-3xl border border-white bg-white p-6 shadow-[0_16px_40px_rgba(0,0,0,0.12)] sm:mx-4 sm:w-[680px] sm:rounded-3xl sm:p-8">
      <div class="mb-4 flex items-start justify-between gap-3">
        <div>
          <p class="mb-1 text-xs font-bold text-rose-500">题目解析</p>
          <h3 class="text-lg font-extrabold text-gray-800">{{ parseDetail?.stem || parseTarget.stem }}</h3>
        </div>
        <button
          type="button"
          class="h-9 w-9 rounded-xl bg-gray-100 text-lg font-bold text-gray-500 transition-colors hover:bg-rose-50 hover:text-rose-500"
          @click="closeParseModal"
        >
          ×
        </button>
      </div>
      <div v-if="parseLoading" class="rounded-2xl border border-white bg-white/70 p-4 text-sm font-bold text-gray-500">正在加载解析...</div>
      <div v-else-if="parseError" class="rounded-2xl border border-rose-100 bg-rose-50 p-4 text-sm font-bold text-rose-500">{{ parseError }}</div>
      <div v-else-if="parseDetail" class="space-y-3 text-sm font-bold">
        <div class="rounded-2xl border border-rose-100 bg-rose-50/70 p-3">
          <p class="mb-1 text-gray-500">最近错题时间</p>
          <p class="text-rose-500">{{ formatTime(parseDetail.lastWrongAt) }}</p>
        </div>
        <div class="rounded-2xl border border-gray-100 bg-white p-3">
          <p class="mb-1 text-gray-500">题型 / 难度</p>
          <p class="leading-relaxed text-gray-700">{{ getTypeLabel(parseDetail.type) }} / {{ getDifficultyText(parseDetail.difficulty) }}</p>
        </div>
        <div class="rounded-2xl border border-emerald-100 bg-emerald-50/60 p-3">
          <p class="mb-1 text-gray-500">正确答案</p>
          <p class="text-emerald-600">{{ formatAnswers(parseDetail.correctAnswers) }}</p>
        </div>
        <div class="rounded-2xl border border-orange-100 bg-orange-50/60 p-3">
          <p class="mb-1 text-gray-500">最近错误答案</p>
          <p class="text-orange-500">{{ formatAnswers(parseDetail.lastWrongAnswers) }}</p>
        </div>
        <div class="rounded-2xl border border-gray-100 bg-white p-3">
          <p class="mb-2 text-gray-500">选项</p>
          <div class="space-y-2">
            <p v-for="option in parseDetail.options" :key="option.key" class="leading-relaxed text-gray-700">
              <span class="mr-1 font-extrabold">{{ option.key }}.</span>{{ option.text }}
            </p>
          </div>
        </div>
        <div class="rounded-2xl border border-gray-100 bg-white p-3">
          <p class="mb-2 text-gray-500">解析</p>
          <div class="space-y-2">
            <p v-for="(line, idx) in parseExplanations" :key="idx" class="leading-relaxed text-gray-700">{{ line }}</p>
          </div>
        </div>
        <div v-if="parseTags.length" class="rounded-2xl border border-indigo-100 bg-indigo-50/60 p-3">
          <p class="mb-2 text-gray-500">知识点</p>
          <div class="flex flex-wrap gap-2">
            <span v-for="tag in parseTags" :key="tag" class="rounded-lg bg-indigo-100 px-2 py-1 text-xs font-extrabold text-indigo-600">{{ tag }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fade-in-up {
  animation: fadeInUp 0.5s ease-out forwards;
  opacity: 0;
}

.delay-100 {
  animation-delay: 0.1s;
}

.delay-200 {
  animation-delay: 0.2s;
}

.mistake-item {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.mistake-item:hover {
  transform: translateX(8px);
  border-color: #fecdd3;
  box-shadow: 0 10px 25px -5px rgba(225, 29, 72, 0.1);
}

.parse-modal-panel {
  transform: translateY(16px);
  transition: all 0.25s ease;
  opacity: 0;
}

.parse-modal-panel.opened {
  transform: translateY(0);
  opacity: 1;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
