<script setup lang="ts">
import { computed, ref } from 'vue'
import { importAdminQuestions, type AdminImportQuestionsResponse } from '@/api/modules/admin'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'

const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const isDragging = ref(false)
const importing = ref(false)
const importResult = ref<AdminImportQuestionsResponse | null>(null)

const canImport = computed(() => Boolean(selectedFile.value) && !importing.value)
const fileDesc = computed(() => selectedFile.value?.name || '支持 .md 文件（UTF-8）')
const failedItems = computed(() => importResult.value?.failedItems || [])
const templateDownloadUrl = '/templates/question-import-template.md'

function pickFile() {
  if (importing.value) {
    return
  }
  fileInput.value?.click()
}

function setFile(file: File | null) {
  if (!file) {
    return
  }
  if (!/\.md$/i.test(file.name)) {
    showErrorToast('仅支持上传 .md 文件', '文件格式错误', 'top-right')
    return
  }
  selectedFile.value = file
  importResult.value = null
}

function onInputChange(event: Event) {
  const target = event.target as HTMLInputElement
  setFile(target.files?.[0] || null)
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  isDragging.value = true
}

function onDragLeave(event: DragEvent) {
  event.preventDefault()
  isDragging.value = false
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  isDragging.value = false
  setFile(event.dataTransfer?.files?.[0] || null)
}

function clearFile() {
  if (importing.value) {
    return
  }
  selectedFile.value = null
  importResult.value = null
}

async function startImport() {
  if (!selectedFile.value || importing.value) {
    return
  }
  importing.value = true
  try {
    const data = await importAdminQuestions(selectedFile.value)
    importResult.value = data
    showActionSuccessToast(`导入完成：成功 ${data.successCount}，失败 ${data.failedCount}`, 'top-right')
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '导入失败，请稍后重试', '导入失败', 'top-right')
  } finally {
    importing.value = false
  }
}

async function downloadTemplate() {
  try {
    const response = await fetch(templateDownloadUrl, { cache: 'no-store' })
    if (!response.ok) {
      throw new Error('模板文件下载失败')
    }
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = '题目导入模板（Markdown）.md'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(downloadUrl)
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '下载失败，请稍后重试', '下载失败', 'top-right')
  }
}
</script>

<template>
  <section class="flex min-h-full items-center justify-center p-8">
    <div
      class="fade-in relative w-full max-w-2xl overflow-hidden rounded-3xl border border-white bg-white/80 p-8 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md sm:p-10"
    >
      <div class="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-orange-50 opacity-50 blur-3xl" />

      <!-- AI 生成引导 -->
      <div class="relative z-10 mb-6 rounded-2xl border border-purple-100 bg-purple-50/50 p-5">
        <div class="flex items-center justify-between">
          <div>
            <h3 class="text-sm font-extrabold text-purple-600">AI 智能生成</h3>
            <p class="mt-1 text-xs font-medium text-purple-500/80">
              使用 AI 自动生成面试题目，批量生成后可进入草稿审核
            </p>
          </div>
          <RouterLink
            to="/admin/questions/generate"
            class="rounded-xl bg-purple-500 px-4 py-2 text-sm font-bold text-white shadow-sm transition-all hover:bg-purple-600"
          >
            前往 AI 生成
          </RouterLink>
        </div>
      </div>

      <div class="relative z-10 mb-8 text-center">
        <div
          class="mb-4 inline-flex h-16 w-16 items-center justify-center rounded-2xl border border-rose-100 bg-rose-50 text-rose-500 shadow-sm"
        >
          <svg class="h-8 w-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
          </svg>
        </div>
        <h2 class="mb-2 text-2xl font-extrabold text-gray-800">上传 Markdown 文件</h2>
        <p class="font-medium text-gray-500">请下载模板文件，按模板填写题目后直接导入。</p>
      </div>

      <input ref="fileInput" type="file" class="hidden" accept=".md,text/markdown" @change="onInputChange" />

      <button
        type="button"
        class="group relative z-10 mb-8 w-full rounded-3xl border-2 border-dashed p-12 text-center shadow-sm transition-all hover:shadow-md"
        :class="
          isDragging || selectedFile
            ? 'border-rose-400 bg-rose-50/60'
            : 'border-rose-200 bg-white/50 hover:border-rose-400 hover:bg-rose-50/50'
        "
        @click="pickFile"
        @dragover="onDragOver"
        @dragleave="onDragLeave"
        @drop="onDrop"
      >
        <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full border border-rose-50 bg-white shadow-sm transition-transform group-hover:scale-110">
          <svg class="h-8 w-8 text-rose-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
          </svg>
        </div>
        <p class="mb-1 text-base font-extrabold text-gray-700">
          {{ selectedFile ? '已选择导入文件' : '点击或拖拽文件到此处上传' }}
        </p>
        <p class="text-sm font-medium text-gray-400">{{ fileDesc }}</p>
      </button>

      <div class="relative z-10 flex flex-col items-center justify-between gap-4 border-t border-gray-100/50 pt-6 sm:flex-row">
        <button
          type="button"
          class="flex items-center gap-1.5 rounded-xl border border-orange-100 bg-orange-50 px-4 py-2 text-sm font-bold text-orange-500 shadow-sm transition-colors hover:bg-orange-100 hover:text-orange-600"
          @click="downloadTemplate"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          下载导入模板
        </button>

        <div class="flex w-full space-x-3 sm:w-auto">
          <button
            type="button"
            class="flex-1 rounded-xl border border-white bg-white px-6 py-2.5 text-sm font-bold text-gray-600 shadow-sm transition-colors hover:bg-gray-50 sm:flex-none"
            :disabled="importing"
            @click="clearFile"
          >
            取消
          </button>
          <button
            type="button"
            class="flex-1 rounded-xl bg-gradient-to-r from-rose-400 to-orange-400 px-6 py-2.5 text-sm font-bold text-white shadow-[0_4px_10px_rgba(244,63,94,0.3)] transition-all hover:from-rose-500 hover:to-orange-500 disabled:cursor-not-allowed disabled:opacity-50 sm:flex-none"
            :disabled="!canImport"
            @click="startImport"
          >
            {{ importing ? '导入中...' : '开始导入' }}
          </button>
        </div>
      </div>

      <div
        v-if="importResult"
        class="relative z-10 mt-6 rounded-2xl border border-gray-100 bg-white/70 p-4 shadow-sm"
      >
        <div class="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
          <div class="rounded-xl bg-gray-50 px-3 py-2">
            <p class="text-xs font-bold text-gray-400">总题数</p>
            <p class="mt-1 text-lg font-extrabold text-gray-700">{{ importResult.total }}</p>
          </div>
          <div class="rounded-xl bg-emerald-50 px-3 py-2">
            <p class="text-xs font-bold text-emerald-500">成功</p>
            <p class="mt-1 text-lg font-extrabold text-emerald-600">{{ importResult.successCount }}</p>
          </div>
          <div class="rounded-xl bg-rose-50 px-3 py-2">
            <p class="text-xs font-bold text-rose-500">失败</p>
            <p class="mt-1 text-lg font-extrabold text-rose-600">{{ importResult.failedCount }}</p>
          </div>
          <div class="rounded-xl bg-orange-50 px-3 py-2">
            <p class="text-xs font-bold text-orange-500">成功率</p>
            <p class="mt-1 text-lg font-extrabold text-orange-600">
              {{ importResult.total ? `${Math.round((importResult.successCount / importResult.total) * 100)}%` : '0%' }}
            </p>
          </div>
        </div>

        <div v-if="failedItems.length" class="mt-4 overflow-hidden rounded-xl border border-rose-100">
          <div class="bg-rose-50 px-3 py-2 text-xs font-extrabold text-rose-500">失败明细</div>
          <div class="max-h-56 overflow-auto bg-white">
            <div
              v-for="item in failedItems"
              :key="`${item.index}-${item.stem}`"
              class="border-t border-rose-50 px-3 py-2 text-xs text-gray-600 first:border-t-0"
            >
              <p class="font-bold text-gray-700">#{{ item.index }} · {{ item.stem || '（题干为空）' }}</p>
              <p class="mt-1 text-rose-500">{{ item.reason }}</p>
            </div>
          </div>
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
