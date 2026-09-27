<template>
  <view class="page">
    <view v-if="categories.length" class="cat-grid">
      <view v-for="(c, i) in categories" :key="c.id" class="cat-card" @tap="openCategory(c.id, c.name)">
        <view class="cat-icon-wrap"><text class="cat-icon">{{ iconFor(i) }}</text></view>
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

// 分类占位图标：按顺序循环取用，避免后端暂无图标数据时卡片空白
const CATEGORY_ICONS = ['🔧', '⚙️', '🛠️', '🔩', '🧰', '⛏️', '🪛', '🔨', '📏']

export default {
  data() {
    return { categories: [] }
  },
  onLoad() {
    products.categories().then(c => { this.categories = c })
  },
  methods: {
    iconFor(i) { return CATEGORY_ICONS[i % CATEGORY_ICONS.length] },
    openCategory(id, name) {
      uni.navigateTo({ url: `/pages/search/search?categoryId=${id}&name=${encodeURIComponent(name)}` })
    },
  },
}
</script>

<style scoped>
.page { padding: 40rpx 20rpx; }
.cat-grid { display: flex; flex-wrap: wrap; justify-content: space-between; }
.cat-card { width: 31%; margin-bottom: 20rpx; padding: 30rpx 0; text-align: center; background: #fff; border-radius: 16rpx; box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.05); }
.cat-icon-wrap { height: 80rpx; display: flex; align-items: center; justify-content: center; }
.cat-icon { font-size: 56rpx; }
.cat-name { font-size: 26rpx; color: #333; display: block; margin-top: 12rpx; }
.empty { padding: 80rpx 20rpx; text-align: center; color: #999; font-size: 28rpx; }
</style>
