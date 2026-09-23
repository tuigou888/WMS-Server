<template>
  <view class="page">
    <view class="header">
      <button class="back-btn" @tap="back()">←</button>
      <text class="title">商品分类</text>
    </view>

    <view v-if="categories.length" class="cat-grid">
      <view v-for="c in categories" :key="c.id" class="cat-card" @tap="openCategory(c.id, c.name)">
        <text class="cat-name">{{ c.name }}</text>
      </view>
    </view>

    <view v-else class="empty">
      <text>暂无分类数据</text>
    </view>
  </view>
</template>

<script>
import { products } from '@/api/market.js'

export default {
  data() {
    return { categories: [] }
  },
  onLoad() {
    products.categories().then(c => { this.categories = c })
  },
  methods: {
    back() { uni.navigateBack() },
    openCategory(id, name) {
      uni.navigateTo({ url: `/pages/search/search?categoryId=${id}&name=${encodeURIComponent(name)}` })
    },
  },
}
</script>

<style scoped>
.page { padding: 40rpx 0; }
.header { padding: 30rpx 30rpx; display: flex; align-items: center; }
.back-btn { background: none; border: none; font-size: 36rpx; color: #666; }
.title { font-size: 36rpx; font-weight: 700; margin-left: 20rpx; }
.cat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20rpx; margin-top: 20rpx; }
.cat-card { padding: 30rpx; text-align: center; background: #fff; border-radius: 16rpx; }
.cat-icon { width: 80rpx; height: 80rpx; margin-bottom: 12rpx; }
.cat-name { font-size: 28rpx; color: #333; display: block; margin-bottom: 8rpx; }
.cat-count { font-size: 22rpx; color: #999; }
</style>
