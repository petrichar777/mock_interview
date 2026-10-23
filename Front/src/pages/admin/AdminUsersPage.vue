<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { listAdminUsers, grantQuota, clearQuota, type AdminUser, updateAdminUserStatus } from '@/api/modules/admin'
import { showActionSuccessToast, showErrorToast } from '@/utils/toast'
import { useDebounceRef } from '@/composables/useDebounceRef'

const keyword = ref('')
const debouncedKeyword = useDebounceRef(keyword)
const page = ref(1)
const pageSize = ref(10)
const users = ref<AdminUser[]>([])
const total = ref(0)
const loading = ref(false)
const updatingUserIds = ref<number[]>([])

// Grant quota modal
const quotaModal = ref(false)
const quotaTarget = ref<AdminUser | null>(null)
const quotaForm = reactive({ interviewQuota: 0, resumeQuota: 0 })
const quotaSubmitting = ref(false)

function openQuotaModal(user: AdminUser) {
  quotaTarget.value = user
  quotaForm.interviewQuota = 0
  quotaForm.resumeQuota = 0
  quotaModal.value = true
}

function closeQuotaModal() {
  quotaModal.value = false
  quotaTarget.value = null
}

async function submitGrantQuota() {
  if (!quotaTarget.value) return
  if (quotaForm.interviewQuota < 0 || quotaForm.resumeQuota < 0) {
    showErrorToast('配额不能为负数', '参数错误', 'top-right')
    return
  }
  if (quotaForm.interviewQuota === 0 && quotaForm.resumeQuota === 0) {
    showErrorToast('请至少填写一项配额', '参数错误', 'top-right')
    return
  }
  quotaSubmitting.value = true
  try {
    await grantQuota(quotaTarget.value.id, {
      interviewQuota: quotaForm.interviewQuota,
      resumeQuota: quotaForm.resumeQuota,
    })
    quotaTarget.value.extraInterviewQuota += quotaForm.interviewQuota
    quotaTarget.value.extraResumeQuota += quotaForm.resumeQuota
    showActionSuccessToast('配额发放成功', 'top-right')
    closeQuotaModal()
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '配额发放失败', '操作失败', 'top-right')
  } finally {
    quotaSubmitting.value = false
  }
}

async function onClearQuota() {
  if (!quotaTarget.value) return
  if (quotaTarget.value.extraInterviewQuota === 0 && quotaTarget.value.extraResumeQuota === 0) {
    showErrorToast('该用户当前无额外配额', '无需清除', 'top-right')
    return
  }
  quotaSubmitting.value = true
  try {
    await clearQuota(quotaTarget.value.id)
    quotaTarget.value.extraInterviewQuota = 0
    quotaTarget.value.extraResumeQuota = 0
    showActionSuccessToast('配额已清除', 'top-right')
    closeQuotaModal()
  } catch (err) {
    showErrorToast(err instanceof Error ? err.message : '配额清除失败', '操作失败', 'top-right')
  } finally {
    quotaSubmitting.value = false
  }
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const rangeStart = computed(() => (total.value === 0 ? 0 : (page.value - 1) * pageSize.value + 1))
const rangeEnd = computed(() => Math.min(total.value, rangeStart.value + users.value.length - 1))

type PageItem = { type: 'page'; num: number } | { type: 'ellipsis' }
const visiblePages = computed<PageItem[]>(() => {
  const total = totalPages.value
  const current = page.value
  const items: PageItem[] = []
  const delta = 2

  let left = Math.max(2, current - delta)
  let right = Math.min(total - 1, current + delta)

  if (current - delta <= 2) right = Math.min(total - 1, 1 + delta * 2)
  if (current + delta >= total - 1) left = Math.max(2, total - delta * 2)

  items.push({ type: 'page', num: 1 })
  if (left > 2) items.push({ type: 'ellipsis' })
  for (let i = left; i <= right; i++) items.push({ type: 'page', num: i })
  if (right < total - 1) items.push({ type: 'ellipsis' })
  if (total > 1) items.push({ type: 'page', num: total })

  return items
})

async function loadUsers() {
  loading.value = true
  try {
    const data = await listAdminUsers({
      keyword: debouncedKeyword.value.trim() || undefined,
      page: page.value,
      size: pageSize.value,
    })
    users.value = data.list
    total.value = data.total
    page.value = data.page
    pageSize.value = data.size
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '用户列表加载失败', '加载失败', 'top-right')
  } finally {
    loading.value = false
  }
}

function goPrev() {
  if (page.value <= 1 || loading.value) {
    return
  }
  page.value -= 1
}

function goNext() {
  if (page.value >= totalPages.value || loading.value) {
    return
  }
  page.value += 1
}

function goPage(target: number) {
  if (target === page.value || loading.value) {
    return
  }
  page.value = target
}

async function toggleStatus(target: AdminUser) {
  if (updatingUserIds.value.includes(target.id)) {
    return
  }
  updatingUserIds.value.push(target.id)
  const nextStatus = target.status === 0 ? 1 : 0
  try {
    await updateAdminUserStatus(target.id, { status: nextStatus })
    target.status = nextStatus
    showActionSuccessToast(nextStatus === 0 ? '启用用户' : '禁用用户', 'top-right')
  } catch (error) {
    showErrorToast(error instanceof Error ? error.message : '用户状态更新失败', '操作失败', 'top-right')
  } finally {
    updatingUserIds.value = updatingUserIds.value.filter((id) => id !== target.id)
  }
}

function getAvatarClass(userId: number) {
  const tones = [
    'bg-rose-100 text-rose-500 border-rose-200',
    'bg-orange-100 text-orange-500 border-orange-200',
    'bg-emerald-100 text-emerald-500 border-emerald-200',
    'bg-blue-100 text-blue-500 border-blue-200',
  ]
  return tones[userId % tones.length]
}

function getStatusClass(status: number) {
  if (status === 0) {
    return 'bg-emerald-50 text-emerald-500 border-emerald-100'
  }
  return 'bg-rose-50 text-rose-500 border-rose-100'
}

function getStatusText(status: number) {
  return status === 0 ? '正常' : '已禁用'
}

function isUpdating(userId: number) {
  return updatingUserIds.value.includes(userId)
}

onMounted(() => {
  loadUsers()
})

watch(page, () => {
  loadUsers()
})

watch(
  () => debouncedKeyword.value,
  () => {
    if (page.value === 1) {
      loadUsers()
      return
    }
    page.value = 1
  },
)
</script>

<template>
  <section class="p-8">
    <div class="fade-in mx-auto w-full">
      <div class="mb-6 flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
        <div class="relative w-full sm:w-72">
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索用户名/邮箱..."
            class="w-full rounded-2xl border border-white bg-white/80 py-2.5 pl-11 pr-4 text-sm font-medium shadow-sm transition-all focus:border-rose-400 focus:outline-none focus:ring-1 focus:ring-rose-400"
          />
          <svg class="absolute left-3.5 top-2.5 h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </div>
        <button
          type="button"
          class="flex items-center gap-2 rounded-2xl border border-white bg-white/80 px-5 py-2.5 text-sm font-bold text-gray-600 shadow-sm transition-all hover:bg-white hover:text-rose-500"
          :disabled="loading"
          @click="loadUsers"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          导出数据
        </button>
      </div>

      <div class="overflow-hidden rounded-3xl border border-white bg-white/80 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-lg">
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-100/50 text-sm">
            <thead class="bg-white/50">
              <tr>
                <th class="px-4 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">ID</th>
                <th class="px-4 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">用户名</th>
                <th class="px-4 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">邮箱</th>
                <th class="px-4 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">角色</th>
                <th class="px-4 py-4 text-center text-xs font-extrabold uppercase tracking-wider text-gray-400">面试配额</th>
                <th class="px-4 py-4 text-center text-xs font-extrabold uppercase tracking-wider text-gray-400">简历配额</th>
                <th class="px-4 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">注册时间</th>
                <th class="px-4 py-4 text-left text-xs font-extrabold uppercase tracking-wider text-gray-400">状态</th>
                <th class="px-4 py-4 text-right text-xs font-extrabold uppercase tracking-wider text-gray-400">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100/50 bg-transparent">
              <tr v-for="user in users" :key="user.id" class="transition-colors hover:bg-white">
                <td class="whitespace-nowrap px-4 py-4 font-medium text-gray-500">#{{ user.id }}</td>
                <td class="whitespace-nowrap px-4 py-4">
                  <div class="flex items-center">
                    <div
                      class="mr-3 flex h-8 w-8 items-center justify-center rounded-xl border text-sm font-bold shadow-sm"
                      :class="getAvatarClass(user.id)"
                    >
                      {{ user.username.slice(0, 1) }}
                    </div>
                    <span class="font-extrabold text-gray-800">{{ user.username }}</span>
                  </div>
                </td>
                <td class="whitespace-nowrap px-4 py-4 font-medium text-gray-500">{{ user.email }}</td>
                <td class="whitespace-nowrap px-4 py-4 font-bold" :class="user.role === 'ADMIN' ? 'text-violet-500' : 'text-gray-500'">
                  {{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}
                </td>
                <td class="whitespace-nowrap px-4 py-4 text-center">
                  <span class="inline-flex rounded-lg px-2.5 py-1 text-xs font-bold"
                    :class="user.extraInterviewQuota > 0 ? 'bg-violet-50 text-violet-600' : 'bg-gray-100 text-gray-400'">
                    {{ user.extraInterviewQuota }}
                  </span>
                </td>
                <td class="whitespace-nowrap px-4 py-4 text-center">
                  <span class="inline-flex rounded-lg px-2.5 py-1 text-xs font-bold"
                    :class="user.extraResumeQuota > 0 ? 'bg-rose-50 text-rose-500' : 'bg-gray-100 text-gray-400'">
                    {{ user.extraResumeQuota }}
                  </span>
                </td>
                <td class="whitespace-nowrap px-4 py-4 font-medium text-gray-500">{{ user.createdAt }}</td>
                <td class="whitespace-nowrap px-4 py-4">
                  <span class="inline-flex rounded-xl border px-3 py-1 text-xs font-bold shadow-sm" :class="getStatusClass(user.status)">
                    {{ getStatusText(user.status) }}
                  </span>
                </td>
                <td class="whitespace-nowrap px-4 py-4 text-right font-bold">
                  <div class="flex items-center justify-end gap-2">
                    <button
                      type="button"
                      class="rounded-xl border border-violet-200 bg-violet-50 px-3 py-1.5 text-xs font-bold text-violet-500 shadow-sm transition-all hover:border-violet-300 hover:bg-violet-100"
                      @click="openQuotaModal(user)"
                    >
                      配额
                    </button>
                    <button
                      type="button"
                      :disabled="isUpdating(user.id)"
                      class="rounded-xl border px-3 py-1.5 text-xs shadow-sm transition-all"
                      :class="
                        user.status === 0
                          ? 'border-rose-100 bg-rose-50 text-rose-500 hover:border-rose-200 hover:bg-rose-100 hover:text-rose-600'
                          : 'border-emerald-100 bg-emerald-50 text-emerald-500 hover:border-emerald-200 hover:bg-emerald-100 hover:text-emerald-600'
                      "
                      @click="toggleStatus(user)"
                    >
                      {{ isUpdating(user.id) ? '提交中...' : user.status === 0 ? '禁用' : '启用' }}
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!users.length">
                <td colspan="9" class="px-6 py-10 text-center text-sm font-bold text-gray-400">暂无用户数据</td>
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

      <!-- Grant quota modal -->
      <Teleport to="body">
        <Transition name="modal">
          <div
            v-if="quotaModal"
            class="fixed inset-0 z-[200] flex items-center justify-center p-4"
            @click.self="closeQuotaModal"
          >
            <div class="absolute inset-0 bg-black/30 backdrop-blur-sm" />
            <div class="relative w-full max-w-sm overflow-hidden rounded-3xl border border-white bg-white/95 shadow-[0_16px_60px_rgba(0,0,0,0.12)] backdrop-blur-xl">
              <div class="border-b border-gray-100 px-6 py-5">
                <h3 class="text-lg font-extrabold text-gray-800">发放配额</h3>
                <p class="mt-1 text-sm font-medium text-gray-500">
                  为 <span class="font-extrabold text-violet-500">{{ quotaTarget?.username }}</span> 添加额外配额
                </p>
              </div>

              <div class="space-y-5 px-6 py-5">
                <div>
                  <label class="mb-2 block text-sm font-extrabold text-gray-700">
                    面试次数
                    <span class="font-normal text-gray-400">— 当前额度 {{ quotaTarget?.extraInterviewQuota ?? 0 }}</span>
                  </label>
                  <input
                    v-model.number="quotaForm.interviewQuota"
                    type="number"
                    min="0"
                    class="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm font-bold text-gray-800 transition-all focus:border-violet-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-violet-100"
                    placeholder="输入增加的面试次数"
                  />
                </div>
                <div>
                  <label class="mb-2 block text-sm font-extrabold text-gray-700">
                    简历优化次数
                    <span class="font-normal text-gray-400">— 当前额度 {{ quotaTarget?.extraResumeQuota ?? 0 }}</span>
                  </label>
                  <input
                    v-model.number="quotaForm.resumeQuota"
                    type="number"
                    min="0"
                    class="w-full rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm font-bold text-gray-800 transition-all focus:border-rose-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-100"
                    placeholder="输入增加的简历优化次数"
                  />
                </div>
              </div>

              <div class="flex gap-3 border-t border-gray-100 px-6 py-4">
                <button
                  type="button"
                  class="flex-1 rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-bold text-gray-600 transition-colors hover:bg-gray-50"
                  :disabled="quotaSubmitting"
                  @click="closeQuotaModal"
                >
                  取消
                </button>
                <button
                  type="button"
                  class="flex-1 rounded-xl bg-gradient-to-r from-violet-400 to-purple-500 px-4 py-2.5 text-sm font-extrabold text-white shadow-lg shadow-violet-200 transition-all hover:-translate-y-0.5 hover:shadow-xl disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0"
                  :disabled="quotaSubmitting"
                  @click="submitGrantQuota"
                >
                  <span v-if="quotaSubmitting" class="flex items-center justify-center gap-1.5">
                    <svg class="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    发放中...
                  </span>
                  <span v-else>确认发放</span>
                </button>
              </div>
              <div class="border-t border-gray-100 px-6 py-3">
                <button
                  type="button"
                  class="w-full rounded-xl border border-red-200 bg-red-50 px-4 py-2.5 text-sm font-bold text-red-500 transition-colors hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-50"
                  :disabled="quotaSubmitting"
                  @click="onClearQuota"
                >
                  清除全部配额
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>
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

.modal-enter-active {
  transition: all 0.3s ease-out;
}

.modal-leave-active {
  transition: all 0.2s ease-in;
}

.modal-enter-from {
  opacity: 0;
}

.modal-enter-from > :last-child {
  transform: scale(0.92) translateY(20px);
  opacity: 0;
}

.modal-leave-to {
  opacity: 0;
}

.modal-leave-to > :last-child {
  transform: scale(0.95) translateY(10px);
  opacity: 0;
}
</style>
