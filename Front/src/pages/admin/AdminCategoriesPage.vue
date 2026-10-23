<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  listAdminDirections,
  createDirection,
  updateDirection,
  updateDirectionStatus,
  deleteDirection,
  listAdminCategories,
  createCategory,
  updateCategory,
  updateCategoryStatus,
  deleteCategory,
  listAdminModules,
  createAdminModule,
  updateAdminModule,
  deleteAdminModule,
  type AdminDirection,
  type AdminCategory,
  type AdminModule,
} from '@/api/modules/admin'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'
import ConfirmModal from '@/components/common/ConfirmModal.vue'

// ── Confirm modal ──
interface ConfirmTarget {
  type: 'dir' | 'cat' | 'mod'
  item: AdminDirection | AdminCategory | AdminModule
}
const confirm = reactive({
  open: false,
  title: '',
  message: '',
  loading: false,
  target: null as ConfirmTarget | null,
})

function askDelete(type: ConfirmTarget['type'], item: ConfirmTarget['item']) {
  confirm.target = { type, item }
  confirm.loading = false
  if (type === 'dir') {
    confirm.title = '删除方向'
    confirm.message = `确定删除方向「${(item as AdminDirection).name}」吗？该方向下所有分类、模块及题目将被永久清除，无法恢复。`
  } else if (type === 'cat') {
    confirm.title = '删除分类'
    confirm.message = `确定删除分类「${(item as AdminCategory).name}」吗？该分类下所有模块及题目将被永久清除，无法恢复。`
  } else {
    confirm.title = '删除模块'
    confirm.message = `确定删除模块「${(item as AdminModule).name}」吗？该模块下所有题目也将被同步删除，无法恢复。`
  }
  confirm.open = true
}

function closeConfirm() {
  if (confirm.loading) return
  confirm.open = false
  confirm.target = null
}

async function executeDelete() {
  if (!confirm.target) return
  confirm.loading = true
  const { type, item } = confirm.target
  try {
    if (type === 'dir') {
      const d = item as AdminDirection
      await deleteDirection(d.id)
      if (selectedDirectionId.value === d.id) {
        selectedDirectionId.value = null
        selectedCategoryId.value = null
        modules.value = []
      }
      await loadDirections()
    } else if (type === 'cat') {
      const c = item as AdminCategory
      await deleteCategory(c.id)
      if (selectedCategoryId.value === c.id) {
        selectedCategoryId.value = null
        modules.value = []
      }
      await loadCategories()
    } else {
      await deleteAdminModule((item as AdminModule).id)
      await loadModules()
    }
    showActionSuccessToast(`${labels[type]}已删除`, 'top-right')
    confirm.open = false
    confirm.target = null
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '删除失败', '操作失败', 'top-right')
  } finally {
    confirm.loading = false
  }
}

const labels: Record<string, string> = { dir: '方向', cat: '分类', mod: '模块' }

// ── Data ──
const directions = ref<AdminDirection[]>([])
const categories = ref<AdminCategory[]>([])
const modules = ref<AdminModule[]>([])
const loading = reactive({ dirs: false, cats: false, mods: false })

const selectedDirectionId = ref<number | null>(null)
const selectedCategoryId = ref<number | null>(null)

// ── Derived ──
const filteredCategories = computed(() =>
  categories.value.filter((c) => c.directionId === selectedDirectionId.value),
)

const selectedDirection = computed(() =>
  directions.value.find((d) => d.id === selectedDirectionId.value),
)
const selectedCategory = computed(() =>
  categories.value.find((c) => c.id === selectedCategoryId.value),
)

// ── Direction form ──
const dirModalOpen = ref(false)
const dirEditing = ref<AdminDirection | null>(null)
const dirForm = reactive({ code: '', name: '', sort: 0, status: 0 })
const dirSubmitting = ref(false)

function openCreateDir() {
  dirEditing.value = null
  dirForm.code = ''
  dirForm.name = ''
  dirForm.sort = 0
  dirForm.status = 0
  dirModalOpen.value = true
}
function openEditDir(dir: AdminDirection) {
  dirEditing.value = dir
  dirForm.code = dir.code
  dirForm.name = dir.name
  dirForm.sort = dir.sort
  dirForm.status = dir.status
  dirModalOpen.value = true
}
function closeDirModal() {
  dirModalOpen.value = false
  dirEditing.value = null
}
async function submitDir() {
  if (!dirForm.code.trim() || !dirForm.name.trim()) {
    showErrorToast('方向代码和名称不能为空', '参数错误', 'top-right')
    return
  }
  dirSubmitting.value = true
  try {
    const body = { code: dirForm.code.trim(), name: dirForm.name.trim(), sort: dirForm.sort, status: dirForm.status }
    if (dirEditing.value) {
      await updateDirection(dirEditing.value.id, body)
      showActionSuccessToast('方向已更新', 'top-right')
    } else {
      await createDirection(body)
      showActionSuccessToast('方向已创建', 'top-right')
    }
    closeDirModal()
    await loadDirections()
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '操作失败', '操作失败', 'top-right')
  } finally {
    dirSubmitting.value = false
  }
}
async function toggleDirStatus(dir: AdminDirection) {
  const next = dir.status === 0 ? 1 : 0
  try {
    await updateDirectionStatus(dir.id, next)
    dir.status = next
    showActionSuccessToast(next === 0 ? '已启用' : '已禁用', 'top-right')
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '状态更新失败', '操作失败', 'top-right')
  }
}

// ── Category form ──
const catModalOpen = ref(false)
const catEditing = ref<AdminCategory | null>(null)
const catForm = reactive({ directionId: 0, code: '', name: '', sort: 0, status: 0 })
const catSubmitting = ref(false)

function openCreateCat() {
  if (!selectedDirectionId.value) {
    showErrorToast('请先选择一个方向', '提示', 'top-right')
    return
  }
  catEditing.value = null
  catForm.directionId = selectedDirectionId.value
  catForm.code = ''
  catForm.name = ''
  catForm.sort = 0
  catForm.status = 0
  catModalOpen.value = true
}
function openEditCat(cat: AdminCategory) {
  catEditing.value = cat
  catForm.directionId = cat.directionId
  catForm.code = cat.code
  catForm.name = cat.name
  catForm.sort = cat.sort
  catForm.status = cat.status
  catModalOpen.value = true
}
function closeCatModal() {
  catModalOpen.value = false
  catEditing.value = null
}
async function submitCat() {
  if (!catForm.code.trim() || !catForm.name.trim()) {
    showErrorToast('分类代码和名称不能为空', '参数错误', 'top-right')
    return
  }
  catSubmitting.value = true
  try {
    const body = { directionId: catForm.directionId, code: catForm.code.trim(), name: catForm.name.trim(), sort: catForm.sort, status: catForm.status }
    if (catEditing.value) {
      await updateCategory(catEditing.value.id, body)
      showActionSuccessToast('分类已更新', 'top-right')
    } else {
      await createCategory(body)
      showActionSuccessToast('分类已创建', 'top-right')
    }
    closeCatModal()
    await loadCategories()
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '操作失败', '操作失败', 'top-right')
  } finally {
    catSubmitting.value = false
  }
}
async function toggleCatStatus(cat: AdminCategory) {
  const next = cat.status === 0 ? 1 : 0
  try {
    await updateCategoryStatus(cat.id, next)
    cat.status = next
    showActionSuccessToast(next === 0 ? '已启用' : '已禁用', 'top-right')
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '状态更新失败', '操作失败', 'top-right')
  }
}

// ── Module form ──
const modModalOpen = ref(false)
const modEditing = ref<AdminModule | null>(null)
const modForm = reactive({ name: '', description: '', sort: 0, status: 0 })
const modSubmitting = ref(false)

function openCreateMod() {
  if (!selectedCategoryId.value) {
    showErrorToast('请先选择一个分类', '提示', 'top-right')
    return
  }
  modEditing.value = null
  modForm.name = ''
  modForm.description = ''
  modForm.sort = 0
  modForm.status = 0
  modModalOpen.value = true
}
function openEditMod(mod: AdminModule) {
  modEditing.value = mod
  modForm.name = mod.name
  modForm.description = mod.description || ''
  modForm.sort = mod.sort
  modForm.status = mod.status
  modModalOpen.value = true
}
function closeModModal() {
  modModalOpen.value = false
  modEditing.value = null
}
async function submitMod() {
  if (!modForm.name.trim()) {
    showErrorToast('模块名称不能为空', '参数错误', 'top-right')
    return
  }
  if (!selectedCategoryId.value) return
  modSubmitting.value = true
  try {
    const body = {
      categoryId: selectedCategoryId.value,
      name: modForm.name.trim(),
      description: modForm.description.trim() || undefined,
      sort: modForm.sort,
      status: modForm.status as 0 | 1,
    }
    if (modEditing.value) {
      await updateAdminModule(modEditing.value.id, body)
      showActionSuccessToast('模块已更新', 'top-right')
    } else {
      await createAdminModule(body)
      showActionSuccessToast('模块已创建', 'top-right')
    }
    closeModModal()
    await loadModules()
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '操作失败', '操作失败', 'top-right')
  } finally {
    modSubmitting.value = false
  }
}
async function toggleModStatus(mod: AdminModule) {
  const next = mod.status === 0 ? 1 : 0
  try {
    // Use createAdminModule/updateAdminModule to toggle? No — we need a dedicated endpoint.
    // For now, re-save the module with the toggled status.
    await updateAdminModule(mod.id, {
      categoryId: mod.categoryId,
      name: mod.name,
      description: mod.description || undefined,
      sort: mod.sort,
      status: next as 0 | 1,
    })
    mod.status = next
    showActionSuccessToast(next === 0 ? '已启用' : '已禁用', 'top-right')
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '状态更新失败', '操作失败', 'top-right')
  }
}

// ── Data loading ──
async function loadDirections() {
  loading.dirs = true
  try {
    directions.value = await listAdminDirections()
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '加载方向失败', '加载失败', 'top-right')
  } finally {
    loading.dirs = false
  }
}

async function loadCategories() {
  loading.cats = true
  try {
    categories.value = await listAdminCategories()
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '加载分类失败', '加载失败', 'top-right')
  } finally {
    loading.cats = false
  }
}

async function loadModules() {
  if (!selectedCategoryId.value) {
    modules.value = []
    return
  }
  loading.mods = true
  try {
    const data = await listAdminModules({ categoryId: selectedCategoryId.value })
    modules.value = data.list
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '加载模块失败', '加载失败', 'top-right')
  } finally {
    loading.mods = false
  }
}

function selectDirection(dirId: number) {
  if (selectedDirectionId.value === dirId) return
  selectedDirectionId.value = dirId
  selectedCategoryId.value = null
  modules.value = []
}

function selectCategory(catId: number) {
  if (selectedCategoryId.value === catId) return
  selectedCategoryId.value = catId
  loadModules()
}

onMounted(async () => {
  await Promise.all([loadDirections(), loadCategories()])
  if (directions.value.length > 0) {
    selectDirection(directions.value[0].id)
  }
})
</script>

<template>
  <div class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
    <!-- Breadcrumb -->
    <nav v-if="selectedDirection" class="mb-6 flex items-center gap-2 text-sm">
      <span class="text-gray-400">层级结构</span>
      <svg class="h-3.5 w-3.5 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
      </svg>
      <span
        class="font-bold cursor-pointer transition-colors"
        :class="selectedDirection.status === 0 ? 'text-indigo-600' : 'text-gray-400'"
        @click="selectDirection(selectedDirection.id); selectedCategoryId = null; modules = []"
      >{{ selectedDirection.name }}</span>
      <template v-if="selectedCategory">
        <svg class="h-3.5 w-3.5 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
        </svg>
        <span
          class="font-bold cursor-pointer transition-colors"
          :class="selectedCategory.status === 0 ? 'text-indigo-600' : 'text-gray-400'"
          @click="selectCategory(selectedCategory.id)"
        >{{ selectedCategory.name }}</span>
      </template>
    </nav>

    <!-- Section 1: Directions -->
    <section class="mb-8">
      <div class="mb-4 flex items-center justify-between">
        <div>
          <h2 class="text-lg font-extrabold text-gray-900">方向管理</h2>
          <p class="mt-0.5 text-sm text-gray-400">顶层分类维度，如"技术研发"、"产品设计"</p>
        </div>
        <button
          class="inline-flex items-center gap-1.5 rounded-xl bg-indigo-500 px-4 py-2 text-sm font-bold text-white shadow-sm transition-all hover:bg-indigo-600 active:scale-95"
          @click="openCreateDir"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          新建方向
        </button>
      </div>

      <div v-if="loading.dirs && directions.length === 0" class="flex items-center justify-center py-8 text-sm text-gray-400">
        <svg class="mr-2 h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        加载中...
      </div>
      <div v-else-if="directions.length === 0" class="rounded-2xl border border-dashed border-gray-200 bg-gray-50/50 py-12 text-center">
        <svg class="mx-auto mb-3 h-8 w-8 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
        </svg>
        <p class="text-sm text-gray-400">暂无方向，点击右上角按钮创建第一个方向</p>
      </div>
      <div v-else class="flex flex-wrap gap-3">
        <button
          v-for="dir in directions"
          :key="dir.id"
          class="group relative inline-flex items-center gap-2.5 rounded-xl border px-4 py-3 text-left transition-all cursor-pointer"
          :class="selectedDirectionId === dir.id
            ? 'border-indigo-300 bg-indigo-50 shadow-sm ring-1 ring-indigo-200'
            : 'border-gray-200 bg-white shadow-sm hover:border-indigo-200 hover:shadow-md active:bg-gray-50'"
          @click="selectDirection(dir.id)"
        >
          <span
            class="inline-flex items-center rounded-lg px-2 py-0.5 text-xs font-mono font-bold shrink-0"
            :class="dir.status === 0 ? 'bg-indigo-100 text-indigo-600' : 'bg-gray-100 text-gray-400'"
          >{{ dir.code }}</span>
          <span
            class="text-sm font-bold truncate max-w-[160px]"
            :class="dir.status === 0 ? 'text-gray-800' : 'text-gray-400 line-through'"
          >{{ dir.name }}</span>
          <span class="ml-auto hidden group-hover:inline-flex sm:hidden items-center gap-0.5">
            <span class="rounded-lg p-1 text-gray-400 hover:bg-indigo-50 hover:text-indigo-500" @click.stop="openEditDir(dir)">
              <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
            </span>
            <span class="rounded-lg p-1 text-gray-400 hover:text-rose-500" @click.stop="toggleDirStatus(dir)">
              <svg v-if="dir.status === 0" class="h-3.5 w-3.5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
              </svg>
              <svg v-else class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 5.636a9 9 0 010 12.728M5.636 18.364a9 9 0 010-12.728" />
              </svg>
            </span>
            <span class="rounded-lg p-1 text-gray-400 hover:text-rose-500" @click.stop="askDelete('dir', dir)">
              <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            </span>
          </span>
        </button>
      </div>
    </section>

    <!-- Section 2: Categories -->
    <section class="mb-8">
      <div class="mb-4 flex items-center justify-between">
        <div>
          <h2 class="text-lg font-extrabold text-gray-900">
            分类管理
            <span v-if="selectedDirection" class="font-medium text-gray-400"> — {{ selectedDirection.name }}</span>
          </h2>
          <p class="mt-0.5 text-sm text-gray-400">每个方向下可包含多个分类</p>
        </div>
        <button
          class="inline-flex items-center gap-1.5 rounded-xl bg-indigo-500 px-4 py-2 text-sm font-bold text-white shadow-sm transition-all hover:bg-indigo-600 active:scale-95 disabled:opacity-40 disabled:cursor-not-allowed"
          :disabled="!selectedDirectionId"
          @click="openCreateCat"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          新建分类
        </button>
      </div>

      <div v-if="!selectedDirectionId" class="rounded-2xl border border-dashed border-gray-200 bg-gray-50/50 py-12 text-center">
        <svg class="mx-auto mb-3 h-8 w-8 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
        </svg>
        <p class="text-sm text-gray-400">请先在上方选择一个方向</p>
      </div>
      <div v-else-if="filteredCategories.length === 0" class="rounded-2xl border border-dashed border-gray-200 bg-gray-50/50 py-12 text-center">
        <svg class="mx-auto mb-3 h-8 w-8 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
        </svg>
        <p class="text-sm text-gray-400">该方向下暂无分类，点击右上角按钮创建</p>
      </div>
      <div v-else class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <button
          v-for="cat in filteredCategories"
          :key="cat.id"
          class="group relative flex flex-col rounded-2xl border p-4 text-left transition-all cursor-pointer"
          :class="selectedCategoryId === cat.id
            ? 'border-indigo-300 bg-indigo-50 shadow-sm ring-1 ring-indigo-200'
            : 'border-gray-200 bg-white shadow-sm hover:border-indigo-200 hover:shadow-md active:bg-gray-50'"
          @click="selectCategory(cat.id)"
        >
          <div class="flex-1">
            <div class="mb-2 flex items-center gap-2">
              <span
                class="inline-flex items-center rounded-lg px-2 py-0.5 text-xs font-mono font-bold"
                :class="cat.status === 0 ? 'bg-indigo-100 text-indigo-600' : 'bg-gray-100 text-gray-400'"
              >{{ cat.code }}</span>
              <span
                class="text-sm font-bold"
                :class="cat.status === 0 ? 'text-gray-800' : 'text-gray-400 line-through'"
              >{{ cat.name }}</span>
            </div>
            <div class="text-xs text-gray-400">排序 {{ cat.sort }}</div>
          </div>

          <div class="mt-3 flex items-center gap-1 border-t border-gray-100 pt-3 opacity-0 transition-opacity group-hover:opacity-100 sm:opacity-0">
            <span class="flex-1 rounded-lg py-1.5 text-center text-xs font-bold text-gray-500 hover:bg-indigo-50 hover:text-indigo-600 transition-colors cursor-pointer" @click.stop="openEditCat(cat)">编辑</span>
            <span
              class="flex-1 rounded-lg py-1.5 text-center text-xs font-bold transition-colors cursor-pointer"
              :class="cat.status === 0 ? 'text-emerald-600 hover:bg-emerald-50' : 'text-gray-400 hover:bg-indigo-50 hover:text-indigo-600'"
              @click.stop="toggleCatStatus(cat)"
            >{{ cat.status === 0 ? '禁用' : '启用' }}</span>
            <span class="flex-1 rounded-lg py-1.5 text-center text-xs font-bold text-gray-400 hover:bg-rose-50 hover:text-rose-500 transition-colors cursor-pointer" @click.stop="askDelete('cat', cat)">删除</span>
          </div>
        </button>
      </div>
    </section>

    <!-- Section 3: Modules -->
    <section>
      <div class="mb-4 flex items-center justify-between">
        <div>
          <h2 class="text-lg font-extrabold text-gray-900">
            模块管理
            <span v-if="selectedCategory" class="font-medium text-gray-400"> — {{ selectedCategory.name }}</span>
          </h2>
          <p class="mt-0.5 text-sm text-gray-400">每个分类下可包含多个练习模块</p>
        </div>
        <button
          class="inline-flex items-center gap-1.5 rounded-xl bg-indigo-500 px-4 py-2 text-sm font-bold text-white shadow-sm transition-all hover:bg-indigo-600 active:scale-95 disabled:opacity-40 disabled:cursor-not-allowed"
          :disabled="!selectedCategoryId"
          @click="openCreateMod"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          新建模块
        </button>
      </div>

      <div v-if="!selectedCategoryId" class="rounded-2xl border border-dashed border-gray-200 bg-gray-50/50 py-12 text-center">
        <svg class="mx-auto mb-3 h-8 w-8 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
        </svg>
        <p class="text-sm text-gray-400">请先在上方选择一个分类</p>
      </div>
      <div v-else-if="loading.mods" class="flex items-center justify-center py-8 text-sm text-gray-400">
        <svg class="mr-2 h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        加载中...
      </div>
      <div v-else-if="modules.length === 0" class="rounded-2xl border border-dashed border-gray-200 bg-gray-50/50 py-12 text-center">
        <svg class="mx-auto mb-3 h-8 w-8 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        <p class="text-sm text-gray-400">该分类下暂无模块，点击右上角按钮创建</p>
      </div>
      <div v-else class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <div
          v-for="mod in modules"
          :key="mod.id"
          class="group relative flex flex-col rounded-2xl border p-4 transition-all cursor-pointer"
          :class="mod.status === 0
            ? 'border-gray-200 bg-white shadow-sm hover:border-indigo-200 hover:shadow-md'
            : 'border-gray-100 bg-gray-50/60 opacity-60'"
        >
          <div class="flex-1">
            <div class="mb-2 flex items-start justify-between">
              <h3
                class="text-sm font-bold truncate"
                :class="mod.status === 0 ? 'text-gray-800' : 'text-gray-400 line-through'"
              >{{ mod.name }}</h3>
            </div>
            <p v-if="mod.description" class="mb-3 text-xs leading-relaxed text-gray-400 line-clamp-2">{{ mod.description }}</p>
            <div class="flex items-center gap-2 text-xs text-gray-400">
              <span class="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-bold"
                :class="mod.status === 0 ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-400'">
                {{ mod.status === 0 ? '启用' : '禁用' }}
              </span>
              <span>{{ mod.questionCount }} 题</span>
              <span>排序 {{ mod.sort }}</span>
            </div>
          </div>

          <div class="mt-3 flex items-center gap-1 border-t border-gray-100 pt-3 opacity-0 transition-opacity group-hover:opacity-100 sm:opacity-0">
            <span class="flex-1 rounded-lg py-1.5 text-center text-xs font-bold text-gray-500 hover:bg-indigo-50 hover:text-indigo-600 transition-colors cursor-pointer" @click.stop="openEditMod(mod)">编辑</span>
            <span
              class="flex-1 rounded-lg py-1.5 text-center text-xs font-bold transition-colors cursor-pointer"
              :class="mod.status === 0 ? 'text-amber-600 hover:bg-amber-50' : 'text-emerald-600 hover:bg-emerald-50'"
              @click.stop="toggleModStatus(mod)"
            >{{ mod.status === 0 ? '禁用' : '启用' }}</span>
            <span class="flex-1 rounded-lg py-1.5 text-center text-xs font-bold text-gray-400 hover:bg-rose-50 hover:text-rose-500 transition-colors cursor-pointer" @click.stop="askDelete('mod', mod)">删除</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Direction Modal -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="dirModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm px-4" @click.self="closeDirModal">
          <div class="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <h3 class="text-lg font-bold text-gray-900 mb-5">{{ dirEditing ? '编辑方向' : '新建方向' }}</h3>
            <div class="space-y-4">
              <div>
                <label class="block text-sm font-bold text-gray-700 mb-1.5">方向代码</label>
                <input
                  v-model="dirForm.code"
                  type="text"
                  class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm placeholder:text-gray-300 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                  placeholder="如 tech, product"
                />
              </div>
              <div>
                <label class="block text-sm font-bold text-gray-700 mb-1.5">方向名称</label>
                <input
                  v-model="dirForm.name"
                  type="text"
                  class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm placeholder:text-gray-300 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                  placeholder="如 技术研发"
                />
              </div>
              <div class="flex gap-4">
                <div class="flex-1">
                  <label class="block text-sm font-bold text-gray-700 mb-1.5">排序</label>
                  <input v-model.number="dirForm.sort" type="number" class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200" />
                </div>
                <div class="flex-1">
                  <label class="block text-sm font-bold text-gray-700 mb-1.5">状态</label>
                  <select v-model.number="dirForm.status" class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200">
                    <option :value="0">启用</option>
                    <option :value="1">禁用</option>
                  </select>
                </div>
              </div>
            </div>
            <div class="flex justify-end gap-3 mt-6">
              <button class="rounded-xl px-4 py-2.5 text-sm font-bold text-gray-500 hover:bg-gray-100 active:bg-gray-200 transition-colors" @click="closeDirModal">取消</button>
              <button
                class="rounded-xl bg-indigo-500 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-600 active:bg-indigo-700 transition-colors disabled:opacity-50"
                :disabled="dirSubmitting"
                @click="submitDir"
              >{{ dirSubmitting ? '提交中...' : '保存' }}</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Category Modal -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="catModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm px-4" @click.self="closeCatModal">
          <div class="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <h3 class="text-lg font-bold text-gray-900 mb-5">{{ catEditing ? '编辑分类' : '新建分类' }}</h3>
            <div class="space-y-4">
              <div>
                <label class="block text-sm font-bold text-gray-700 mb-1.5">所属方向</label>
                <select v-model.number="catForm.directionId" class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200">
                  <option v-for="d in directions" :key="d.id" :value="d.id">{{ d.name }}</option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-bold text-gray-700 mb-1.5">分类代码</label>
                <input
                  v-model="catForm.code"
                  type="text"
                  class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm placeholder:text-gray-300 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                  placeholder="如 backend, frontend"
                />
              </div>
              <div>
                <label class="block text-sm font-bold text-gray-700 mb-1.5">分类名称</label>
                <input
                  v-model="catForm.name"
                  type="text"
                  class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm placeholder:text-gray-300 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                  placeholder="如 后端"
                />
              </div>
              <div class="flex gap-4">
                <div class="flex-1">
                  <label class="block text-sm font-bold text-gray-700 mb-1.5">排序</label>
                  <input v-model.number="catForm.sort" type="number" class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200" />
                </div>
                <div class="flex-1">
                  <label class="block text-sm font-bold text-gray-700 mb-1.5">状态</label>
                  <select v-model.number="catForm.status" class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200">
                    <option :value="0">启用</option>
                    <option :value="1">禁用</option>
                  </select>
                </div>
              </div>
            </div>
            <div class="flex justify-end gap-3 mt-6">
              <button class="rounded-xl px-4 py-2.5 text-sm font-bold text-gray-500 hover:bg-gray-100 active:bg-gray-200 transition-colors" @click="closeCatModal">取消</button>
              <button
                class="rounded-xl bg-indigo-500 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-600 active:bg-indigo-700 transition-colors disabled:opacity-50"
                :disabled="catSubmitting"
                @click="submitCat"
              >{{ catSubmitting ? '提交中...' : '保存' }}</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Module Modal -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="modModalOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm px-4" @click.self="closeModModal">
          <div class="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <h3 class="text-lg font-bold text-gray-900 mb-5">{{ modEditing ? '编辑模块' : '新建模块' }}</h3>
            <div class="space-y-4">
              <div>
                <label class="block text-sm font-bold text-gray-700 mb-1.5">模块名称</label>
                <input
                  v-model="modForm.name"
                  type="text"
                  class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm placeholder:text-gray-300 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                  placeholder="如 Java 核心基础"
                />
              </div>
              <div>
                <label class="block text-sm font-bold text-gray-700 mb-1.5">描述</label>
                <textarea
                  v-model="modForm.description"
                  rows="2"
                  class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm placeholder:text-gray-300 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                  placeholder="模块简介（选填）"
                />
              </div>
              <div class="flex gap-4">
                <div class="flex-1">
                  <label class="block text-sm font-bold text-gray-700 mb-1.5">排序</label>
                  <input v-model.number="modForm.sort" type="number" class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200" />
                </div>
                <div class="flex-1">
                  <label class="block text-sm font-bold text-gray-700 mb-1.5">状态</label>
                  <select v-model.number="modForm.status" class="w-full rounded-xl border border-gray-200 px-3.5 py-2.5 text-sm focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200">
                    <option :value="0">启用</option>
                    <option :value="1">禁用</option>
                  </select>
                </div>
              </div>
            </div>
            <div class="flex justify-end gap-3 mt-6">
              <button class="rounded-xl px-4 py-2.5 text-sm font-bold text-gray-500 hover:bg-gray-100 active:bg-gray-200 transition-colors" @click="closeModModal">取消</button>
              <button
                class="rounded-xl bg-indigo-500 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-600 active:bg-indigo-700 transition-colors disabled:opacity-50"
                :disabled="modSubmitting"
                @click="submitMod"
              >{{ modSubmitting ? '提交中...' : '保存' }}</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Confirm Delete Modal -->
    <ConfirmModal
      :open="confirm.open"
      :title="confirm.title"
      :message="confirm.message"
      :loading="confirm.loading"
      @confirm="executeDelete"
      @cancel="closeConfirm"
    />
  </div>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active > div,
.modal-leave-active > div {
  transition: transform 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-from > div {
  transform: scale(0.95);
}
.modal-leave-to > div {
  transform: scale(0.95);
}
</style>
