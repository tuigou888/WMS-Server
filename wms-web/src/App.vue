<script setup>
import { onMounted } from 'vue'
import { ConfigProvider, Typography } from 'ant-design-vue'
import { useAuthStore } from './stores/auth'
import { getStorage } from './utils/storage'

const auth = useAuthStore()

const theme = {
  token: { colorPrimary: '#1677ff', borderRadius: 8, fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif' },
}

onMounted(() => {
  if (getStorage('wms_token')) auth.fetchMe()
  else auth.checking = false
})
</script>

<template>
  <ConfigProvider :theme="theme">
    <div v-if="auth.checking" class="login-shell">
      <Typography.Text>正在验证登录状态…</Typography.Text>
    </div>
    <router-view v-else />
  </ConfigProvider>
</template>
