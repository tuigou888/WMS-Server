<template>
  <view class="page">
    <view v-if="!product" class="empty"><text>商品不存在</text></view>
    <view v-else class="product-detail">
      <image class="detail-img" :src="product.mainImage || defaultImg" mode="aspectFill" />
      <view class="detail-body">
        <text class="product-sku-title">{{ product.title }}</text>
        <view class="flex mb-10">
          <text class="badge" :style="{ background: badgeColors[product.status] }">
            {{ statusText }}
          </text>
        </view>
        <view class="detail-info">
          <view class="row">
            <text class="label">分类：</text><text class="value">{{ product.categoryName || 'N/A' }}</text>
          </view>
          <view class="row">
            <text class="label">库存：</text><text class="value">{{ product.availableStock }} 件</text>
          </view>
          <view class="row">
            <text class="label">单位：</text><text class="value">{{ product.unit }}</text>
          </view>
          <view class="row">
            <text class="label">规格：</text><text class="value">{{ product.specs || 'N/A' }}</text>
          </view>
        </view>

        <!-- 价格 -->
        <view class="price-section">
          <text class="price-symbol">¥</text>
          <text class="price">{{ formatPrice(product.salePrice) }}</text>
          <view v-if="product.marketPrice" class="price-tag">
            原价 <text class="price-mid">¥{{ formatPrice(product.marketPrice) }}</text>
          </view>
        </view>

        <view class="buy-section">
          <text class="stock-tip">
            库存 {{ product.availableStock }} 件
            <text>{{ getStockText(product) }}</text>
          </text>
          <view class="flex">
            <button class="btn-primary" @tap="doAdd" :disabled="addingDisabled">加入购物车</button>
            <button class="btn-outline" @tap="doBuy" :disabled="addingDisabled">直接购买</button>
          </view>
        </view>
      </view>

      <view class="detail-actions">
        <view class="d-item">
          <text class="d-label">商品图片</text>
          <text class="d-text">已保存</text>
        </view>
        <view class="d-item">
          <text class="d-label">推荐搭配</text>
          <text class="d-text">暂无推荐</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { products } from '@/api/market.js'
import { useCartStore } from '@/store/cart.js'
import { ref } from 'vue'

export default {
    data() {
      return { product: null, defaultImg: '', isAdding: false }
    },
  onLoad() {
    const id = this.$route.params.id
    if (id) products.detail(id).then(p => { this.product = p })
  },
  computed: {
    productStatus() {
      return { text: this.product?.status || '', color: 'default' }
    },
    badgeColors() {
      return { SHELF_ON: '#52c41a', SHELF_OFF: '#999', DRAFT: '#666' }
    },
    statusText() {
      const c = this.productStatus.text
      return { SHELF_ON: '在售', SHELF_OFF: '已下架', DRAFT: '草稿' }[c] || c
    },
    addingDisabled() {
      return this.isAdding || !this.product || this.product.status !== 'SHELF_ON' || Number(this.product.availableStock || 0) <= 0
    },
  },
  methods: {
    formatPrice(v) { return Number(v || 0).toFixed(2) },
    async doAdd() {
      if (this.addingDisabled) return
      this.isAdding = true
      try {
        await useCartStore().add(this.product.id, 1)
        uni.showToast({ title: '已加入购物车', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '添加失败', icon: 'none' })
      } finally { this.isAdding = false }
    },
    async doBuy() {
      if (this.addingDisabled) return
      uni.showModal({
        title: '确认下单',
        content: `购买「${this.product.title}」× 1（¥${this.product.salePrice}）`,
        confirmText: '确认购买',
        cancelText: '取消',
        success: (b) => { if (b) this.goCheckout() },
      })
    },
    goCheckout() {
      uni.navigateTo({ url: '/pages/checkout/checkout' })
    },
    getStockText(p) {
      const avail = Number(p.availableStock || 0)
      if (avail <= 0) return '库存不足'
      return '有货'
    },
  },
}
</script>

<style scoped>
.page { padding: 40rpx 20rpx; }
.header { padding: 30rpx 0; display: flex; align-items: center; }
.back-btn { background: none; border: none; font-size: 36rpx; color: #666; padding: 16rpx; }
.title { font-size: 36rpx; font-weight: 700; margin-left: 20rpx; }
.product-detail { padding: 24rpx 0; }
.detail-img { width: 100%; height: 400rpx; background: #f0f0f0; border-radius: 16rpx; }
.detail-body { padding: 0 20rpx; }
.product-sku-title { font-size: 34rpx; font-weight: 700; color: #333; }
.detail-info { margin-top: 16rpx; }
.row { display: flex; padding: 12rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.label { width: 140rpx; color: #999; font-size: 26rpx; }
.value { flex: 1; color: #333; font-size: 26rpx; }
.price-section {
  display: flex; align-items: baseline; gap: 8rpx; padding: 30rpx 0;
  border-top: 1rpx solid #f0f0f0; margin-top: 20rpx;
}
.price-symbol { font-size: 40rpx; color: #ff4d4f; font-weight: 700; }
.price { font-size: 56rpx; color: #333; font-weight: 700; }
.price-tag { margin-left: auto; }
.price-mid { color: #999; text-decoration: line-through; font-size: 28rpx; }
.buy-section { margin-top: 40rpx; }
.stock-tip { font-size: 24rpx; color: #999; margin-bottom: 16rpx; }
.buy-section { margin-top: 20rpx; }
.btn-primary { flex: 1; height: 88rpx; border-radius: 16rpx; font-size: 32rpx; margin-right: 16rpx; }
.btn-outline { border: 1rpx solid #d9d9d9; height: 88rpx; border-radius: 16rpx; font-size: 32rpx; margin-right: 16rpx; }
.stock-tip { font-size: 22rpx; color: #999; margin-bottom: 16rpx; }
.detail-actions { margin-top: 40rpx; padding: 20rpx; background: #fafafa; border-radius: 12rpx; }
.d-item { display: flex; flex-direction: column; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.d-item:last-child { border-bottom: none; }
.d-label { font-size: 24rpx; color: #999; }
.d-text { font-size: 24rpx; color: #666; margin-top: 4rpx; }
.badge { display: inline-block; padding: 4rpx 16rpx; border-radius: 16rpx; font-size: 24rpx; }
.badge-info { background: #e6f7ff; color: #1677ff; }
.badge-success { background: #f6ffed; color: #52c41a; }
</style>
