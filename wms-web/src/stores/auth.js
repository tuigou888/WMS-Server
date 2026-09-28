import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '../api/wms'
import { getJson, getStorage, removeStorage, setJson, setStorage } from '../utils/storage'

export const useAuthStore = defineStore('auth', () => {
  const user = ref(getJson('wms_user'))
  const checking = ref(!!getStorage('wms_token'))

  const isLoggedIn = computed(() => !!user.value && !!getStorage('wms_token'))

  async function fetchMe() {
    try {
      user.value = await api.me()
      setJson('wms_user', user.value)
    } catch {
      // client.js 仅在 401 时清除 token/user 并跳转登录页；网络抖动或服务端 5xx 不应强制登出
      if (!getStorage('wms_token')) user.value = null
    } finally {
      checking.value = false
    }
  }

  async function login(values) {
    const data = await api.login(values)
    setStorage('wms_token', data.token)
    user.value = data
    setJson('wms_user', data)
    return data
  }

  async function logout() {
    try { await api.logout() } finally {
      removeStorage('wms_token')
      removeStorage('wms_user')
      user.value = null
    }
  }

  return { user, checking, isLoggedIn, fetchMe, login, logout }
})
