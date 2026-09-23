<script setup>
import { h, onMounted } from 'vue'
import { ConfigProvider, Empty, Typography } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { useAuthStore } from './stores/auth'
import { getStorage } from './utils/storage'

const auth = useAuthStore()

const theme = {
  token: {
    colorPrimary: '#0b6b53',
    colorInfo: '#2563a6',
    colorSuccess: '#237a4b',
    colorWarning: '#b7791f',
    colorError: '#b42318',
    colorText: '#1f2937',
    colorTextSecondary: '#64748b',
    colorBorder: '#dfe7e5',
    colorBgLayout: '#f5f7fa',
    borderRadius: 5,
    controlHeight: 38,
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  },
}

const locale = {
  ...zhCN,
  Table: { ...zhCN.Table, emptyText: '无数据' },
  Empty: { ...zhCN.Empty, description: '无数据' },
  Modal: { ...zhCN.Modal, okText: '确定', cancelText: '取消', justOkText: '确定' },
  Pagination: { ...zhCN.Pagination, page: '页' },
}

const renderEmpty = () => h(Empty, { description: '无数据' })

onMounted(() => {
  if (getStorage('wms_token')) auth.fetchMe()
  else auth.checking = false
})
</script>

<template>
  <ConfigProvider :theme="theme" :locale="locale" :render-empty="renderEmpty">
    <div v-if="auth.checking" class="login-shell">
      <Typography.Text>正在验证登录状态…</Typography.Text>
    </div>
    <router-view v-else />
  </ConfigProvider>
</template>
