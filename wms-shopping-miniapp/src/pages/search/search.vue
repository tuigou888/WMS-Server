<template>
  <view class="page">
    <view class="search-bar">
      <input class="input" v-model="keyword" placeholder="搜索商品名称 / 型号 / 品牌" confirm-type="search" @confirm="search" />
      <button class="btn-primary search-btn" @tap="search">搜索</button>
    </view>
    <view class="search-result">
      <view v-for="p in results" :key="p.id" class="result-item" @tap="goProduct(p.id)">
        <image class="r-img" :src="p.mainImage || ''" mode="aspectFill" />
        <view class="r-info">
          <text class="r-title">{{ p.title }}</text>
          <text class="r-spec">{{ p.specs || p.brand || '' }}</text>
          <text class="r-price">¥{{ money(p.salePrice) }}</text>
        </view>
      </view>
      <view v-if="!results.length && searched" class="empty"><text>未找到相关商品</text></view>
    </view>
  </view>
</template>

<script>
import { products } from '@/api/market.js'

export default {
  data() { return { keyword: '', results: [], searched: false } },
  methods: {
    money(v) { return Number(v || 0).toFixed(2) },
    async search() {
      if (!this.keyword) return
      try {
        const res = await products.list({ keyword: this.keyword })
        this.results = (res && res.records) || []
        this.searched = true
      } catch (e) { uni.showToast({ title: (e && e.message) || '搜索失败', icon: 'none' }) }
    },
    goProduct(id) { uni.navigateTo({ url: `/pages/product/product?id=${id}` }) },
  },
}
</script>

<style scoped>
.page { padding: 24rpx; }
.search-bar { display: flex; gap: 20rpx; margin-bottom: 24rpx; }
.search-btn { border-radius: 8rpx; padding: 0 32rpx; font-size: 28rpx; }
.result-item { display: flex; padding: 20rpx; background: #fff; border-radius: 12rpx; margin-bottom: 20rpx; }
.r-img { width: 140rpx; height: 140rpx; border-radius: 8rpx; background: #f0f0f0; margin-right: 20rpx; }
.r-info { flex: 1; display: flex; flex-direction: column; justify-content: center; }
.r-title { font-size: 28rpx; color: #333; }
.r-spec { font-size: 22rpx; color: #999; margin-top: 8rpx; }
.r-price { font-size: 32rpx; color: #ff4d4f; font-weight: 700; margin-top: 8rpx; }
.empty { padding: 80rpx; text-align: center; color: #999; }
</style>
