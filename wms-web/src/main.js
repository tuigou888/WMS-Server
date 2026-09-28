import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/client'
import './styles.css'

dayjs.locale('zh-cn')

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Antd, { locale: zhCN })

setUnauthorizedHandler(() => {
  // 401 被踢出前记住当前页面，重新登录后回跳原处（配合页面级表单草稿恢复，避免过期丢工作现场）
  const current = router.currentRoute.value
  if (current.path !== '/login') sessionStorage.setItem('wms_redirect_after_login', current.fullPath)
  router.push('/login')
})

app.mount('#root')
