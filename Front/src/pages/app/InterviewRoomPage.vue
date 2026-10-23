<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch, type ComponentPublicInstance } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppErrorState from '@/components/common/AppErrorState.vue'
import AppLoading from '@/components/common/AppLoading.vue'
import RoomQuestionSheet from '@/features/interview/RoomQuestionSheet.vue'
import {
  createRoom,
  finishRoom,
  getRoomDetail,
  getRtcToken,
  joinRoom,
  listQuestionOptions,
  saveRoomQuestions,
  type RoomDetail,
  type RoomQuestionOption,
  type RoomQuestionResult,
  type RoomStatus,
  ROOM_STATUS_LABELS,
} from '@/api/modules/room'
import { useTRTC } from '@/composables/useTRTC'
import { useAuthStore } from '@/stores/auth'
import { showErrorToast, showSuccessToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const trtc = useTRTC()

const roomCode = computed(() => String(route.params.roomCode || '').trim().toUpperCase())
const isEntry = computed(() => !roomCode.value)
const room = ref<RoomDetail | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const actionLoading = ref(false)
const saving = ref(false)
const questionOptions = ref<RoomQuestionOption[]>([])
const optionsLoading = ref(false)
const optionsError = ref('')
const questionFilter = ref('')
const joinCode = ref('')
const form = reactive({
  position: 'Java 后端',
  level: '校招',
  questionIds: [] as number[],
})

let pollTimer: ReturnType<typeof setInterval> | null = null
let saveTimer: ReturnType<typeof setTimeout> | null = null
let saveInFlight = false
let savePending = false

const {
  localVideoRef,
  remoteVideoRef,
  joined,
  connecting,
  audioMuted,
  videoEnabled,
  remoteUserId,
  remoteVideoVisible,
  networkQualityLabel,
  rtcError,
  degraded,
  enterRoom,
  toggleAudio,
  toggleVideo,
  switchCamera,
  leaveRoom,
} = trtc

const isInterviewer = computed(
  () => !!room.value && auth.userProfile?.id === room.value.interviewer.id,
)
const isCandidate = computed(
  () => !!room.value && auth.userProfile?.id === room.value.candidate?.id,
)
const isFinished = computed(() => room.value?.status === 2)
const canEdit = computed(() => isInterviewer.value && !isFinished.value)
const canFinish = computed(
  () => isInterviewer.value && room.value?.status === 1 && !!room.value.candidate,
)
const canEnterRtc = computed(
  () => !!room.value && room.value.status === 1 && (isInterviewer.value || isCandidate.value),
)
const statusLabel = computed(() =>
  room.value ? ROOM_STATUS_LABELS[room.value.status] : '',
)
const roleLabel = computed(() => (isInterviewer.value ? '面试官' : isCandidate.value ? '候选人' : '访客'))
const waitingForCandidate = computed(
  () => isInterviewer.value && !room.value?.candidate && room.value?.status === 0,
)
const remoteDisplayName = computed(() => {
  if (!remoteUserId.value) {
    return '对方'
  }
  const remoteId = Number(remoteUserId.value.replace(/^u/, ''))
  if (room.value?.interviewer.id === remoteId) {
    return room.value.interviewer.username
  }
  if (room.value?.candidate?.id === remoteId) {
    return room.value.candidate.username
  }
  return remoteUserId.value
})
const filteredOptions = computed(() => {
  const keyword = questionFilter.value.trim().toLowerCase()
  if (!keyword) {
    return questionOptions.value
  }
  return questionOptions.value.filter((option) =>
    `${option.stem} ${option.moduleName || ''}`.toLowerCase().includes(keyword),
  )
})
const selectedCount = computed(() => form.questionIds.length)

function difficultyLabel(difficulty: number | null) {
  if (difficulty === 3) {
    return '困难'
  }
  if (difficulty === 2) {
    return '中等'
  }
  return '简单'
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function setLocalVideoRef(el: Element | ComponentPublicInstance | null) {
  localVideoRef.value = el instanceof HTMLElement ? el : null
}

function setRemoteVideoRef(el: Element | ComponentPublicInstance | null) {
  remoteVideoRef.value = el instanceof HTMLElement ? el : null
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPollingIfNeeded() {
  stopPolling()
  if (!room.value) {
    return
  }
  if (isInterviewer.value && room.value.candidate) {
    return
  }
  pollTimer = setInterval(async () => {
    if (!room.value) {
      return
    }
    try {
      const latest = await getRoomDetail(roomCode.value)
      if (isInterviewer.value) {
        room.value = {
          ...latest,
          questions: room.value.questions,
          answeredCount: room.value.answeredCount,
          totalCount: room.value.totalCount,
        }
        if (latest.candidate) {
          stopPolling()
        }
      } else {
        room.value = latest
        if (latest.status === 2) {
          stopPolling()
        }
      }
    } catch {
      // 轮询失败不打断页面，下一次继续
    }
  }, 5000)
}

async function loadQuestionOptions() {
  optionsLoading.value = true
  optionsError.value = ''
  try {
    questionOptions.value = await listQuestionOptions()
  } catch (error) {
    optionsError.value = errorText(error, '题目加载失败，请稍后重试')
  } finally {
    optionsLoading.value = false
  }
}

async function loadRoom() {
  stopPolling()
  room.value = null
  errorMessage.value = ''
  if (!roomCode.value) {
    loading.value = false
    await loadQuestionOptions()
    return
  }
  loading.value = true
  try {
    if (!auth.userProfile) {
      await auth.fetchCurrentUserProfile()
    }
    room.value = await getRoomDetail(roomCode.value)
    startPollingIfNeeded()
  } catch (error) {
    errorMessage.value = errorText(error, '房间加载失败，请检查房间码')
  } finally {
    loading.value = false
  }
}

function toggleQuestion(questionId: number) {
  const index = form.questionIds.indexOf(questionId)
  if (index >= 0) {
    form.questionIds.splice(index, 1)
    return
  }
  if (form.questionIds.length >= 20) {
    showErrorToast('最多选择 20 道题', '无法继续选择', 'top-right')
    return
  }
  form.questionIds.push(questionId)
}

async function onCreateRoom() {
  if (actionLoading.value) {
    return
  }
  if (!form.position.trim()) {
    showErrorToast('请填写面试岗位', '参数错误', 'top-right')
    return
  }
  if (!form.level) {
    showErrorToast('请选择岗位级别', '参数错误', 'top-right')
    return
  }
  if (!form.questionIds.length) {
    showErrorToast('请至少选择一道题', '参数错误', 'top-right')
    return
  }
  actionLoading.value = true
  try {
    const data = await createRoom({
      position: form.position.trim(),
      level: form.level,
      questionIds: [...form.questionIds],
    })
    showSuccessToast(`房间码 ${data.roomCode}，分享给候选人即可加入`, '房间创建成功', 'top-center')
    await router.push(`/app/interview/room/${data.roomCode}`)
  } catch (error) {
    showErrorToast(errorText(error, '创建房间失败'), '创建失败', 'top-right')
  } finally {
    actionLoading.value = false
  }
}

async function onJoinRoom() {
  if (actionLoading.value) {
    return
  }
  const code = joinCode.value.trim().toUpperCase()
  if (!/^[A-Z0-9]{6}$/.test(code)) {
    showErrorToast('请输入 6 位房间码', '参数错误', 'top-right')
    return
  }
  actionLoading.value = true
  try {
    await joinRoom(code)
    showSuccessToast('已加入房间', '加入成功', 'top-center')
    await router.push(`/app/interview/room/${code}`)
  } catch (error) {
    showErrorToast(errorText(error, '加入房间失败'), '加入失败', 'top-right')
  } finally {
    actionLoading.value = false
  }
}

async function copyRoomCode() {
  if (!room.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(room.value.roomCode)
    showSuccessToast('房间码已复制', '复制成功', 'top-center')
  } catch {
    showErrorToast('复制失败，请手动复制房间码', '复制失败', 'top-right')
  }
}

function scheduleSave() {
  if (!canEdit.value) {
    return
  }
  if (saveTimer) {
    clearTimeout(saveTimer)
  }
  saveTimer = setTimeout(() => {
    void saveNow()
  }, 800)
}

async function saveNow(): Promise<boolean> {
  if (!room.value || !canEdit.value) {
    return false
  }
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
  }
  if (saveInFlight) {
    savePending = true
    return true
  }
  saveInFlight = true
  saving.value = true
  try {
    const data = await saveRoomQuestions(room.value.roomCode, {
      questions: room.value.questions.map((question) => ({
        roomQuestionId: question.id,
        result: (question.result ?? 0) as RoomQuestionResult,
        note: question.note || '',
      })),
    })
    room.value.answeredCount = data.answeredCount
    room.value.totalCount = data.totalCount
    return true
  } catch (error) {
    showErrorToast(errorText(error, '面试单保存失败'), '保存失败', 'top-right')
    return false
  } finally {
    saveInFlight = false
    saving.value = false
    if (savePending) {
      savePending = false
      void saveNow()
    }
  }
}

function onUpdateResult(roomQuestionId: number, result: RoomQuestionResult) {
  const question = room.value?.questions.find((item) => item.id === roomQuestionId)
  if (!question || !room.value) {
    return
  }
  question.result = result
  room.value.answeredCount = room.value.questions.filter(
    (item) => (item.result ?? 0) > 0,
  ).length
  scheduleSave()
}

function onUpdateNote(roomQuestionId: number, note: string) {
  const question = room.value?.questions.find((item) => item.id === roomQuestionId)
  if (!question) {
    return
  }
  question.note = note
  scheduleSave()
}

async function onEnterRtc() {
  if (!room.value || actionLoading.value) {
    return
  }
  actionLoading.value = true
  try {
    const token = await getRtcToken(room.value.roomCode)
    await enterRoom(token)
    if (joined.value) {
      showSuccessToast('已进入音视频房间', '连接成功', 'top-center')
    } else if (degraded.value) {
      showErrorToast(rtcError.value || '音视频不可用，可继续使用面试单', '音视频降级', 'top-right')
    }
  } catch (error) {
    showErrorToast(errorText(error, '获取音视频凭证失败'), '进入失败', 'top-right')
  } finally {
    actionLoading.value = false
  }
}

async function onSwitchCamera() {
  const switched = await switchCamera()
  if (!switched) {
    showErrorToast('未检测到可切换的摄像头', '切换失败', 'top-right')
  }
}

async function onLeaveRtc() {
  await leaveRoom()
  rtcError.value = ''
  degraded.value = false
  showSuccessToast('已退出音视频，面试单仍可继续使用', '已退出', 'top-center')
}

async function onFinish() {
  if (!room.value || !canFinish.value || actionLoading.value || saving.value) {
    return
  }
  if (!window.confirm('确认结束面试并生成 AI 总结？')) {
    return
  }
  actionLoading.value = true
  try {
    const saved = await saveNow()
    if (!saved) {
      return
    }
    await finishRoom(room.value.roomCode)
    await leaveRoom()
    showSuccessToast('面试已结束，正在生成 AI 报告', '操作成功', 'top-center')
    await router.push(`/app/interview/room/${room.value.roomCode}/report`)
  } catch (error) {
    showErrorToast(errorText(error, '结束面试失败'), '操作失败', 'top-right')
  } finally {
    actionLoading.value = false
  }
}

function statusBadgeClass(status: RoomStatus | undefined) {
  if (status === 1) {
    return 'bg-emerald-50 text-emerald-600 border-emerald-200'
  }
  if (status === 2) {
    return 'bg-slate-100 text-slate-500 border-slate-200'
  }
  return 'bg-amber-50 text-amber-600 border-amber-200'
}

watch(roomCode, () => {
  void loadRoom()
}, { immediate: true })

onUnmounted(() => {
  stopPolling()
  if (saveTimer) {
    clearTimeout(saveTimer)
  }
  void leaveRoom()
})
</script>

<template>
  <main class="mx-auto w-full max-w-6xl flex-grow px-4 py-8 sm:px-6 lg:py-10">
    <AppLoading v-if="loading" message="正在加载面试房间..." />

    <AppErrorState
      v-else-if="errorMessage"
      :message="errorMessage"
      @retry="loadRoom"
    />

    <template v-else-if="isEntry">
      <div class="fade-in-up mb-8 text-center">
        <div class="mb-4 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-tr from-rose-400 to-orange-400 text-white shadow-lg">
          <svg class="h-7 w-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.55-2.28A1 1 0 0121 8.62v6.76a1 1 0 01-1.45.9L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
          </svg>
        </div>
        <h1 class="text-3xl font-extrabold tracking-tight text-gray-800">双人视频面试</h1>
        <p class="mt-2 text-sm font-medium text-gray-500">面试官创建房间，候选人凭 6 位房间码加入；音视频由腾讯云 TRTC 承载。</p>
        <p class="mt-2 text-xs font-medium text-gray-400">请使用 HTTPS 或 localhost 打开页面，否则浏览器无法授权摄像头和麦克风。</p>
      </div>

      <div class="grid gap-6 lg:grid-cols-2">
        <section class="rounded-3xl border border-white bg-white/85 p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md sm:p-8">
          <div class="mb-5 flex items-center justify-between">
            <div>
              <h2 class="text-lg font-extrabold text-gray-800">我是面试官</h2>
              <p class="mt-1 text-xs font-medium text-gray-500">选题并创建房间，候选人加入后开始音视频面试。</p>
            </div>
            <span class="rounded-full bg-rose-50 px-3 py-1 text-[11px] font-extrabold text-rose-500">创建房间</span>
          </div>

          <form class="space-y-4" @submit.prevent="onCreateRoom">
            <div class="grid gap-4 sm:grid-cols-2">
              <label class="block">
                <span class="mb-1.5 block text-xs font-extrabold text-gray-600">面试岗位</span>
                <input v-model="form.position" type="text" maxlength="100" class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-medium outline-none transition-all focus:border-rose-300 focus:ring-2 focus:ring-rose-100" placeholder="例如：Java 后端" />
              </label>
              <label class="block">
                <span class="mb-1.5 block text-xs font-extrabold text-gray-600">岗位级别</span>
                <select v-model="form.level" class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm font-medium outline-none transition-all focus:border-rose-300 focus:ring-2 focus:ring-rose-100">
                  <option value="校招">校招</option>
                  <option value="初级">初级</option>
                  <option value="中级">中级</option>
                  <option value="高级">高级</option>
                </select>
              </label>
            </div>

            <div>
              <div class="mb-2 flex items-center justify-between">
                <span class="text-xs font-extrabold text-gray-600">面试题目（{{ selectedCount }}/20）</span>
                <input v-model="questionFilter" type="search" class="w-40 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-medium outline-none focus:border-rose-300" placeholder="搜索题目/模块" />
              </div>
              <div v-if="optionsLoading" class="rounded-xl border border-slate-100 bg-slate-50 p-4 text-xs text-slate-500">题目加载中...</div>
              <div v-else-if="optionsError" class="rounded-xl border border-rose-100 bg-rose-50 p-4 text-xs font-medium text-rose-600">
                {{ optionsError }}
                <button type="button" class="ml-2 font-extrabold underline" @click="loadQuestionOptions">重试</button>
              </div>
              <div v-else-if="!filteredOptions.length" class="rounded-xl border border-dashed border-slate-200 p-4 text-xs text-slate-500">暂无可用题目，请先在题库中添加题目。</div>
              <div v-else class="max-h-64 space-y-2 overflow-y-auto rounded-xl border border-slate-100 bg-slate-50/60 p-2">
                <label v-for="option in filteredOptions" :key="option.id" class="flex cursor-pointer items-start gap-3 rounded-lg bg-white px-3 py-2.5 transition-all hover:bg-rose-50/50">
                  <input type="checkbox" class="mt-1 h-4 w-4 accent-rose-500" :checked="form.questionIds.includes(option.id)" @change="toggleQuestion(option.id)" />
                  <span class="min-w-0 flex-1">
                    <span class="block text-xs font-semibold leading-relaxed text-gray-700">{{ option.stem }}</span>
                    <span class="mt-1 flex flex-wrap gap-1.5">
                      <span class="rounded-full bg-indigo-50 px-2 py-0.5 text-[10px] font-bold text-indigo-500">{{ option.moduleName || '未分类' }}</span>
                      <span class="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-600">{{ difficultyLabel(option.difficulty) }}</span>
                    </span>
                  </span>
                </label>
              </div>
            </div>

            <button type="submit" class="w-full rounded-2xl bg-gradient-to-r from-rose-400 to-orange-400 py-3 text-sm font-extrabold text-white shadow-[0_8px_20px_rgba(244,63,94,0.25)] transition-all hover:from-rose-500 hover:to-orange-500 disabled:cursor-not-allowed disabled:opacity-70" :disabled="actionLoading">
              {{ actionLoading ? '正在创建...' : '创建面试房间' }}
            </button>
          </form>
        </section>

        <section class="flex flex-col rounded-3xl border border-white bg-white/85 p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md sm:p-8">
          <div class="mb-5 flex items-center justify-between">
            <div>
              <h2 class="text-lg font-extrabold text-gray-800">我是候选人</h2>
              <p class="mt-1 text-xs font-medium text-gray-500">输入面试官分享的 6 位房间码加入。</p>
            </div>
            <span class="rounded-full bg-emerald-50 px-3 py-1 text-[11px] font-extrabold text-emerald-600">加入房间</span>
          </div>
          <form class="space-y-4" @submit.prevent="onJoinRoom">
            <label class="block">
              <span class="mb-1.5 block text-xs font-extrabold text-gray-600">房间码</span>
              <input v-model="joinCode" type="text" maxlength="6" class="w-full rounded-2xl border border-slate-200 bg-white px-4 py-4 text-center text-2xl font-black uppercase tracking-[0.35em] text-gray-800 outline-none transition-all focus:border-emerald-300 focus:ring-2 focus:ring-emerald-100" placeholder="ABC123" />
            </label>
            <button type="submit" class="w-full rounded-2xl bg-slate-900 py-3 text-sm font-extrabold text-white transition-all hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-70" :disabled="actionLoading">
              {{ actionLoading ? '正在加入...' : '加入面试房间' }}
            </button>
          </form>
          <div class="mt-auto rounded-2xl bg-slate-50 p-4 text-xs font-medium leading-relaxed text-slate-500">
            <p class="font-extrabold text-slate-700">加入后你会看到：</p>
            <p class="mt-1">面试题目和当前进度；逐题评价仅面试官可见。音视频需要你点击“进入音视频面试”后才会开启。</p>
          </div>
        </section>
      </div>

      <div class="mt-6 text-center">
        <RouterLink to="/app/interview/setup" class="text-xs font-bold text-slate-400 hover:text-rose-500">← 返回 AI 模拟面试</RouterLink>
      </div>
    </template>

    <template v-else-if="room">
      <div class="fade-in-up mb-5 flex flex-wrap items-center gap-3">
        <RouterLink to="/app/interview/room" class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-500 hover:border-rose-200 hover:text-rose-500">← 双人面试</RouterLink>
        <div>
          <h1 class="text-xl font-extrabold text-gray-800">房间 {{ room.roomCode }}</h1>
          <p class="mt-0.5 text-xs font-medium text-gray-500">{{ room.position }} · {{ room.level }} · 你是{{ roleLabel }}</p>
        </div>
        <span class="rounded-full border px-3 py-1 text-[11px] font-extrabold" :class="statusBadgeClass(room.status)">{{ statusLabel }}</span>
        <button type="button" class="rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[11px] font-extrabold text-slate-500 hover:border-rose-200 hover:text-rose-500" @click="copyRoomCode">复制房间码</button>
        <RouterLink v-if="isFinished" :to="`/app/interview/room/${room.roomCode}/report`" class="ml-auto rounded-full bg-gradient-to-r from-rose-400 to-orange-400 px-4 py-2 text-xs font-extrabold text-white shadow-sm">查看 AI 报告 →</RouterLink>
      </div>

      <div class="grid gap-5 lg:grid-cols-[minmax(0,1.25fr)_minmax(320px,0.75fr)]">
        <div class="space-y-5">
          <section class="overflow-hidden rounded-3xl border border-white bg-white/85 shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md">
            <div class="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-3">
              <div class="flex items-center gap-2 text-xs font-extrabold text-slate-600">
                <span class="h-2 w-2 rounded-full" :class="joined ? 'bg-emerald-400' : 'bg-slate-300'" />
                音视频面试
                <span v-if="joined" class="font-medium text-slate-400">网络：{{ networkQualityLabel }}</span>
              </div>
              <span class="text-[11px] font-medium text-slate-400">TRTC 实时音视频</span>
            </div>

            <div v-if="joined" class="relative aspect-video bg-slate-900">
              <div :ref="setRemoteVideoRef" class="h-full w-full" />
              <div v-if="!remoteVideoVisible" class="absolute inset-0 flex flex-col items-center justify-center gap-2 text-slate-300">
                <svg class="h-10 w-10 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.6" d="M15 10l4.55-2.28A1 1 0 0121 8.62v6.76a1 1 0 01-1.45.9L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
                <p class="text-xs font-bold">{{ remoteUserId ? `${remoteDisplayName} 暂未开启摄像头` : '等待对方进入房间' }}</p>
              </div>
              <div class="absolute bottom-3 right-3 h-24 w-32 overflow-hidden rounded-xl border-2 border-white/80 bg-slate-800 shadow-lg sm:h-28 sm:w-40">
                <div :ref="setLocalVideoRef" class="h-full w-full" />
                <span class="absolute bottom-1 left-1 rounded bg-black/45 px-1.5 py-0.5 text-[10px] font-bold text-white">我</span>
              </div>
              <div class="absolute left-3 top-3 rounded-full bg-black/40 px-2.5 py-1 text-[10px] font-bold text-white">{{ remoteUserId ? remoteDisplayName : '等待对方' }}</div>
            </div>

            <div v-else class="flex aspect-video flex-col items-center justify-center gap-3 bg-slate-900 px-6 text-center">
              <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/10 text-white">
                <svg class="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M15 10l4.55-2.28A1 1 0 0121 8.62v6.76a1 1 0 01-1.45.9L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
              </div>
              <p v-if="waitingForCandidate" class="text-sm font-extrabold text-white">等待候选人凭房间码加入...</p>
              <p v-else-if="isFinished" class="text-sm font-extrabold text-white">面试已结束</p>
              <p v-else class="text-sm font-extrabold text-white">音视频尚未开启</p>
              <p v-if="canEnterRtc" class="max-w-sm text-xs font-medium leading-relaxed text-slate-300">
                点击下方按钮后浏览器会请求摄像头和麦克风权限。若设备不可用，可继续使用右侧面试单完成面试并生成 AI 总结。
              </p>
              <button v-if="canEnterRtc" type="button" class="rounded-2xl bg-gradient-to-r from-rose-400 to-orange-400 px-5 py-2.5 text-xs font-extrabold text-white shadow-lg transition-all hover:from-rose-500 hover:to-orange-500 disabled:opacity-70" :disabled="actionLoading || connecting" @click="onEnterRtc">
                {{ actionLoading || connecting ? '正在连接...' : joined ? '重新进入音视频' : '进入音视频面试' }}
              </button>
            </div>

            <div v-if="joined" class="flex flex-wrap items-center gap-2 border-t border-slate-100 px-4 py-3">
              <button type="button" class="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-extrabold text-slate-600 hover:bg-slate-50" @click="toggleAudio">{{ audioMuted ? '取消静音' : '静音' }}</button>
              <button type="button" class="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-extrabold text-slate-600 hover:bg-slate-50" @click="toggleVideo">{{ videoEnabled ? '关闭摄像头' : '开启摄像头' }}</button>
              <button type="button" class="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-extrabold text-slate-600 hover:bg-slate-50" @click="onSwitchCamera">切换摄像头</button>
              <button type="button" class="rounded-xl border border-rose-100 bg-rose-50 px-3 py-1.5 text-xs font-extrabold text-rose-500 hover:bg-rose-100" @click="onLeaveRtc">退出音视频</button>
              <button v-if="isInterviewer" type="button" class="ml-auto rounded-xl bg-slate-900 px-4 py-1.5 text-xs font-extrabold text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50" :disabled="!canFinish || actionLoading || saving" @click="onFinish">结束面试并生成报告</button>
            </div>
          </section>

          <div v-if="rtcError || degraded" class="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-bold text-amber-700">
            {{ rtcError }}
            <span v-if="degraded" class="ml-1">— 你仍可以继续填写/查看面试单。</span>
          </div>

          <div v-if="!joined && isInterviewer" class="rounded-2xl border border-slate-100 bg-white/70 px-4 py-3 text-xs font-medium text-slate-500">
            结束面试前请先完成右侧逐题评价；未评题目不会计入 AI 评分，但会在报告中标记为“未覆盖”。
            <button type="button" class="ml-2 font-extrabold text-rose-500 hover:underline" :disabled="!canFinish || actionLoading || saving" @click="onFinish">直接结束面试</button>
          </div>
        </div>

        <RoomQuestionSheet
          :questions="room.questions"
          :editable="canEdit"
          :disabled="isFinished"
          :saving="saving"
          :answered-count="room.answeredCount"
          :total-count="room.totalCount"
          @update-result="onUpdateResult"
          @update-note="onUpdateNote"
        />
      </div>
    </template>
  </main>
</template>

<style scoped>
.fade-in-up {
  animation: fadeInUp 0.45s ease-out both;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
