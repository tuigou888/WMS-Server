import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUser, setUser, setToken, clearAuth } from '@/api/request.js'

export const useUserStore = defineStore('user', () => {
  const user = ref(getUser() || null)
  const isLoggedIn = computed(() => !!user.value)

  function restore() { user.value = getUser() || null }

  function login(userData, token) {
    user.value = userData
    setUser(userData)
    setToken(token)
  }

  function setUserInfo(patches) {
    if (!user.value) return
    user.value = { ...user.value, ...patches }
    setUser(user.value)
  }

  function logout() {
    user.value = null
    clearAuth()
  }

  return { user, isLoggedIn, restore, login, setUserInfo, logout }
})
