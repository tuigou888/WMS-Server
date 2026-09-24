<template>
  <view class="page">
    <view v-if="list.length" class="fav-grid">
      <view class="fav-card" v-for="p in list" :key="p.id">
        <image class="f-img" :src="p.mainImage || ''" mode="aspectFill" @tap="goProduct(p.id)" />
        <text class="f-title">{{ p.title }}</text>
        <view class="f-foot">
          <text class="f-price">¥{{ money(p.salePrice) }}</text>
          <view class="f-actions">
            <text class="f-add" @tap="addCart(p)">加入购物车</text>
            <text class="f-del" @tap="removeFav(p)">取消收藏</text>
          </view>
        </view>
      </view>
    </view>
    <view v-else class="empty"><text>暂无收藏，去商品详情页点收藏吧</text></view>
  </view>
</template>

<script>
import { favorites } from '@/api/market.js'
import { useCartStore } from '@/store/cart.js'
import { formatPrice as money } from '@/utils/format.js'

export default {
  data() { return { list: [] } },
  onShow() { this.load() },
  methods: {
    money,
    async load() {
      try {
        const res = await favorites.list({ page: 1, pageSize: 50 })
        this.list = (res && res.records) || []
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
      }
    },
    goProduct(id) { uni.navigateTo({ url: `/pages/product/product?id=${id}` }) },
    async addCart(p) {
      try { await useCartStore().add(p.id, 1); uni.showToast({ title: '已加入', icon: 'success' }) }
      catch (e) { uni.showToast({ title: (e && e.message) || '失败', icon: 'none' }) }
    },
    async removeFav(p) {
      try {
        await favorites.toggle(p.id)
        uni.showToast({ title: '已取消收藏', icon: 'none' })
        this.load()
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '失败', icon: 'none' })
      }
    },
  },
}
</script>

<style scoped>
.fav-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; padding: 20rpx; }
.fav-card { background: #fff; border-radius: 12rpx; padding: 20rpx; }
.f-img { width: 100%; height: 260rpx; border-radius: 8rpx; background: #f0f0f0; }
.f-title { font-size: 26rpx; color: #333; margin: 12rpx 0; display: block; }
.f-foot { display: flex; justify-content: space-between; align-items: center; }
.f-price { font-size: 30rpx; color: #ff4d4f; font-weight: 700; }
.f-actions { display: flex; flex-direction: column; align-items: flex-end; gap: 8rpx; }
.f-add { font-size: 22rpx; color: #1677ff; }
.f-del { font-size: 22rpx; color: #999; }
.empty { padding: 100rpx; text-align: center; color: #999; }
</style>
