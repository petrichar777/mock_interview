<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSelect from '@/components/common/AppSelect.vue'
import { createAdminQuestion, getAdminQuestionDetail, listAdminModules, updateAdminQuestion, type AdminModule } from '@/api/modules/admin'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'

type OptionItem = {
  id: number
  key: string
  text: string
  correct: boolean
  sort: number
}

const route = useRoute()
const router = useRouter()

const isCreate = computed(() => route.params.id === 'new')
const questionId = computed(() => Number(route.params.id))
const saving = ref(false)
const loading = ref(false)
const moduleIdValue = ref('')
const typeValue = ref<'1' | '2' | '3'>('1')
const difficultyValue = ref<'1' | '2' | '3'>('1')
const statusValue = ref<'0' | '1'>('0')
const stem = ref('')
const analysis = ref('')
const tags = ref('')
const options = ref<OptionItem[]>([
  { id: 1, key: 'A', text: '', correct: true, sort: 1 },
  { id: 2, key: 'B', text: '', correct: false, sort: 2 },
])
const nextOptionId = ref(3)
const modules = ref<AdminModule[]>([])

const moduleSelectOptions = computed(() =>
  modules.value.map((item) => ({
    label: [item.directionName, item.categoryName, item.name].filter(Boolean).join(' / '),
    value: String(item.id),
  })),
)
const typeSelectOptions = [
  { label: '单选题', value: '1' },
  { label: '多选题', value: '2' },
  { label: '判断题', value: '3' },
]
const difficultySelectOptions = [
  { label: '简单', value: '1' },
  { label: '中等', value: '2' },
  { label: '困难', value: '3' },
]
const statusOptions = [
  { label: '上架', value: '0' },
  { label: '下架', value: '1' },
]

const canAddOption = computed(() => typeValue.value !== '3' && options.value.length < 6)

function resetOptionsByType(type: '1' | '2' | '3') {
  if (type === '3') {
    options.value = [
      { id: 1, key: 'T', text: '正确', correct: true, sort: 1 },
      { id: 2, key: 'F', text: '错误', correct: false, sort: 2 },
    ]
    nextOptionId.value = 3
    return
  }
  if (options.value[0]?.key === 'T') {
    options.value = [
      { id: 1, key: 'A', text: '', correct: true, sort: 1 },
      { id: 2, key: 'B', text: '', correct: false, sort: 2 },
    ]
    nextOptionId.value = 3
  }
}

let suppressTypeWatch = false

watch(typeValue, (type) => {
  if (suppressTypeWatch) return
  resetOptionsByType(type)
})

function setCorrect(optionId: number) {
  if (typeValue.value === '2') {
    options.value = options.value.map((item) => (item.id === optionId ? { ...item, correct: !item.correct } : item))
    return
  }
  options.value = options.value.map((item) => ({ ...item, correct: item.id === optionId }))
}

function addOption() {
  if (!canAddOption.value) {
    return
  }
  const index = options.value.length
  const key = String.fromCharCode(65 + index)
  options.value.push({
    id: nextOptionId.value,
    key,
    text: '',
    correct: false,
    sort: index + 1,
  })
  nextOptionId.value += 1
}

function removeOption(optionId: number) {
  if (options.value.length <= 2 || typeValue.value === '3') {
    return
  }
  options.value = options.value.filter((item) => item.id !== optionId)
  options.value = options.value.map((item, index) => ({
    ...item,
    key: String.fromCharCode(65 + index),
    sort: index + 1,
  }))
  if (!options.value.some((item) => item.correct)) {
    options.value[0].correct = true
  }
}

async function loadModules() {
  try {
    const data = await listAdminModules({})
    modules.value = data.list
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '模块列表加载失败', '加载失败', 'top-right')
  }
}

async function loadQuestionDetail() {
  if (isCreate.value || !Number.isFinite(questionId.value)) {
    return
  }
  loading.value = true
  try {
    const data = await getAdminQuestionDetail(questionId.value)
    suppressTypeWatch = true
    moduleIdValue.value = String(data.moduleId)
    typeValue.value = String(data.type) as '1' | '2' | '3'
    suppressTypeWatch = false
    difficultyValue.value = String(data.difficulty) as '1' | '2' | '3'
    statusValue.value = String(data.status) as '0' | '1'
    stem.value = data.stem
    analysis.value = data.analysis || ''
    tags.value = data.tags || ''
    const sortedOptions = [...data.options].sort((a, b) => a.sort - b.sort)
    options.value = sortedOptions.map((item, index) => ({
      id: index + 1,
      key: item.key,
      text: item.text,
      correct: item.correct,
      sort: item.sort || index + 1,
    }))
    nextOptionId.value = options.value.length + 1
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '题目详情加载失败', '加载失败', 'top-right')
  } finally {
    loading.value = false
  }
}

function validateForm() {
  if (!moduleIdValue.value) {
    showErrorToast('请选择所属模块', '参数错误', 'top-right')
    return false
  }
  if (!stem.value.trim()) {
    showErrorToast('请输入题干', '参数错误', 'top-right')
    return false
  }
  if (typeValue.value !== '3' && options.value.some((item) => !item.text.trim())) {
    showErrorToast('请完善选项内容', '参数错误', 'top-right')
    return false
  }
  const correctCount = options.value.filter((item) => item.correct).length
  if (typeValue.value === '1' || typeValue.value === '3') {
    if (correctCount !== 1) {
      showErrorToast('单选题或判断题必须且只能有一个正确答案', '参数错误', 'top-right')
      return false
    }
  } else if (correctCount < 1) {
    showErrorToast('多选题至少需要一个正确答案', '参数错误', 'top-right')
    return false
  }
  return true
}

async function submitForm() {
  if (!validateForm()) {
    return
  }
  saving.value = true
  try {
    const payload = {
      moduleId: Number(moduleIdValue.value),
      type: Number(typeValue.value),
      difficulty: Number(difficultyValue.value),
      stem: stem.value.trim(),
      analysis: analysis.value.trim() || undefined,
      tags: tags.value.trim() || undefined,
      status: Number(statusValue.value),
      options: options.value.map((item, index) => ({
        key: item.key,
        text: item.text.trim(),
        correct: item.correct,
        sort: index + 1,
      })),
    }
    if (isCreate.value) {
      await createAdminQuestion(payload)
      showActionSuccessToast('新增题目', 'top-right')
    } else {
      if (!Number.isFinite(questionId.value)) {
        showErrorToast('题目ID无效', '参数错误', 'top-right')
        return
      }
      await updateAdminQuestion(questionId.value, payload)
      showActionSuccessToast('更新题目', 'top-right')
    }
    await router.push('/admin/questions')
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '保存题目失败', '保存失败', 'top-right')
  } finally {
    saving.value = false
  }
}

function getOptionWrapClass(item: OptionItem) {
  if (item.correct) {
    return 'bg-emerald-50/50 border-emerald-200 hover:border-emerald-300'
  }
  return 'bg-white border-white hover:border-orange-300'
}

function getOptionKeyClass(item: OptionItem) {
  if (item.correct) {
    return 'text-emerald-600'
  }
  return 'text-gray-500'
}

onMounted(async () => {
  await loadModules()
  await loadQuestionDetail()
})
</script>

<template>
  <section class="p-8">
    <div
      class="fade-in relative mx-auto max-w-4xl overflow-hidden rounded-3xl border border-white bg-white/80 p-8 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md sm:p-10"
    >
      <div class="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-orange-50 opacity-50 blur-3xl" />

      <form class="relative z-10 space-y-8" @submit.prevent="submitForm">
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div class="group">
            <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-orange-500">所属模块</label>
            <AppSelect
              v-model="moduleIdValue"
              :options="moduleSelectOptions"
              variant="orange"
            />
          </div>

          <div class="group">
            <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-orange-500">题型</label>
            <AppSelect
              v-model="typeValue"
              :options="typeSelectOptions"
              variant="orange"
            />
          </div>
        </div>

        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div class="group">
            <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-orange-500">难度</label>
            <AppSelect
              v-model="difficultyValue"
              :options="difficultySelectOptions"
              variant="orange"
            />
          </div>

          <div class="group">
            <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-orange-500">状态</label>
            <AppSelect
              v-model="statusValue"
              :options="statusOptions"
              variant="orange"
            />
          </div>
        </div>

        <div class="group">
          <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-orange-500">题目内容 (支持 Markdown)</label>
          <textarea
            v-model="stem"
            rows="4"
            class="w-full resize-y rounded-2xl border border-white bg-white/60 px-4 py-3.5 font-medium leading-relaxed text-gray-700 shadow-sm outline-none transition-all focus:border-transparent focus:ring-2 focus:ring-orange-400"
            placeholder="输入题目正文..."
            :disabled="loading || saving"
          />
        </div>

        <div class="group">
          <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-orange-500">标签 (逗号分隔)</label>
          <input
            v-model="tags"
            type="text"
            class="w-full rounded-2xl border border-white bg-white/60 px-4 py-3 font-medium text-gray-700 shadow-sm outline-none transition-all focus:border-transparent focus:ring-2 focus:ring-orange-400"
            placeholder="例如：集合、多线程、JVM"
            :disabled="loading || saving"
          />
        </div>

        <div class="rounded-3xl border border-white bg-white/50 p-6 shadow-sm">
          <div class="mb-4 flex items-center justify-between">
            <label class="block text-sm font-extrabold text-gray-700">选项设置</label>
            <button
              type="button"
              class="flex items-center gap-1 rounded-xl border border-orange-100 bg-orange-50 px-3 py-1.5 text-sm font-bold text-orange-500 shadow-sm transition-colors hover:text-orange-600"
              :class="canAddOption ? 'hover:bg-orange-100' : 'cursor-not-allowed opacity-50'"
              @click="addOption"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              添加选项
            </button>
          </div>

          <div class="space-y-3">
            <div
              v-for="item in options"
              :key="item.id"
              class="group/opt relative flex items-center gap-3 overflow-hidden rounded-2xl border p-2 pr-4 shadow-sm transition-all"
              :class="getOptionWrapClass(item)"
            >
              <div v-if="item.correct" class="absolute bottom-0 left-0 top-0 w-1 bg-emerald-400" />
              <div class="flex items-center justify-center pl-3">
                <input
                  :type="typeValue === '2' ? 'checkbox' : 'radio'"
                  :name="typeValue === '2' ? `correct-${item.id}` : 'correct'"
                  class="h-4 w-4 text-emerald-500 focus:ring-emerald-400"
                  :checked="item.correct"
                  @change="setCorrect(item.id)"
                />
              </div>
              <span class="w-6 text-center font-extrabold" :class="getOptionKeyClass(item)">{{ item.key }}</span>
              <input
                v-model="item.text"
                type="text"
                class="flex-1 border-none bg-transparent px-2 py-2 text-sm font-medium text-gray-800 outline-none"
                :placeholder="typeValue === '3' ? '' : '输入选项内容...'"
                :readonly="false"
              />
              <button
                type="button"
                class="rounded-xl p-1.5 text-gray-400 transition-all hover:bg-rose-50 hover:text-rose-500"
                :class="options.length <= 2 || typeValue === '3' ? 'cursor-not-allowed opacity-30' : 'opacity-0 group-hover/opt:opacity-100'"
                @click="removeOption(item.id)"
              >
                <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          </div>
        </div>

        <div class="group">
          <label class="mb-2 block text-sm font-extrabold text-gray-700 transition-colors group-focus-within:text-orange-500">答案解析</label>
          <textarea
            v-model="analysis"
            rows="5"
            class="w-full resize-y rounded-2xl border border-white bg-white/60 px-4 py-3.5 font-medium leading-relaxed text-gray-700 shadow-sm outline-none transition-all focus:border-transparent focus:ring-2 focus:ring-orange-400"
            placeholder="详细解释为什么选该选项..."
            :disabled="loading || saving"
          />
        </div>

        <div class="flex justify-end gap-4 border-t border-gray-100/50 pt-6">
          <RouterLink
            to="/admin/questions"
            class="rounded-2xl border border-white bg-white px-6 py-3 text-sm font-bold text-gray-600 shadow-sm transition-colors hover:bg-gray-50"
          >
            取消
          </RouterLink>
          <button
            type="submit"
            :disabled="loading || saving"
            class="flex items-center gap-2 rounded-2xl bg-gradient-to-r from-orange-400 to-rose-400 px-8 py-3 text-sm font-bold text-white shadow-[0_8px_20px_rgba(251,146,60,0.3)] transition-all hover:-translate-y-0.5 hover:from-orange-500 hover:to-rose-500 active:scale-95"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            {{ saving ? '保存中...' : isCreate ? '保存题目' : '更新题目' }}
          </button>
        </div>
      </form>
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
