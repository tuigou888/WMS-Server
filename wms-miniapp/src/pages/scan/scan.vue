<template>
  <view class="scan-page">
    <view class="scan-header">
      <text class="scan-title">扫码操作</text>
      <text class="scan-desc">扫描物品二维码/条形码，快速进入入库/出库/查询</text>
    </view>

    <button class="btn-scan" @tap="scanCode" :disabled="scanning">
      <text v-if="scanning" class="loading"></text>
      <text v-else>📷  打开扫码</text>
    </button>

    <view v-if="lastScan" class="last-scan">
      <text class="label">上次扫码: {{ lastScan }}</text>
    </view>

    <view class="quick-actions">
      <text class="section-title">或选择功能</text>
      <view class="action-grid">
        <navigator url="/pages/stock-in/stock-in" class="action-item" hover-class="action-item-hover">
          <text class="action-icon">📥</text>
          <text>扫码入库</text>
        </navigator>
        <navigator url="/pages/stock-out/stock-out" class="action-item" hover-class="action-item-hover">
          <text class="action-icon">📤</text>
          <text>扫码出库</text>
        </navigator>
        <navigator url="/pages/item-list/item-list" class="action-item" hover-class="action-item-hover">
          <text class="action-icon">🏷️</text>
          <text>物品查询</text>
        </navigator>
        <navigator url="/pages/inventory/inventory" class="action-item" hover-class="action-item-hover">
          <text class="action-icon">📦</text>
          <text>库存查询</text>
        </navigator>
      </view>
    </view>

    <view class="history-section" v-if="scanHistory.length > 0">
      <text class="section-title">扫码历史</text>
      <view class="history-list">
        <view v-for="h in scanHistory" :key="h" class="history-item" @tap="goToItem(h)">
          <text>{{ h }}</text>
          <text class="arrow">▶</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { api } from '@/api/request.js'

export default {
  data() {
    return {
      scanning: false,
      lastScan: '',
      scanHistory: [],
    }
  },
  onLoad() {
    this.loadHistory()
  },
  methods: {
    loadHistory() {
      const history = uni.getStorageSync('scan_history') || []
      this.scanHistory = history.slice(0, 10)
    },
    saveHistory(code) {
      let history = uni.getStorageSync('scan_history') || []
      history = [code, ...history.filter(c => c !== code)].slice(0, 20)
      uni.setStorageSync('scan_history', history)
      this.scanHistory = history.slice(0, 10)
    },
    async scanCode() {
      this.scanning = true
      try {
        const res = await uni.scanCode({ scanType: ['qrCode', 'barCode'] })
        if (res.result) {
          this.lastScan = res.result
          this.saveHistory(res.result)
          this.goToItem(res.result)
        }
      } catch (e) {
        uni.showToast({ title: e.errMsg || '扫码失败', icon: 'none' })
      } finally {
        this.scanning = false
      }
    },
    goToItem(code) {
      uni.navigateTo({ url: `/pages/item-detail/item-detail?code=${encodeURIComponent(code)}` })
    },
  },
}
</script>

<style scoped>
.scan-page { padding: 40rpx; background: #f5f5f5; min-height: 100vh; box-sizing: border-box; }
.scan-header { text-align: center; margin-bottom: 48rpx; }
.scan-title { font-size: 44rpx; font-weight: 600; color: #333; display: block; margin-bottom: 16rpx; }
.scan-desc { font-size: 28rpx; color: #999; }

.btn-scan {
  width: 100%;
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 20rpx;
  padding: 36rpx;
  font-size: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  margin-bottom: 40rpx;
}
.btn-scan:disabled { opacity: 0.7; }

.last-scan {
  text-align: center;
  padding: 24rpx;
  background: #e6f7ff;
  border-radius: 16rpx;
  margin-bottom: 48rpx;
  font-size: 28rpx;
}
.last-scan .label { color: #1677ff; }

.quick-actions { margin-bottom: 48rpx; }
.action-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24rpx;
}
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 24rpx;
  background: #fff;
  border-radius: 20rpx;
  text-decoration: none;
  border: 2rpx solid #f0f0f0;
}
.action-item-hover { background: #e6f7ff; border-color: #91d5ff; }
.action-icon { font-size: 56rpx; margin-bottom: 16rpx; }
.action-item text:last-child { font-size: 28rpx; color: #333; }

.history-section { background: #fff; border-radius: 20rpx; padding: 32rpx; }
.history-list { margin-top: 24rpx; }
.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  border-bottom: 2rpx solid #f0f0f0;
}
.history-item:last-child { border-bottom: none; }
.history-item text:first-child { font-size: 28rpx; color: #333; }
.arrow { font-size: 24rpx; color: #999; }
</style>