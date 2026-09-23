<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button, Layout, Menu } from 'ant-design-vue'
import { MenuUnfoldOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '../stores/auth'
import { buildMenu } from '../utils/menu'
import { roleLabel as formatRoleLabel } from '../utils/labels'

const { Sider, Header, Content } = Layout
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const mobileMenuOpen = ref(false)
const isMobile = ref(false)
let mediaQuery
let syncViewport

const menuItems = computed(() => buildMenu(auth.user))
const selectedKeys = computed(() => [route.path])
const displayName = computed(() => auth.user?.displayName || auth.user?.username || '')
const roleLabel = computed(() => formatRoleLabel(auth.user?.role))
const currentTitle = computed(() => route.meta.title || '仓储运营')
const userInitial = computed(() => displayName.value.slice(0, 1).toUpperCase() || 'W')

function onMenuClick({ key }) {
  mobileMenuOpen.value = false
  router.push(key)
}

async function onLogout() {
  await auth.logout()
  router.push('/login')
}

watch(() => auth.user, (u) => {
  if (!u && route.path !== '/login') router.replace('/login')
}, { immediate: true })

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 767px)')
  syncViewport = () => { isMobile.value = mediaQuery.matches }
  syncViewport()
  mediaQuery.addEventListener?.('change', syncViewport)
})

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener?.('change', syncViewport)
})
</script>

<template>
  <a-layout class="app-shell">
    <Sider v-model:collapsed="collapsed" :width="236" :collapsed-width="isMobile ? 0 : 64" breakpoint="lg" class="app-sider">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true"><span /></span>
        <span v-if="!collapsed">WMS 管理系统</span>
      </div>
      <a-menu theme="dark" mode="inline" :selected-keys="selectedKeys" :items="menuItems" @click="onMenuClick" />
    </Sider>
    <a-layout>
      <Header class="app-header">
        <div class="header-left">
          <Button v-if="isMobile" type="text" class="header-menu-button" aria-label="打开导航" @click="mobileMenuOpen = true">
            <MenuUnfoldOutlined />
          </Button>
          <div class="header-context">
            <span class="header-page-title">{{ currentTitle }}</span>
          </div>
        </div>
        <div class="header-right">
          <div class="header-user" :title="`${displayName} · ${roleLabel}`">
            <span class="header-avatar" aria-hidden="true">{{ userInitial }}</span>
            <span class="header-user-copy">{{ displayName }} · {{ roleLabel }}</span>
          </div>
          <Button type="text" @click="onLogout">退出</Button>
        </div>
      </Header>
      <Content class="app-content">
        <router-view />
      </Content>
    </a-layout>
    <a-drawer v-model:open="mobileMenuOpen" placement="left" :width="236" :closable="false" class="mobile-nav-drawer" title="">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true"><span /></span>
        <span>WMS 管理系统</span>
      </div>
      <Menu theme="dark" mode="inline" :selected-keys="selectedKeys" :items="menuItems" @click="onMenuClick" />
    </a-drawer>
  </a-layout>
</template>
