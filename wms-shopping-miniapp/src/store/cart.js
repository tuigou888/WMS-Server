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
      const res = await cartApi.list({ pricing: 'effective' })
      items.value = res.items || []
      total.value = res.total || 0
      count.value = res.count || 0
    } catch (e) {
      // 失败重置为空态，避免残留上一账号/上一次成功加载的脏数据
      items.value = []
      total.value = 0
      count.value = 0
      throw e
    } finally {
      loading.value = false
    }
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

  return { items, total, count, loading, load, add, update, remove, clear }
})
