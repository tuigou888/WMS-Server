<template>
  <view class="page">
    <view class="search-bar">
      <input class="input" :placeholder="categoryName ? `分类：${categoryName}` : '搜索商品名称 / 型号 / 品牌'" v-model="keyword" confirm-type="search" @confirm="search" />
      <button class="btn-primary search-btn" @tap="search">搜索</button>
    </view>
    <view class="search-result">
      <view v-for="p in results" :key="p.id" class="result-item" @tap="goProduct(p.id)">
        <image v-if="p.mainImage" class="r-img" :src="p.mainImage" mode="aspectFill" />
        <view v-else class="r-img img-holder"><text class="img-holder-icon">📦</text></view>
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
import { formatPrice as money } from '@/utils/format.js'

export default {
  data() { return { keyword: '', categoryId: null, categoryName: '', results: [], searched: false, page: 1, total: 0, loading: false, fetchSeq: 0 } },
  onLoad(opt) {
    this.categoryId = (opt && opt.categoryId) || null
    this.categoryName = (opt && opt.name) || ''
    if (this.categoryId) this.search()
  },
  onReachBottom() { this.loadMore() },
  methods: {
    money,
    async search() {
      this.page = 1
      await this.fetch(true)
    },
    async loadMore() {
      if (!this.searched || this.loading || this.results.length >= this.total) return
      await this.fetch(false)
    },
    async fetch(reset) {
      // 请求序号：新搜索覆盖在途请求（原实现直接 return，输入已变却仍显示旧结果）
      const seq = ++this.fetchSeq
      if (reset) { this.results = []; this.page = 1 }
      this.loading = true
      try {
        const params = { page: this.page, pageSize: 20 }
        if (this.keyword) params.keyword = this.keyword
        if (this.categoryId) params.categoryId = this.categoryId
        const res = await products.list(params)
        if (seq !== this.fetchSeq) return
        const rows = (res && res.records) || []
        this.results = reset ? rows : this.results.concat(rows)
        this.total = (res && res.total) || 0
        this.searched = true
      } catch (e) {
        if (seq === this.fetchSeq) uni.showToast({ title: (e && e.message) || '搜索失败', icon: 'none' })
      } finally {
        if (seq === this.fetchSeq) this.loading = false
      }
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
