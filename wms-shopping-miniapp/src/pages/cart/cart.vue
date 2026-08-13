<template>
  <view class="page">
    <view v-if="items.length" class="cart-list">
      <view class="cart-item" v-for="c in items" :key="c.id">
        <view class="item-left" @tap="goProduct(c.product.id)">
          <image class="item-img" :src="c.product.mainImage || defaultImg" mode="aspectFill" />
          <view class="item-info">
            <text class="item-title">{{ c.product.title }}</text>
            <text class="item-meta">¥{{ money(c.snapshotPrice) }}</text>
          </view>
        </view>
        <view class="item-right">
          <view class="qty-control">
            <button class="qty-btn" @tap="minus(c)">-</button>
            <text class="qty-val">{{ c.quantity }}</text>
            <button class="qty-btn" @tap="plus(c)">+</button>
          </view>
          <text class="item-subtotal">¥{{ money(c.subtotal) }}</text>
          <text class="delete-btn" @tap="remove(c)">删除</text>
        </view>
      </view>
    </view>
    <view v-else class="empty">
      <text>购物车是空的</text>
    </view>

    <view class="bottom-bar" v-if="items.length">
      <view class="total">
        <text class="total-label">合计：</text>
        <text class="total-price">¥{{ money(total) }}</text>
      </view>
      <button class="btn-primary" @tap="goCheckout">去结算</button>
    </view>
  </view>
</template>

<script>
import { useCartStore } from '@/store/cart.js'

export default {
  data() { return { defaultImg: '', items: [], total: 0 } },
  onShow() { this.$store = useCartStore(); this.load() },
  methods: {
    money(v) { return Number(v || 0).toFixed(2) },
    async load() {
      await this.$store.load()
      this.items = this.$store.items || []
      this.total = this.$store.total || 0
    },
    goProduct(id) { uni.navigateTo({ url: `/pages/product/product?id=${id}` }) },
    async minus(c) { if (c.quantity <= 1) return; await this.$store.update(c.id, c.quantity - 1); this.sync() },
    async plus(c) { await this.$store.update(c.id, c.quantity + 1); this.sync() },
    async remove(c) { uni.showModal({ title: '删除', content: '确认从购物车移除？', success: (b) => { if (b) this.$store.remove([c.id]).then(() => this.sync()) } }) },
    sync() { this.items = this.$store.items || []; this.total = this.$store.total || 0 },
    goCheckout() { uni.navigateTo({ url: '/pages/checkout/checkout' }) },
  },
}
</script>

<style scoped>
.page { padding: 40rpx 20rpx; }
.cart-item { display: flex; justify-content: space-between; padding: 24rpx 20rpx; background: #fff; border-radius: 16rpx; margin-bottom: 20rpx; }
.item-left { display: flex; flex: 1; }
.item-img { width: 160rpx; height: 160rpx; border-radius: 12rpx; background: #f0f0f0; margin-right: 20rpx; }
.item-info { display: flex; flex-direction: column; justify-content: center; }
.item-title { font-size: 28rpx; color: #333; margin-bottom: 8rpx; }
.item-meta { font-size: 24rpx; color: #ff4d4f; }
.item-right { display: flex; flex-direction: column; align-items: flex-end; }
.qty-control { display: flex; align-items: center; margin-bottom: 8rpx; }
.qty-btn { width: 56rpx; height: 56rpx; background: #f5f5f5; border-radius: 8rpx; font-size: 32rpx; }
.qty-val { margin: 0 16rpx; font-size: 28rpx; min-width: 40rpx; text-align: center; }
.delete-btn { font-size: 24rpx; color: #999; margin-top: 8rpx; }
.bottom-bar { position: fixed; bottom: 0; left: 0; right: 0; background: #fff; padding: 20rpx 40rpx; display: flex; align-items: center; justify-content: space-between; box-shadow: 0 -2rpx 6rpx rgba(0,0,0,0.06); }
.total-label { font-size: 26rpx; color: #333; }
.total-price { font-size: 36rpx; color: #ff4d4f; font-weight: 700; }
.btn-primary { background: #1677ff; color: #fff; border-radius: 40rpx; padding: 20rpx 40rpx; font-size: 30rpx; }
.empty { padding: 80rpx 40rpx; text-align: center; color: #999; }
</style>
