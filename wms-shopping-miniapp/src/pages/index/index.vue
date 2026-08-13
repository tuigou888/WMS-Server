<template>
  <view class="page">
    <!-- 顶部欢迎条 -->
    <view class="hero">
      <view>
        <text class="hero-title">WMS 机械商城</text>
        <text class="hero-sub">在线选购机械零配件 · 一键下单 · 物流追踪</text>
      </view>
    </view>

    <!-- 分类入口 -->
    <view class="cat-strip" v-if="categories.length">
      <view class="cat-chip" v-for="c in categories" :key="c.id" @tap="goCategory(c.id, c.name)">
        <text>{{ c.name }}</text>
      </view>
    </view>

    <!-- 推荐商品 -->
    <view class="section-title">热卖推荐</view>
    <view v-if="products.length" class="grid">
      <view v-for="p in products" :key="p.id" class="p-card" @tap="goProduct(p.id)">
        <image class="p-img" :src="p.mainImage || defaultImg" mode="aspectFill" />
        <view class="p-info">
          <text class="p-name">{{ p.title }}</text>
          <view class="p-bottom">
            <text class="p-price">¥{{ money(p.salePrice) }}</text>
            <text class="p-sold">已售{{ p.salesCount || 0 }}</text>
          </view>
          <button class="add-btn" :disabled="p.status!=='SHELF_ON'" @tap.stop="addCart(p)">加入购物车</button>
        </view>
      </view>
    </view>
    <view v-if="!loading && !products.length" class="empty">
      <text>暂无在售商品，请先在后台商品管理中上架</text>
    </view>

    <view class="foot-tip">—— 下拉刷新 / 上拉加载更多 ——</view>
  </view>
</template>

<script>
import { products as productApi } from '@/api/market.js'
import { useCartStore } from '@/store/cart.js'

export default {
  data() {
    return {
      loading: false,
      products: [],
      categories: [],
      page: 1,
      pageSize: 12,
      total: 0,
      hasMore: true,
      defaultImg: '',
    }
  },
  onLoad() { this.loadCategories(); this.load(); },
  onShow() { /* 回到首页刷新购物车徽标由 tab 自动处理 */ },
  onPullDownRefresh() { this.load().finally(() => uni.stopPullDownRefresh()); },
  onReachBottom() { if (this.hasMore) this.loadMore(); },
  methods: {
    money(v) { return Number(v || 0).toFixed(2) },
    async loadCategories() {
      try { this.categories = await productApi.categories() } catch (e) {}
    },
    async load() {
      this.loading = true
      try {
        const res = await productApi.list({ page: 1, pageSize: this.pageSize })
        this.products = res.records || []
        this.total = res.total || 0
        this.page = 1
        this.hasMore = this.products.length < this.total
      } catch (e) { uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' }) }
      finally { this.loading = false }
    },
    async loadMore() {
      if (!this.hasMore) return
      const next = this.page + 1
      const res = await productApi.list({ page: next, pageSize: this.pageSize })
      const more = res.records || []
      this.products = this.products.concat(more)
      this.page = next
      this.hasMore = this.products.length < (res.total || 0)
    },
    goProduct(id) { uni.navigateTo({ url: `/pages/product/product?id=${id}` }) },
    goCategory(id, name) { uni.switchTab({ url: '/pages/category/category' }).catch(() => {}) },
    async addCart(p) {
      if (p.status !== 'SHELF_ON') { uni.showToast({ title: '已下架', icon: 'none' }); return }
      try {
        await useCartStore().add(p.id, 1)
        uni.showToast({ title: '已加入购物车', icon: 'success' })
      } catch (e) { uni.showToast({ title: (e && e.message) || '添加失败', icon: 'none' }) }
    },
  },
}
</script>

<style scoped>
.page { padding: 20rpx 20rpx 60rpx; }
.hero { background: linear-gradient(135deg,#1677ff,#4096ff); border-radius: 16rpx; padding: 40rpx 32rpx; color: #fff; margin-bottom: 20rpx; }
.hero-title { font-size: 40rpx; font-weight: 700; display: block; }
.hero-sub { font-size: 24rpx; opacity: 0.85; display: block; margin-top: 8rpx; }
.cat-strip { display: flex; flex-wrap: wrap; gap: 16rpx; margin-bottom: 20rpx; }
.cat-chip { background: #fff; padding: 16rpx 28rpx; border-radius: 40rpx; font-size: 26rpx; color: #333; box-shadow: 0 1rpx 3rpx rgba(0,0,0,0.06); }
.section-title { font-size: 32rpx; font-weight: 700; margin: 20rpx 0 16rpx; color: #333; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; }
.p-card { background: #fff; border-radius: 16rpx; overflow: hidden; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.06); }
.p-img { width: 100%; height: 320rpx; background: #f0f0f0; }
.p-info { padding: 20rpx; }
.p-name { font-size: 28rpx; color: #333; display: block; min-height: 80rpx; line-height: 40rpx; }
.p-bottom { display: flex; justify-content: space-between; align-items: baseline; margin-top: 12rpx; }
.p-price { color: #ff4d4f; font-size: 32rpx; font-weight: 700; }
.p-sold { color: #999; font-size: 22rpx; }
.add-btn { margin-top: 16rpx; background: #1677ff; color: #fff; font-size: 26rpx; border-radius: 8rpx; line-height: 2.2; }
.add-btn[disabled] { background: #b3d1ff; }
.empty { padding: 80rpx 20rpx; text-align: center; color: #999; font-size: 28rpx; }
.foot-tip { text-align: center; color: #ccc; font-size: 24rpx; margin: 40rpx 0; }
</style>
