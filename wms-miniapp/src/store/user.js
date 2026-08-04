import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, clearToken, getUser, setUser, getWarehouseId, setWarehouseId } from '@/api/request.js'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const user = ref(getUser() || null)
  const warehouseId = ref(getWarehouseId() || null)
  const warehouses = ref([])

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const permissions = computed(() => user.value?.permissions || [])

  const hasPerm = (perm) => permissions.value.includes(perm)

  function login(userData, tokenValue) {
    user.value = userData
    token.value = tokenValue
    setUser(userData)
    setToken(tokenValue)
  }

  function logout() {
    user.value = null
    token.value = ''
    warehouseId.value = null
    clearToken()
  }

  function setWarehouse(id) {
    warehouseId.value = id
    setWarehouseId(id)
  }

  function setWarehouses(list) {
    warehouses.value = list
    if (!warehouseId.value && list.length > 0) {
      setWarehouse(list[0].id)
    }
  }

  function updateUser(updates) {
    if (user.value) {
      user.value = { ...user.value, ...updates }
      setUser(user.value)
    }
  }

  return {
    token,
    user,
    warehouseId,
    warehouses,
    isLoggedIn,
    isAdmin,
    permissions,
    hasPerm,
    login,
    logout,
    setWarehouse,
    setWarehouses,
    updateUser,
  }
})