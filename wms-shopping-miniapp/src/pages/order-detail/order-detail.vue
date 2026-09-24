<template>
  <view class="page">
    <view v-if="!order" class="empty"><text>订单不存在</text></view>
    <view v-else class="detail">
      <!-- 物流/状态头 -->
      <view class="track-card">
        <view class="flex justify-between items-center">
          <text class="track-status">{{ statusText }}</text>
          <text class="track-sub">{{ subText }}</text>
        </view>
        <view class="track-no" v-if="order.logisticsNumber" @tap="copyLogistics">
          运单号：{{ order.logisticsCompany }} {{ order.logisticsNumber }}
          <text class="copy-hint">（点击复制）</text>
        </view>
        <view class="track-ship-time" v-if="order.shippedAt">发货时间：{{ fmtDate(order.shippedAt) }}</view>
      </view>

      <!-- 物流轨迹（操作日志） -->
      <view class="card" v-if="order.logs && order.logs.length">
        <view class="section-label">物流轨迹</view>
        <view class="timeline">
          <view class="tl-item" v-for="(log, idx) in order.logs" :key="log.id">
            <view class="tl-dot" :class="{ active: idx === order.logs.length - 1 }"></view>
            <view class="tl-content">
              <text class="tl-action">{{ logActionText(log.action) }}</text>
              <text class="tl-time">{{ fmtDate(log.createdAt) }}</text>
              <text class="tl-remark" v-if="log.remark">{{ log.remark }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 订单信息 -->
      <view class="card">
        <view class="section-label">收货信息</view>
        <view class="info-row"><text class="k">收货人：</text><text class="v">{{ order.receiverName }} {{ order.receiverPhone }}</text></view>
        <view class="info-row"><text class="k">地址：</text><text class="v">{{ order.receiverAddress }}</text></view>
        <view class="info-row"><text class="k">仓库：</text><text class="v">{{ order.warehouseName }}</text></view>
      </view>

      <!-- 商品 -->
      <view class="card">
        <view class="section-label">商品明细</view>
        <view class="oi" v-for="it in order.items" :key="it.id">
          <view class="flex-1">
            <text class="oi-name">{{ it.itemName }}</text>
            <text class="oi-meta">¥{{ money(it.salePrice) }} × {{ it.quantity }}</text>
          </view>
          <text class="oi-sub">¥{{ money(it.subtotal) }}</text>
        </view>
        <view class="total-row">
          <text class="total-label">合计：</text>
          <text class="total-price">¥{{ money(order.totalAmount) }}</text>
        </view>
      </view>

      <!-- 支付信息 -->
      <view class="card">
        <view class="info-row"><text class="k">订单号：</text><text class="v">{{ order.orderNo }}</text></view>
        <view class="info-row"><text class="k">支付方式：</text><text class="v">{{ payTypeText }}</text></view>
        <view class="info-row"><text class="k">支付状态：</text><text class="v">{{ payStatusText }}</text></view>
        <view class="info-row"><text class="k">下单时间：</text><text class="v">{{ fmtDate(order.createdAt) }}</text></view>
      </view>

      <!-- 操作 -->
      <view class="action-bar">
        <button v-if="order.orderStatus==='PENDING'" class="btn-danger" @tap="cancel">取消订单</button>
        <button v-if="order.orderStatus==='PENDING'" class="btn-primary" @tap="pay">立即支付</button>
        <button v-if="order.orderStatus==='SHIPPED'" class="btn-primary" @tap="receive">确认收货</button>
      </view>
    </view>
  </view>
</template>

<script>
import { orders as orderApi } from '@/api/market.js'
import { formatDateTime, formatPrice as money } from '@/utils/format.js'
import { requestPayment, PaymentCancelled } from '@/utils/pay.js'

export default {
  data() { return { order: null, id: null } },
  computed: {
    statusText() { return this.order ? ({ PENDING: '待付款', AUDITED: '待发货', SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消', REJECTED: '已拒绝' }[this.order.orderStatus] || this.order.orderStatus) : '' },
    subText() {
      if (!this.order) return ''
      if (this.order.orderStatus === 'PENDING') return '请尽快完成支付'
      if (this.order.orderStatus === 'SHIPPED') return '正在运输途中，请留意物流'
      return ''
    },
    payTypeText() { return this.order ? { PAY_ONLINE: '在线支付', CASH_ON_DELIVERY: '货到付款', CREDIT: '挂账' }[this.order.payType] || this.order.payType : '' },
    payStatusText() { return this.order ? { UNPAID: '未支付', PAID: '已支付', REFUNDING: '退款处理中', REFUNDED: '已退款' }[this.order.payStatus] || this.order.payStatus : '' },
  },
  onLoad(opt) {
    this.id = opt.id
    this.load()
  },
  methods: {
    money,
    fmtDate(v) { return formatDateTime(v) },
    async load() {
      try { this.order = await orderApi.detail(this.id) }
      catch (e) { uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' }) }
    },
    cancel() {
      uni.showModal({ title: '取消订单', content: '确认取消该订单？', success: async (b) => { if (b) { try { await orderApi.cancel(this.id); uni.showToast({ title: '已取消', icon: 'success' }); this.load() } catch (e) { uni.showToast({ title: (e && e.message) || '取消失败', icon: 'none' }) } } } })
    },
    async pay() {
      try {
        await requestPayment(this.id)
        uni.showToast({ title: '支付成功', icon: 'success' })
        this.load()
      } catch (e) {
        if (e && e.cancelled) {
          uni.showToast({ title: '已取消支付', icon: 'none' })
        } else {
          uni.showToast({ title: (e && e.message) || '支付失败', icon: 'none' })
        }
      }
    },
    async receive() {
      try { await orderApi.receive(this.id); uni.showToast({ title: '已确认收货', icon: 'success' }); this.load() }
      catch (e) { uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none' }) }
    },
    copyLogistics() {
      if (!this.order || !this.order.logisticsNumber) return
      uni.setClipboardData({
        data: this.order.logisticsNumber,
        success: () => uni.showToast({ title: '运单号已复制', icon: 'success' }),
      })
    },
    logActionText(action) {
      return ({ CREATE: '订单创建', PAY: '支付成功', AUDIT: '订单审核', SHIP: '商家发货', COMPLETE: '确认收货', CANCEL: '订单取消', REFUND: '退款' }[action]) || action
    },
  },
}
</script>

<style scoped>
.page { padding: 20rpx 20rpx; padding-bottom: calc(160rpx + env(safe-area-inset-bottom)); }
.track-card { background: linear-gradient(135deg,#1677ff,#4096ff); color: #fff; border-radius: 16rpx; padding: 32rpx; margin-bottom: 20rpx; }
.track-status { font-size: 40rpx; font-weight: 700; }
.track-sub { font-size: 26rpx; opacity: 0.85; }
.track-no { font-size: 24rpx; opacity: 0.85; margin-top: 16rpx; }
.copy-hint { font-size: 20rpx; opacity: 0.7; }
.track-ship-time { font-size: 22rpx; opacity: 0.75; margin-top: 8rpx; }
.timeline { padding-left: 8rpx; }
.tl-item { display: flex; padding-bottom: 24rpx; position: relative; }
.tl-item:last-child { padding-bottom: 0; }
.tl-item::before { content: ''; position: absolute; left: 7rpx; top: 24rpx; bottom: 0; width: 2rpx; background: #e8e8e8; }
.tl-item:last-child::before { display: none; }
.tl-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #d9d9d9; margin-top: 8rpx; margin-right: 20rpx; flex-shrink: 0; z-index: 1; }
.tl-dot.active { background: #1677ff; }
.tl-content { flex: 1; display: flex; flex-direction: column; }
.tl-action { font-size: 26rpx; color: #333; }
.tl-time { font-size: 22rpx; color: #999; margin-top: 4rpx; }
.tl-remark { font-size: 22rpx; color: #666; margin-top: 4rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.section-label { font-size: 28rpx; font-weight: 600; margin-bottom: 16rpx; }
.info-row { display: flex; padding: 10rpx 0; font-size: 26rpx; }
.k { color: #999; width: 150rpx; }
.v { color: #333; flex: 1; }
.oi { display: flex; align-items: center; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.oi-name { font-size: 28rpx; display: block; color: #333; }
.oi-meta { font-size: 22rpx; color: #999; }
.oi-sub { font-size: 28rpx; color: #333; font-weight: 600; }
.total-row { display: flex; justify-content: flex-end; align-items: baseline; padding-top: 20rpx; }
.total-label { font-size: 26rpx; color: #666; }
.total-price { font-size: 36rpx; color: #ff4d4f; font-weight: 700; }
.action-bar { position: fixed; bottom: 0; left: 0; right: 0; background: #fff; padding: 20rpx 40rpx; padding-bottom: calc(20rpx + env(safe-area-inset-bottom)); display: flex; gap: 20rpx; justify-content: flex-end; box-shadow: 0 -2rpx 6rpx rgba(0,0,0,0.06); }
.empty { padding: 100rpx 40rpx; text-align: center; color: #999; }
</style>
