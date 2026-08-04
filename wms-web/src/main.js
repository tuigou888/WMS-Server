import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { setUnauthorizedHandler } from './api/client'
import './styles.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Antd)

setUnauthorizedHandler(() => {
  router.push('/login')
})

// 初始化时验证 token 并拉取用户信息
const auth = useAuthStore()
auth.fetchMe().catch(() => {})

app.mount('#root')
