<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  type AdminModule,
  generateQuestions,
  listAdminModules,
} from '@/api/modules/admin'
import AppSelect from '@/components/common/AppSelect.vue'
import { showErrorToast } from '@/utils/toast'

const router = useRouter()

const modules = ref<AdminModule[]>([])
const selectedCategoryId = ref<number | null>(null)
const selectedModuleId = ref<number | null>(null)
const count = ref(5)
const difficultyFilter = ref<string>('ALL')
const typeFilter = ref<string>('ALL')
const generating = ref(false)
const result = ref<{ generatedCount: number; questionIds: number[] } | null>(null)
const loadingModules = ref(true)
const error = ref<string | null>(null)

const difficultyOptions = [
  { label: '全部难度', value: 'ALL' },
  { label: '简单', value: '1' },
  { label: '中等', value: '2' },
  { label: '困难', value: '3' },
]

const typeOptions = [
  { label: '全部题型', value: 'ALL' },
  { label: '单选题', value: '1' },
  { label: '多选题', value: '2' },
  { label: '判断题', value: '3' },
]

// Derive distinct categories from modules
const categories = computed(() => {
  const seen = new Map<number, { id: number; name: string; directionName: string }>()
  for (const m of modules.value) {
    if (!seen.has(m.categoryId)) {
      seen.set(m.categoryId, { id: m.categoryId, name: m.categoryName, directionName: m.directionName })
    }
  }
  return Array.from(seen.values()).sort((a, b) => a.name.localeCompare(b.name))
})

const categoryOptions = computed(() => [
  { label: '请选择分类', value: '' },
  ...categories.value.map((c) => ({ label: `${c.name}（${c.directionName}）`, value: c.id })),
])

const filteredModules = computed(() =>
  modules.value.filter((m) => m.categoryId === selectedCategoryId.value),
)

const moduleOptions = computed(() => [
  { label: '请选择模块', value: '' },
  ...filteredModules.value.map((m) => ({
    label: `${m.name}（${m.directionName} / ${m.categoryName}）`,
    value: m.id,
  })),
])

async function loadModules() {
  loadingModules.value = true
  error.value = null
  try {
    const data = await listAdminModules({})
    modules.value = data.list.filter((m) => m.status === 0)
  } catch (e) {
    error.value = '加载模块列表失败'
    showErrorToast(error.value, '加载失败', 'top-right')
  } finally {
    loadingModules.value = false
  }
}

function onCategoryChange() {
  selectedModuleId.value = null
}

async function doGenerate() {
  if (!selectedModuleId.value) {
    showErrorToast('请先选择目标模块', '参数错误', 'top-right')
    return
  }
  generating.value = true
  result.value = null
  try {
    const res = await generateQuestions({
      moduleId: selectedModuleId.value,
      count: count.value,
      difficulty:
        difficultyFilter.value !== 'ALL'
          ? (Number(difficultyFilter.value) as 1 | 2 | 3)
          : undefined,
      type:
        typeFilter.value !== 'ALL'
          ? (Number(typeFilter.value) as 1 | 2 | 3)
          : undefined,
    })
    result.value = res
  } catch (e) {
    const message = e instanceof Error ? e.message : 'AI 生成失败，请重试'
    showErrorToast(message, '生成失败', 'top-right')
  } finally {
    generating.value = false
  }
}

function goToDrafts() {
  router.push('/admin/questions/drafts')
}

function reset() {
  result.value = null
}

onMounted(() => {
  loadModules()
})
</script>

<template>
  <section class="flex min-h-full items-start justify-center p-8 pt-12">
    <div class="fade-in w-full max-w-3xl">
      <!-- Header -->
      <div class="mb-8 text-center">
        <div
          class="mx-auto mb-4 inline-flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-purple-100 to-indigo-100 shadow-sm"
        >
          <svg class="h-8 w-8 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z"
            />
          </svg>
        </div>
        <h1 class="text-2xl font-extrabold text-gray-800">AI 智能生成题目</h1>
        <p class="mt-2 text-sm font-medium text-gray-500">
          选择分类与模块并设置参数，AI 将自动生成题目并保存为草稿
        </p>
      </div>

      <!-- Error State -->
      <div
        v-if="error"
        class="fade-in mb-6 rounded-2xl border border-rose-200 bg-rose-50 p-6 text-center"
      >
        <p class="text-sm font-bold text-rose-600">{{ error }}</p>
        <button
          class="mt-3 rounded-xl bg-rose-500 px-5 py-2 text-sm font-bold text-white transition-all hover:bg-rose-600"
          @click="loadModules"
        >
          重试
        </button>
      </div>

      <!-- Loading -->
      <div v-if="loadingModules" class="flex items-center justify-center py-20">
        <div
          class="h-8 w-8 animate-spin rounded-full border-4 border-purple-200 border-t-purple-500"
        />
      </div>

      <!-- Form Card -->
      <div
        v-if="!loadingModules && !error"
        class="relative rounded-3xl border border-white bg-white/80 p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-sm"
      >
        <!-- Pending Overlay -->
        <Transition name="overlay">
          <div
            v-if="generating"
            class="absolute inset-0 z-20 flex flex-col items-center justify-center rounded-3xl bg-white/90 backdrop-blur-sm"
          >
            <div class="relative mb-5">
              <div
                class="h-16 w-16 animate-spin rounded-full border-4 border-purple-200 border-t-purple-500"
              />
              <div class="absolute inset-0 flex items-center justify-center">
                <svg class="h-7 w-7 animate-pulse text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z"
                  />
                </svg>
              </div>
            </div>
            <p class="text-sm font-extrabold text-purple-600">AI 正在生成题目...</p>
            <p class="mt-1 text-xs font-medium text-gray-400">请耐心等待，这可能需要几秒到几十秒</p>
          </div>
        </Transition>

        <!-- Category Select -->
        <div class="mb-5">
          <label class="mb-2 block text-sm font-extrabold text-gray-700">
            所属分类 <span class="text-rose-500">*</span>
          </label>
          <AppSelect
            v-model="selectedCategoryId"
            :options="categoryOptions"
            placeholder="请选择分类"
            variant="rose"
            @change="onCategoryChange"
          />
        </div>

        <!-- Module Select -->
        <div class="mb-5">
          <label class="mb-2 block text-sm font-extrabold text-gray-700">
            目标模块 <span class="text-rose-500">*</span>
          </label>
          <AppSelect
            v-model="selectedModuleId"
            :options="moduleOptions"
            placeholder="请先选择分类"
            variant="rose"
            :disabled="!selectedCategoryId"
          />
          <p v-if="!selectedCategoryId" class="mt-1 text-xs text-gray-400">先选择分类后才能选择模块</p>
        </div>

        <!-- Count Slider -->
        <div class="mb-5">
          <label class="mb-2 block text-sm font-extrabold text-gray-700">
            生成数量
          </label>
          <div class="flex items-center gap-4">
            <input
              v-model.number="count"
              type="range"
              min="1"
              max="20"
              class="h-2 flex-1 cursor-pointer appearance-none rounded-full bg-purple-100 accent-purple-500"
            />
            <span
              class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-purple-50 text-base font-extrabold text-purple-600"
            >
              {{ count }}
            </span>
          </div>
          <div class="mt-1 flex justify-between px-0.5 text-xs font-medium text-gray-400">
            <span>1 道</span>
            <span>20 道</span>
          </div>
        </div>

        <!-- Difficulty & Type side by side -->
        <div class="mb-6 grid grid-cols-2 gap-4">
          <div>
            <label class="mb-2 block text-sm font-extrabold text-gray-700">难度</label>
            <AppSelect
              v-model="difficultyFilter"
              :options="difficultyOptions"
              variant="rose"
            />
          </div>
          <div>
            <label class="mb-2 block text-sm font-extrabold text-gray-700">题型</label>
            <AppSelect
              v-model="typeFilter"
              :options="typeOptions"
              variant="rose"
            />
          </div>
        </div>

        <!-- Generate Button -->
        <button
          class="flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-purple-500 to-indigo-500 px-6 py-3.5 text-sm font-extrabold text-white shadow-md transition-all hover:from-purple-600 hover:to-indigo-600 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="generating || !selectedModuleId"
          @click="doGenerate"
        >
          <svg
            v-if="!generating"
            class="h-5 w-5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z"
            />
          </svg>
          {{ generating ? 'AI 正在生成中...' : '开始生成' }}
        </button>
      </div>

      <!-- Success Bubble -->
      <Transition name="bubble">
        <div
          v-if="result && !generating"
          class="bubble-pop mt-6 rounded-3xl border border-emerald-200 bg-gradient-to-br from-emerald-50 to-teal-50 p-6 shadow-[0_8px_30px_rgba(16,185,129,0.15)]"
        >
          <div class="mb-4 flex items-center gap-3">
            <div
              class="flex h-11 w-11 items-center justify-center rounded-full bg-emerald-400 shadow-sm"
            >
              <svg class="h-6 w-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2.5"
                  d="M5 13l4 4L19 7"
                />
              </svg>
            </div>
            <div>
              <h3 class="text-lg font-extrabold text-emerald-700">生成完成!</h3>
              <p class="text-sm font-medium text-emerald-600">
                成功生成
                <span class="font-extrabold">{{ result.generatedCount }}</span>
                道题目，已保存为草稿待审核
              </p>
            </div>
          </div>
          <div class="flex gap-3">
            <button
              class="flex-1 rounded-xl bg-emerald-500 px-5 py-2.5 text-sm font-bold text-white shadow-sm transition-all hover:bg-emerald-600"
              @click="goToDrafts"
            >
              前往草稿审核
            </button>
            <button
              class="rounded-xl border border-gray-200 bg-white px-5 py-2.5 text-sm font-bold text-gray-600 shadow-sm transition-all hover:bg-gray-50"
              @click="reset"
            >
              继续生成
            </button>
          </div>
        </div>
      </Transition>
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

.bubble-pop {
  animation: bubblePop 0.45s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
}

@keyframes bubblePop {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.overlay-enter-active {
  transition: opacity 0.25s ease-out;
}
.overlay-leave-active {
  transition: opacity 0.2s ease-in;
}
.overlay-enter-from,
.overlay-leave-to {
  opacity: 0;
}
</style>
