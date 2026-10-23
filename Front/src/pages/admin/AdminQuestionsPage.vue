<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppSelect from '@/components/common/AppSelect.vue'
import {
  deleteAdminQuestion,
  listAdminModules,
  listAdminQuestions,
  updateAdminQuestionStatus,
  type AdminModule,
  type AdminQuestion,
} from '@/api/modules/admin'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'
import { useDebounceRef } from '@/composables/useDebounceRef'

const keyword = ref('')
const debouncedKeyword = useDebounceRef(keyword)
const moduleIdFilter = ref<'ALL' | `${number}`>('ALL')
const typeFilter = ref<'ALL' | '1' | '2' | '3'>('ALL')
const difficultyFilter = ref<'ALL' | '1' | '2' | '3'>('ALL')
const statusFilter = ref<'ALL' | '0' | '1' | '2'>('ALL')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const modules = ref<AdminModule[]>([])
const questions = ref<AdminQuestion[]>([])
const loading = ref(false)
const updatingStatusQuestionIds = ref<number[]>([])
const deletingQuestionIds = ref<number[]>([])

const typeSelectOptions = [
  { label: '全部题型', value: 'ALL' },
  { label: '单选题', value: '1' },
  { label: '多选题', value: '2' },
  { label: '判断题', value: '3' },
]
const difficultySelectOptions = [
  { label: '全部难度', value: 'ALL' },
  { label: '简单', value: '1' },
  { label: '中等', value: '2' },
  { label: '困难', value: '3' },
]
const statusSelectOptions = [
  { label: '全部状态', value: 'ALL' },
  { label: '上架', value: '0' },
  { label: '下架', value: '1' },
  { label: '草稿', value: '2' },
]

const moduleSelectOptions = computed(() => [
  { label: '全部模块', value: 'ALL' },
  ...modules.value.map((item) => ({
    label: [item.directionName, item.categoryName, item.name].filter(Boolean).join(' / '),
    value: String(item.id) as `${number}`,
  })),
])
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const rangeStart = computed(() => (total.value === 0 ? 0 : (page.value - 1) * pageSize.value + 1))
const rangeEnd = computed(() => Math.min(total.value, rangeStart.value + questions.value.length - 1))

type PageItem = { type: 'page'; num: number } | { type: 'ellipsis' }
const visiblePages = computed<PageItem[]>(() => {
  const total = totalPages.value
  const current = page.value
  const items: PageItem[] = []
  const delta = 2 // pages to show on each side of current

  let left = Math.max(2, current - delta)
  let right = Math.min(total - 1, current + delta)

  if (current - delta <= 2) {
    right = Math.min(total - 1, 1 + delta * 2)
  }
  if (current + delta >= total - 1) {
    left = Math.max(2, total - delta * 2)
  }

  items.push({ type: 'page', num: 1 })
  if (left > 2) items.push({ type: 'ellipsis' })
  for (let i = left; i <= right; i++) {
    items.push({ type: 'page', num: i })
  }
  if (right < total - 1) items.push({ type: 'ellipsis' })
  if (total > 1) items.push({ type: 'page', num: total })

  return items
})

async function loadModuleOptions() {
  try {
    const moduleData = await listAdminModules({})
    modules.value = moduleData.list
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '模块筛选项加载失败', '加载失败', 'top-right')
  }
}

async function loadQuestions() {
  loading.value = true
  try {
    const data = await listAdminQuestions({
      keyword: debouncedKeyword.value.trim() || undefined,
      moduleId: moduleIdFilter.value === 'ALL' ? undefined : Number(moduleIdFilter.value),
      type: typeFilter.value === 'ALL' ? undefined : Number(typeFilter.value) as 1 | 2 | 3,
      difficulty: difficultyFilter.value === 'ALL' ? undefined : Number(difficultyFilter.value) as 1 | 2 | 3,
      status: statusFilter.value === 'ALL' ? undefined : Number(statusFilter.value) as 0 | 1 | 2,
      page: page.value,
      size: pageSize.value,
    })
    questions.value = data.list
    total.value = data.total
    page.value = data.page
    pageSize.value = data.size
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '题目列表加载失败', '加载失败', 'top-right')
  } finally {
    loading.value = false
  }
}

function goPrev() {
  if (page.value > 1 && !loading.value) {
    page.value -= 1
  }
}

function goNext() {
  if (page.value < totalPages.value && !loading.value) {
    page.value += 1
  }
}

function goPage(target: number) {
  if (loading.value || target === page.value) {
    return
  }
  page.value = target
}

function getTypeText(type: number) {
  if (type === 1) {
    return '单选题'
  }
  if (type === 2) {
    return '多选题'
  }
  return '判断题'
}

function getTypeClass(type: number) {
  if (type === 1) {
    return 'bg-emerald-50 text-emerald-600 border-emerald-100'
  }
  if (type === 2) {
    return 'bg-orange-50 text-orange-600 border-orange-100'
  }
  return 'bg-blue-50 text-blue-600 border-blue-100'
}

function getDifficultyText(difficulty: number) {
  if (difficulty === 1) {
    return '简单'
  }
  if (difficulty === 2) {
    return '中等'
  }
  return '困难'
}

function getDifficultyClass(difficulty: number) {
  if (difficulty === 1) {
    return 'bg-emerald-50 text-emerald-600 border-emerald-100'
  }
  if (difficulty === 2) {
    return 'bg-orange-50 text-orange-600 border-orange-100'
  }
  return 'bg-rose-50 text-rose-600 border-rose-100'
}

function getStatusText(status: number) {
  if (status === 0) return '上架'
  if (status === 1) return '下架'
  return '草稿'
}

function getStatusClass(status: number) {
  if (status === 0) return 'bg-emerald-50 text-emerald-600 border-emerald-100'
  if (status === 1) return 'bg-rose-50 text-rose-600 border-rose-100'
  return 'bg-amber-50 text-amber-600 border-amber-100'
}

function isUpdatingStatus(questionId: number) {
  return updatingStatusQuestionIds.value.includes(questionId)
}

function isDeletingQuestion(questionId: number) {
  return deletingQuestionIds.value.includes(questionId)
}

async function toggleQuestionStatus(target: AdminQuestion) {
  if (isUpdatingStatus(target.id) || isDeletingQuestion(target.id)) {
    return
  }
  updatingStatusQuestionIds.value.push(target.id)
  const nextStatus = target.status === 0 ? 1 : 0
  try {
    await updateAdminQuestionStatus(target.id, { status: nextStatus as 0 | 1 })
    target.status = nextStatus
    showActionSuccessToast(nextStatus === 0 ? '上架题目' : '下架题目', 'top-right')
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '题目状态更新失败', '操作失败', 'top-right')
  } finally {
    updatingStatusQuestionIds.value = updatingStatusQuestionIds.value.filter((id) => id !== target.id)
  }
}

async function removeQuestion(target: AdminQuestion) {
  if (isDeletingQuestion(target.id) || isUpdatingStatus(target.id)) {
    return
  }
  if (!window.confirm(`确认删除题目 #${target.id} 吗？`)) {
    return
  }
  deletingQuestionIds.value.push(target.id)
  try {
    await deleteAdminQuestion(target.id)
    showActionSuccessToast('删除题目', 'top-right')
    if (questions.value.length === 1 && page.value > 1) {
      page.value -= 1
    } else {
      await loadQuestions()
    }
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '删除题目失败', '操作失败', 'top-right')
  } finally {
    deletingQuestionIds.value = deletingQuestionIds.value.filter((id) => id !== target.id)
  }
}

onMounted(async () => {
  await loadModuleOptions()
  await loadQuestions()
})

watch(page, () => {
  loadQuestions()
})

watch([debouncedKeyword, moduleIdFilter, typeFilter, difficultyFilter, statusFilter], () => {
  if (page.value === 1) {
    loadQuestions()
    return
  }
  page.value = 1
})
</script>

<template>
  <section class="p-8">
    <div class="fade-in mx-auto w-full">
      <div class="mb-6 flex flex-col items-start justify-between gap-4 xl:flex-row xl:items-center">
        <div class="flex w-full flex-wrap gap-3 xl:w-auto">
          <div class="min-w-[180px]">
            <AppSelect
              v-model="moduleIdFilter"
              :options="moduleSelectOptions"
              variant="rose"
            />
          </div>
          <AppSelect
            v-model="typeFilter"
            :options="typeSelectOptions"
            variant="rose"
          />
          <AppSelect
            v-model="difficultyFilter"
            :options="difficultySelectOptions"
            variant="rose"
          />
          <AppSelect
            v-model="statusFilter"
            :options="statusSelectOptions"
            variant="rose"
          />
          <div class="relative w-44">
            <input
              v-model="keyword"
              type="text"
              placeholder="搜索题干..."
              class="w-full rounded-2xl border border-white bg-white/80 py-2.5 pl-11 pr-4 text-sm font-medium shadow-sm transition-all focus:border-rose-400 focus:outline-none focus:ring-1 focus:ring-rose-400"
            />
            <svg class="absolute left-3.5 top-2.5 h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
        </div>

        <div class="flex w-full gap-3 xl:w-auto">
          <RouterLink
            to="/admin/questions/import"
            class="flex flex-1 items-center justify-center gap-2 rounded-2xl border border-orange-100 bg-orange-50 px-5 py-2.5 text-sm font-bold text-orange-600 shadow-sm transition-all hover:border-orange-200 hover:bg-orange-100 hover:text-orange-700 xl:flex-none"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
            </svg>
            导入题目
          </RouterLink>
          <RouterLink
            to="/admin/questions/drafts"
            class="flex flex-1 items-center justify-center gap-2 rounded-2xl border border-amber-100 bg-amber-50 px-5 py-2.5 text-sm font-bold text-amber-600 shadow-sm transition-all hover:border-amber-200 hover:bg-amber-100 xl:flex-none"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            草稿审核
          </RouterLink>
          <RouterLink
            to="/admin/questions/generate"
            class="flex flex-1 items-center justify-center gap-2 rounded-2xl border border-purple-100 bg-purple-50 px-5 py-2.5 text-sm font-bold text-purple-600 shadow-sm transition-all hover:border-purple-200 hover:bg-purple-100 xl:flex-none"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            AI 生成
          </RouterLink>
          <RouterLink
            to="/admin/questions/new"
            class="flex flex-1 items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-rose-400 to-orange-400 px-5 py-2.5 text-sm font-bold text-white shadow-[0_8px_20px_rgba(244,63,94,0.3)] transition-all hover:from-rose-500 hover:to-orange-500 hover:shadow-[0_10px_25px_rgba(244,63,94,0.4)] active:scale-95 xl:flex-none"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            添加题目
          </RouterLink>
        </div>
      </div>

      <div class="overflow-hidden rounded-3xl border border-white bg-white/80 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-lg">
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-100/50 text-sm">
            <thead class="bg-white/50">
              <tr>
                <th class="w-20 px-6 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">ID</th>
                <th class="w-24 px-6 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">模块</th>
                <th class="w-24 px-6 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">题型</th>
                <th class="w-24 px-6 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">难度</th>
                <th class="px-6 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">题干</th>
                <th class="w-24 px-6 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">状态</th>
                <th class="w-40 px-6 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">更新时间</th>
                <th class="w-32 px-6 py-4 text-right text-xs font-extrabold uppercase tracking-wider text-gray-400">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100/50 bg-transparent">
              <tr v-for="item in questions" :key="item.id" class="group transition-colors hover:bg-white">
                <td class="whitespace-nowrap px-6 py-5 font-medium text-gray-500">#{{ item.id }}</td>
                <td class="whitespace-nowrap px-6 py-5">
                  <span class="inline-flex items-center gap-1.5 rounded-xl border px-2.5 py-1 text-xs font-bold shadow-sm bg-rose-50 text-rose-500 border-rose-100">
                    {{ item.moduleName }}
                  </span>
                </td>
                <td class="whitespace-nowrap px-6 py-5">
                  <span class="rounded-xl border px-3 py-1 text-xs font-bold shadow-sm" :class="getTypeClass(item.type)">
                    {{ getTypeText(item.type) }}
                  </span>
                </td>
                <td class="whitespace-nowrap px-6 py-5">
                  <span class="rounded-xl border px-3 py-1 text-xs font-bold shadow-sm" :class="getDifficultyClass(item.difficulty)">
                    {{ getDifficultyText(item.difficulty) }}
                  </span>
                </td>
                <td class="max-w-md truncate px-6 py-5 font-bold text-gray-800 transition-colors group-hover:text-rose-500">{{ item.stem }}</td>
                <td class="whitespace-nowrap px-6 py-5">
                  <span class="rounded-xl border px-3 py-1 text-xs font-bold shadow-sm" :class="getStatusClass(item.status)">
                    {{ getStatusText(item.status) }}
                  </span>
                </td>
                <td class="whitespace-nowrap px-6 py-5 font-medium text-gray-500">{{ item.updatedAt }}</td>
                <td class="whitespace-nowrap px-6 py-5 text-right font-bold">
                  <div class="flex items-center justify-end gap-1">
                    <button
                      v-if="item.status !== 2"
                      type="button"
                      class="rounded-lg px-2 py-1 text-blue-500 transition-colors hover:bg-blue-50 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-50"
                      :disabled="isUpdatingStatus(item.id) || isDeletingQuestion(item.id)"
                      @click="toggleQuestionStatus(item)"
                    >
                      {{ isUpdatingStatus(item.id) ? '处理中...' : item.status === 0 ? '下架' : '上架' }}
                    </button>
                    <RouterLink
                      :to="`/admin/questions/${item.id}`"
                      class="rounded-lg px-2 py-1 text-orange-500 transition-colors hover:bg-orange-50 hover:text-orange-600"
                    >
                      编辑
                    </RouterLink>
                    <button
                      type="button"
                      class="rounded-lg px-2 py-1 text-rose-500 transition-colors hover:bg-rose-50 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-50"
                      :disabled="isDeletingQuestion(item.id) || isUpdatingStatus(item.id)"
                      @click="removeQuestion(item)"
                    >
                      {{ isDeletingQuestion(item.id) ? '删除中...' : '删除' }}
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!questions.length">
                <td colspan="8" class="px-6 py-10 text-center text-sm font-bold text-gray-400">暂无题目数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div
        class="mt-6 flex items-center justify-between rounded-3xl border border-white bg-white/80 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] backdrop-blur-md"
      >
        <div class="text-sm font-medium text-gray-500">
          显示 <span class="font-bold text-gray-800">{{ rangeStart }}</span> 到 <span class="font-bold text-gray-800">{{ rangeEnd }}</span> 条，共
          <span class="font-bold text-gray-800">{{ total }}</span> 条
        </div>
        <div class="flex space-x-2">
          <button
            type="button"
            class="rounded-xl border px-3 py-1.5 text-sm font-bold"
            :class="
              page === 1 || loading
                ? 'cursor-not-allowed border-white bg-white/50 text-gray-400'
                : 'border-white bg-white text-gray-600 shadow-sm transition-colors hover:bg-rose-50 hover:text-rose-500'
            "
            :disabled="page === 1 || loading"
            @click="goPrev"
          >
            上一页
          </button>
          <template v-for="item in visiblePages" :key="item.type === 'ellipsis' ? 'dots' : item.num">
            <span
              v-if="item.type === 'ellipsis'"
              class="inline-flex items-center px-1 text-sm font-bold text-gray-400"
            >...</span>
            <button
              v-else
              type="button"
              class="rounded-xl px-3.5 py-1.5 text-sm font-bold"
              :class="
                page === item.num
                  ? 'bg-gradient-to-r from-rose-400 to-orange-400 text-white shadow-[0_4px_10px_rgba(244,63,94,0.3)]'
                  : 'border border-white bg-white text-gray-600 shadow-sm transition-colors hover:bg-rose-50 hover:text-rose-500'
              "
              @click="goPage(item.num)"
            >
              {{ item.num }}
            </button>
          </template>
          <button
            type="button"
            class="rounded-xl border px-3 py-1.5 text-sm font-bold"
            :class="
              page === totalPages || loading
                ? 'cursor-not-allowed border-white bg-white/50 text-gray-400'
                : 'border-white bg-white text-gray-600 shadow-sm transition-colors hover:bg-rose-50 hover:text-rose-500'
            "
            :disabled="page === totalPages || loading"
            @click="goNext"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

  </section>
</template>

<style scoped>
.fade-in {
  animation: fadeIn 0.4s ease-out forwards;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
