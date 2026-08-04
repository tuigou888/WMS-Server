<script setup>
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button, Layout, Menu, Space, Typography } from 'ant-design-vue'
import { SwapOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '../stores/auth'
import { buildMenu } from '../utils/menu'

const { Sider, Header, Content } = Layout
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const menuItems = computed(() => buildMenu(auth.user))
const selectedKeys = computed(() => [route.path])
const displayName = computed(() => auth.user?.displayName || auth.user?.username || '')
const roleLabel = computed(() => auth.user?.role === 'ADMIN' ? '管理员' : '仓库操作员')

function onMenuClick({ key }) {
  router.push(key)
}

async function onLogout() {
  await auth.logout()
  router.push('/login')
}

watch(() => auth.user, (u) => {
  if (!u && route.path !== '/login') router.replace('/login')
}, { immediate: true })
</script>

<template>
  <a-layout class="app-shell">
    <Sider width="236" class="app-sider">
      <div class="brand">
        <SwapOutlined />
        <span>WMS 管理系统</span>
      </div>
      <a-menu theme="dark" mode="inline" :selected-keys="selectedKeys" :items="menuItems" @click="onMenuClick" />
    </Sider>
    <a-layout>
      <Header class="app-header">
        <Typography.Text type="secondary">仓库进销存管理</Typography.Text>
        <Space>
          <Typography.Text>{{ displayName }} · {{ roleLabel }}</Typography.Text>
          <Button type="link" @click="onLogout">退出</Button>
        </Space>
      </Header>
      <Content class="app-content">
        <router-view />
      </Content>
    </a-layout>
  </a-layout>
</template>
