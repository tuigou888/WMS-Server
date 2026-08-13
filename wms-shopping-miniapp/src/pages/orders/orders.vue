<template>
  <view class="page">
    <!-- 状态筛选 -->
    <view class="tab-bar">
      <view v-for="t in tabs" :key="t.value" :class="['tab', status===t.value&&'active']" @tap="switchTab(t.value)">
        {{ t.label }}
      </view>
    </view>

    <view v-if="list.length" class="order-list">
      <view class="order-card" v-for="o in list" :key="o.id" @tap="goDetail(o)">
        <view class="order-head">
          <text class="order-no">{{ o.orderNo }}</text>
          <text class="order-status" :class="'status-'+o.orderStatus.toLowerCase()">{{ statusText(o.orderStatus) }}</text>
        </view>
        <view class="order-item" v-for="it in o.items" :key="it.id">
          <text class="oi-name">{{ it.itemName }} ×{{ it.quantity }}</text>
          <text class="oi-price">¥{{ money(it.subtotal) }}</text>
        </view>
        <view class="order-foot">
          <text class="order-total">共 {{ o.items.length }} 件，合计 <text class="orange">¥{{ money(o.totalAmount) }}</text></text>
          <view class="actions" v-if="o.orderStatus==='PENDING'">
            <button class="btn-mini" @tap.stop="cancelOrder(o)">取消</button>
            <button class="btn-mini primary" @tap.stop="payOrder(o)">去支付</button>
          </view>
          <view class="actions" v-else-if="o.orderStatus==='SHIPPED'">
            <button class="btn-mini primary" @tap.stop="receiveOrder(o)">确认收货</button>
          </view>
        </view>
      </view>
    </view>
    <view v-else class="empty">
      <text>暂无订单</text>
    </view>

    <view v-if="hasMore" class="load-more">上拉加载更多</view>
  </view>
</template>

<script>
import { orders as orderApi } from '@/api/market.js'
import { useUserStore } from '@/store/user.js'

export default {
  data() {
    return { status: '', list: [], page: 1, pageSize: 10, hasMore: false }
  },
  computed: {
    tabs() {
      return [ { value: '', label: '全部' }, { value: 'PENDING', label: '待付款' }, { value: 'AUDITED', label: '待发货' }, { value: 'SHIPPED', label: '已发货' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' } ]
    },
    statusText() { return (s) => ({ PENDING: '待付款', AUDITED: '待发货', SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消', REJECTED: '已拒绝' }[s] || s) },
  },
  onShow() { this.load(true) },
  onReachBottom() { if (this.hasMore) this.load() },
  methods: {
    money(v) { return Number(v || 0).toFixed(2) },
    switchTab(v) { this.status = v; this.list = []; this.load(true) },
    async load(reset = false) {
      const page = reset ? 1 : this.page + 1
      try {
        const res = await orderApi.list({ page, pageSize: this.pageSize, status: this.status })
        const rows = (res && res.records) || []
        this.list = reset ? rows : this.list.concat(rows)
        this.page = page
        this.hasMore = this.list.length < (res?.total || 0)
      } catch (e) { uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' }) }
    },
    goDetail(o) { uni.navigateTo({ url: `/pages/order-detail/order-detail?id=${o.id}` }) },
    cancelOrder(o) {
      uni.showModal({ title: '取消订单', content: '确认取消该订单？', success: async (b) => {
        if (b) { try { await orderApi.cancel(o.id); uni.showToast({ title: '已取消', icon: 'success' }); this.load(true) } catch (e) { uni.showToast({ title: (e && e.message) || '取消失败', icon: 'none' }) } }
      }})
    },
    async payOrder(o) {
      try { await orderApi.pay(o.id); uni.showToast({ title: '支付成功', icon: 'success' }); this.load(true) }
      catch (e) { uni.showToast({ title: (e && e.message) || '支付失败', icon: 'none' }) }
    },
    async receiveOrder(o) {
      try { await orderApi.receive(o.id); uni.showToast({ title: '已确认收货', icon: 'success' }); this.load(true) }
      catch (e) { uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' }) }
    },
  },
}
</script>

<style scoped>
.page { padding: 20rpx; }
.tab-bar { display: flex; background: #fff; border-radius: 12rpx; margin-bottom: 20rpx; overflow: hidden; flex-wrap: wrap; }
.tab { flex: 1; text-align: center; padding: 24rpx 0; font-size: 26rpx; color: #666; min-width: 100rpx; }
.tab.active { color: #1677ff; font-weight: 600; border-bottom: 4rpx solid #1677ff; }
.order-card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.order-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.order-no { font-size: 26rpx; color: #666; }
.order-status { font-size: 26rpx; font-weight: 600; }
.status-pending, .status-rejected { color: #fa8c16; }
.status-audited { color: #1677ff; }
.status-shipped { color: #52c41a; }
.status-completed { color: #666; }
.status-cancelled { color: #999; }
.order-item { display: flex; justify-content: space-between; padding: 8rpx 0; }
.oi-name { font-size: 26rpx; color: #333; }
.oi-price { font-size: 26rpx; color: #333; }
.order-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 16rpx; }
.order-total { font-size: 24rpx; color: #666; }
.orange { color: #ff4d4f; font-weight: 600; }
.actions { display: flex; gap: 16rpx; }
.btn-mini { background: #fff; border: 1rpx solid #d9d9d9; color: #666; font-size: 24rpx; border-radius: 8rpx; margin: 0; padding: 8rpx 24rpx; line-height: 1.6; }
.btn-mini.primary { border-color: #1677ff; color: #1677ff; }
.empty { padding: 100rpx 40rpx; text-align: center; color: #999; }
.load-more { text-align: center; color: #ccc; padding: 20rpx; font-size: 24rpx; }
</style>
