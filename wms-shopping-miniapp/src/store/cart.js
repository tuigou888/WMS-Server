import { defineStore } from 'pinia'
import { ref } from 'vue'
import { cart as cartApi } from '@/api/market.js'

export const useCartStore = defineStore('cart', () => {
  const items = ref([])
  const total = ref(0)
  const count = ref(0)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const res = await cartApi.list()
      items.value = res.items || []
      total.value = res.total || 0
      count.value = res.count || 0
    } finally {
      loading.value = false
    }
  }

  async function loadCount() {
    try {
      const res = await cartApi.list()
      count.value = res.count || 0
      total.value = res.total || 0
    } catch (e) { /* 未登录忽略 */ }
  }

  async function add(productId, quantity = 1) {
    await cartApi.add({ productId, quantity })
    await load()
  }

  async function update(id, quantity) {
    await cartApi.update(id, { quantity })
    await load()
  }

  async function remove(ids) {
    await cartApi.remove(ids)
    await load()
  }

  async function clear() {
    await cartApi.clear()
    items.value = []
    total.value = 0
    count.value = 0
  }

  return { items, total, count, loading, load, loadCount, add, update, remove, clear }
})
