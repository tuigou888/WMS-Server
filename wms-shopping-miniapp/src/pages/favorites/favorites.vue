<template>
  <view class="page">
    <view v-if="list.length" class="fav-grid">
      <view class="fav-card" v-for="p in list" :key="p.id">
        <image class="f-img" :src="p.mainImage || ''" mode="aspectFill" @tap="goProduct(p.id)" />
        <text class="f-title">{{ p.title }}</text>
        <view class="f-foot">
          <text class="f-price">¥{{ money(p.salePrice) }}</text>
          <text class="f-add" @tap="addCart(p)">加入购物车</text>
        </view>
      </view>
    </view>
    <view v-else class="empty"><text>暂无收藏（可长按商品收藏，demo 暂无收藏接口）</text></view>
  </view>
</template>

<script>
import { products } from '@/api/market.js'
import { useCartStore } from '@/store/cart.js'

export default {
  data() { return { list: [] } },
  onShow() {
    products.list({ pageSize: 20 }).then(res => { this.list = (res && res.records) || [] }).catch(() => {})
  },
  methods: {
    money(v) { return Number(v || 0).toFixed(2) },
    goProduct(id) { uni.navigateTo({ url: `/pages/product/product?id=${id}` }) },
    async addCart(p) { try { await useCartStore().add(p.id, 1); uni.showToast({ title: '已加入', icon: 'success' }) } catch (e) { uni.showToast({ title: (e && e.message) || '失败', icon: 'none' }) } },
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
.f-add { font-size: 22rpx; color: #1677ff; }
.empty { padding: 100rpx; text-align: center; color: #999; }
</style>
