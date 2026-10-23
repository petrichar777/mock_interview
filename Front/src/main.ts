import { createApp } from 'vue'
import App from './App.vue'
import { createPinia } from 'pinia'
import router from '@/router'
import { showErrorToast } from '@/utils/toast'
import '@/styles/index.css'

const app = createApp(App)

app.config.errorHandler = (err, _instance, _info) => {
  const message = err instanceof Error ? err.message : '未知错误'
  console.error('[Vue error]', err)
  showErrorToast(message, '应用异常', 'top-center')
}

app.use(createPinia())
app.use(router)
app.mount('#app')
