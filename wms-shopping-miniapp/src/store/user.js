import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUser, setUser, setToken, clearAuth } from '@/api/request.js'

export const useUserStore = defineStore('user', () => {
  const user = ref(getUser() || null)
  const isLoggedIn = computed(() => !!user.value)

  function restore() { user.value = getUser() || null }

  function login(userData, token) {
    // token 单独存 wms_token，避免随 user 对象重复落一份到 wms_user
    const safe = { ...(userData || {}) }
    delete safe.token
    user.value = safe
    setUser(safe)
    setToken(token)
  }

  function setUserInfo(patches) {
    if (!user.value) return
    const merged = { ...user.value, ...patches }
    delete merged.token
    user.value = merged
    setUser(merged)
  }

  function logout() {
    user.value = null
    clearAuth()
  }

  return { user, isLoggedIn, restore, login, setUserInfo, logout }
})
