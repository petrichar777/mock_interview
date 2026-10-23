import { computed, nextTick, onUnmounted, ref } from 'vue'
import TRTC from 'trtc-sdk-v5'
import type { RtcTokenResponse } from '@/api/modules/room'

type TrtcClient = ReturnType<typeof TRTC.create>

const NETWORK_QUALITY_LABELS = ['未知', '极佳', '良好', '一般', '较差', '极差', '已断开']

export function useTRTC() {
  const localVideoRef = ref<HTMLElement | null>(null)
  const remoteVideoRef = ref<HTMLElement | null>(null)
  const joined = ref(false)
  const connecting = ref(false)
  const audioMuted = ref(false)
  const videoEnabled = ref(true)
  const remoteUserId = ref('')
  const remoteVideoVisible = ref(false)
  const networkQuality = ref(0)
  const rtcError = ref('')
  const degraded = ref(false)

  let trtc: TrtcClient | null = null
  let cameraIndex = 0

  const networkQualityLabel = computed(
    () => NETWORK_QUALITY_LABELS[networkQuality.value] || '未知',
  )

  async function enterRoom(token: RtcTokenResponse) {
    if (joined.value || connecting.value) {
      return
    }
    connecting.value = true
    rtcError.value = ''
    degraded.value = false
    try {
      trtc = TRTC.create()
      trtc.on(TRTC.EVENT.ERROR, (error) => {
        rtcError.value = error.message || '音视频连接异常'
      })
      trtc.on(TRTC.EVENT.KICKED_OUT, () => {
        rtcError.value = '当前账号已在其他页面进入该房间'
        joined.value = false
      })
      trtc.on(TRTC.EVENT.REMOTE_USER_ENTER, ({ userId }) => {
        remoteUserId.value = userId
      })
      trtc.on(TRTC.EVENT.REMOTE_USER_EXIT, ({ userId }) => {
        if (userId === remoteUserId.value) {
          remoteUserId.value = ''
          remoteVideoVisible.value = false
        }
      })
      trtc.on(TRTC.EVENT.REMOTE_VIDEO_AVAILABLE, async ({ userId, streamType }) => {
        if (streamType !== TRTC.TYPE.STREAM_TYPE_MAIN) {
          return
        }
        remoteUserId.value = userId
        await playRemoteVideo(userId)
      })
      trtc.on(TRTC.EVENT.REMOTE_VIDEO_UNAVAILABLE, ({ userId, streamType }) => {
        if (userId === remoteUserId.value && streamType === TRTC.TYPE.STREAM_TYPE_MAIN) {
          remoteVideoVisible.value = false
        }
      })
      trtc.on(TRTC.EVENT.NETWORK_QUALITY, (quality) => {
        networkQuality.value = quality.uplinkNetworkQuality
      })

      await trtc.enterRoom({
        sdkAppId: token.sdkAppId,
        userId: token.userId,
        userSig: token.userSig,
        roomId: token.roomId,
        scene: TRTC.TYPE.SCENE_RTC,
      })
      joined.value = true

      await nextTick()
      try {
        if (localVideoRef.value) {
          await trtc.startLocalVideo({ view: localVideoRef.value })
        }
      } catch {
        degraded.value = true
        rtcError.value = '摄像头不可用，已切换到音频/面试单模式'
        videoEnabled.value = false
      }
      try {
        await trtc.startLocalAudio()
      } catch {
        degraded.value = true
        rtcError.value = '麦克风不可用，已切换到面试单模式'
        audioMuted.value = true
      }
    } catch (error) {
      degraded.value = true
      rtcError.value = error instanceof Error
        ? error.message
        : '音视频进房失败，可继续使用面试单模式'
      await leaveRoom()
    } finally {
      connecting.value = false
    }
  }

  async function playRemoteVideo(userId: string) {
    if (!trtc || !remoteVideoRef.value) {
      return
    }
    await nextTick()
    if (!remoteVideoRef.value) {
      return
    }
    try {
      await trtc.startRemoteVideo({
        userId,
        streamType: TRTC.TYPE.STREAM_TYPE_MAIN,
        view: remoteVideoRef.value,
      })
      remoteVideoVisible.value = true
    } catch {
      remoteVideoVisible.value = false
    }
  }

  async function toggleAudio() {
    if (!trtc || !joined.value) {
      return
    }
    const nextMuted = !audioMuted.value
    try {
      await trtc.updateLocalAudio({ mute: nextMuted })
      audioMuted.value = nextMuted
    } catch {
      rtcError.value = '麦克风切换失败，请检查设备权限'
    }
  }

  async function toggleVideo() {
    if (!trtc || !joined.value) {
      return
    }
    try {
      if (videoEnabled.value) {
        await trtc.stopLocalVideo()
        videoEnabled.value = false
      } else {
        await nextTick()
        if (localVideoRef.value) {
          await trtc.startLocalVideo({ view: localVideoRef.value })
          videoEnabled.value = true
        }
      }
    } catch {
      rtcError.value = '摄像头切换失败，请检查设备权限'
    }
  }

  async function switchCamera() {
    if (!trtc || !videoEnabled.value) {
      return false
    }
    try {
      const cameras = await TRTC.getCameraList()
      if (cameras.length < 2) {
        return false
      }
      cameraIndex = (cameraIndex + 1) % cameras.length
      await trtc.updateLocalVideo({ option: { cameraId: cameras[cameraIndex].deviceId } })
      return true
    } catch {
      rtcError.value = '切换摄像头失败'
      return false
    }
  }

  async function leaveRoom() {
    const client = trtc
    trtc = null
    joined.value = false
    remoteUserId.value = ''
    remoteVideoVisible.value = false
    networkQuality.value = 0
    if (!client) {
      return
    }
    try {
      await client.exitRoom()
    } catch {
      // 房间未进入或已断开时 exitRoom 会失败，忽略即可
    }
    try {
      client.destroy()
    } catch {
      // destroy 失败不影响页面降级
    }
  }

  onUnmounted(() => {
    void leaveRoom()
  })

  return {
    localVideoRef,
    remoteVideoRef,
    joined,
    connecting,
    audioMuted,
    videoEnabled,
    remoteUserId,
    remoteVideoVisible,
    networkQuality,
    networkQualityLabel,
    rtcError,
    degraded,
    enterRoom,
    toggleAudio,
    toggleVideo,
    switchCamera,
    leaveRoom,
  }
}
