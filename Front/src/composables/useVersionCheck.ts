import { ref, onMounted, onUnmounted } from 'vue'

const POLL_INTERVAL = 3 * 60 * 1000 // 每 3 分钟检测一次

export function useVersionCheck() {
  const hasUpdate = ref(false)
  const newVersion = ref('')
  let timer: ReturnType<typeof setInterval> | null = null

  async function check() {
    try {
      const res = await fetch('/version.json?t=' + Date.now(), { cache: 'no-store' })
      if (!res.ok) return
      const data = await res.json()
      if (!data.version) return

      const cached = localStorage.getItem('app_version')
      if (cached && cached !== data.version) {
        hasUpdate.value = true
        newVersion.value = data.version
      }
      localStorage.setItem('app_version', data.version)
    } catch {
      // 开发环境没有 version.json，静默忽略
    }
  }

  function applyUpdate() {
    localStorage.setItem('app_version', newVersion.value)
    window.location.reload()
  }

  onMounted(() => {
    check()
    timer = setInterval(check, POLL_INTERVAL)
  })

  onUnmounted(() => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  })

  return { hasUpdate, newVersion, applyUpdate }
}
