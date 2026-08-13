<template>
  <view class="page">
    <!-- 收货地址 -->
    <view class="card" @tap="chooseAddress">
      <block v-if="address">
        <view class="flex justify-between">
          <text class="addr-name">{{ address.name }} <text class="addr-phone">{{ address.phone }}</text></text>
          <text class="text-muted">默认</text>
        </view>
        <text class="addr-detail">{{ address.address }}</text>
        <view class="min-tip">点击可更换地址</view>
      </block>
      <block v-else>
        <view class="addr-empty">添加收货地址</view>
      </block>
    </view>

    <!-- 收货仓库 -->
    <view class="card">
      <view class="section-label">发货仓库</view>
      <picker :range="warehouseNames" range-key="name" @change="onWarehouse">
        <view class="picker-val">{{ warehouse?.name || '请选择仓库' }} <text class="text-muted">▾</text></view>
      </picker>
    </view>

    <!-- 商品清单 -->
    <view class="card">
      <view class="section-label">订单商品</view>
      <view class="order-item" v-for="c in items" :key="c.id">
        <image class="oi-img" :src="c.product.mainImage || ''" mode="aspectFill" />
        <view class="oi-info">
          <text class="oi-title">{{ c.product.title }}</text>
          <text class="oi-meta">¥{{ money(c.snapshotPrice) }} × {{ c.quantity }}</text>
        </view>
        <text class="oi-sub">¥{{ money(c.subtotal) }}</text>
      </view>
    </view>

    <!-- 支付方式 -->
    <view class="card">
      <view class="section-label">支付方式</view>
      <view class="pay-options">
        <view v-for="p in payTypes" :key="p.value" :class="['pay-opt', payType===p.value&&'active']" @tap="payType=p.value">
          <text>{{ p.label }}</text>
        </view>
      </view>
    </view>

    <!-- 备注 -->
    <view class="card">
      <view class="section-label">订单备注</view>
      <input class="input" v-model="remark" placeholder="选填，备注给商家" />
    </view>

    <view class="bottom-bar">
      <view class="total">
        <text class="total-label">应付：</text>
        <text class="total-price">¥{{ money(total) }}</text>
      </view>
      <button class="btn-primary" :disabled="submitting" @tap="submit">{{ submitting ? '提交中...' : '提交订单' }}</button>
    </view>
  </view>
</template>

<script>
import { cart as cartApi, customers, orders, products } from '@/api/market.js'
import { useCartStore } from '@/store/cart.js'
import { useUserStore } from '@/store/user.js'

export default {
  data() {
    return { items: [], total: 0, address: null, addressList: [], warehouses: [], warehouseId: null, payType: 'PAY_ONLINE', remark: '', submitting: false }
  },
  computed: {
    warehouse() { return this.warehouses.find(w => w.id === this.warehouseId) },
    warehouseNames() { return this.warehouses.map(w => w.name) },
    payTypes() { return [ { value: 'PAY_ONLINE', label: '在线支付(模拟)' }, { value: 'CASH_ON_DELIVERY', label: '货到付款' } ] },
  },
  async onShow() {
    await useCartStore().load()
    this.items = useCartStore().items || []
    this.total = useCartStore().total || 0
    await this.loadAddresses()
    await this.loadWarehouses()
    // 从地址选择页回填选中地址（优先），否则用默认地址兜底
    const picked = uni.getStorageSync('checkout_address')
    if (picked && picked.id) {
      this.address = picked
      uni.removeStorageSync('checkout_address')
    } else if (!this.address && this.addressList.length) {
      this.address = this.addressList.find(a => a.defaultFlag) || this.addressList[0]
    }
  },
  methods: {
    money(v) { return Number(v || 0).toFixed(2) },
    async loadAddresses() {
      try { this.addressList = await customers.list() } catch (e) {}
    },
    async loadWarehouses() {
      try { this.warehouses = await products.warehouses() } catch (e) {}
    },
    chooseAddress() { uni.navigateTo({ url: '/pages/address/address?select=1' }) },
    onWarehouse(e) {
      const idx = Number(e.detail.value)
      this.warehouseId = this.warehouses[idx] && this.warehouses[idx].id
    },
    async submit() {
      if (!this.address) { uni.showToast({ title: '请选择收货地址', icon: 'none' }); return }
      if (!this.warehouseId) { uni.showToast({ title: '请选择发货仓库', icon: 'none' }); return }
      if (!this.items.length) { uni.showToast({ title: '购物车为空', icon: 'none' }); return }
      this.submitting = true
      try {
        const res = await orders.create({
          customerId: this.address.id,
          warehouseId: this.warehouseId,
          payType: this.payType,
          remark: this.remark,
        })
        await useCartStore().clear()
        uni.showToast({ title: '下单成功', icon: 'success' })
        setTimeout(() => uni.redirectTo({ url: `/pages/order-detail/order-detail?id=${res.id}` }), 600)
      } catch (e) {
        uni.showToast({ title: (e && e.message) || '下单失败', icon: 'none' })
      } finally { this.submitting = false }
    },
  },
}
</script>

<style scoped>
.page { padding: 20rpx 20rpx 140rpx; }
.card { margin-bottom: 20rpx; }
.section-label { font-size: 28rpx; font-weight: 600; color: #333; margin-bottom: 16rpx; }
.addr-name { font-size: 32rpx; font-weight: 600; }
.addr-phone { font-weight: 400; color: #666; font-size: 28rpx; }
.addr-detail { font-size: 28rpx; color: #333; margin-top: 8rpx; display: block; }
.min-tip { font-size: 22rpx; color: #999; margin-top: 8rpx; }
.addr-empty { color: #1677ff; font-size: 28rpx; text-align: center; padding: 20rpx; }
.picker-val { font-size: 28rpx; padding: 16rpx 0; }
.order-item { display: flex; align-items: center; margin-bottom: 20rpx; }
.oi-img { width: 100rpx; height: 100rpx; border-radius: 8rpx; background: #f0f0f0; margin-right: 16rpx; }
.oi-info { flex: 1; display: flex; flex-direction: column; }
.oi-title { font-size: 28rpx; color: #333; margin-bottom: 8rpx; }
.oi-meta { font-size: 22rpx; color: #999; }
.oi-sub { font-size: 28rpx; color: #333; font-weight: 600; }
.pay-options { display: flex; flex-wrap: wrap; gap: 16rpx; }
.pay-opt { border: 1rpx solid #d9d9d9; border-radius: 8rpx; padding: 16rpx 28rpx; font-size: 26rpx; color: #666; }
.pay-opt.active { border-color: #1677ff; color: #1677ff; background: #e6f7ff; }
.bottom-bar { position: fixed; bottom: 0; left: 0; right: 0; background: #fff; padding: 20rpx 40rpx; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 -2rpx 6rpx rgba(0,0,0,0.06); }
.total-label { font-size: 26rpx; }
.total-price { font-size: 36rpx; color: #ff4d4f; font-weight: 700; }
.btn-primary { border-radius: 40rpx; padding: 20rpx 40rpx; }
</style>
