<template>
  <view class="scan-page">
    <view class="scan-header">
      <text class="scan-title">扫码操作</text>
      <text class="scan-desc">扫描物品二维码/条形码，快速进入入库/出库/查询</text>
    </view>

    <button class="btn-primary btn-scan" @tap="scanCode" :disabled="scanning">
      <text v-if="scanning" class="loading"></text>
      <text v-else>📷  打开扫码</text>
    </button>

    <view v-if="lastScan" class="last-scan">
      <text class="label">上次扫码</text>
      <text class="last-scan-code">{{ lastScan }}</text>
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
.scan-page { padding: 24rpx; box-sizing: border-box; }

.scan-header { text-align: center; margin: 16rpx 0 40rpx; }
.scan-title { font-size: 44rpx; font-weight: 600; color: var(--wms-ink); display: block; margin-bottom: 12rpx; }
.scan-desc { font-size: 26rpx; color: var(--wms-ink-3); }

.btn-scan { width: 100%; padding: 30rpx; display: flex; align-items: center; justify-content: center; gap: 16rpx; margin-bottom: 24rpx; }

.loading { width: 32rpx; height: 32rpx; border: 4rpx solid rgba(255, 255, 255, 0.35); border-top-color: #ffffff; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.last-scan {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
  padding: 28rpx 32rpx;
  margin-bottom: 20rpx;
}
.last-scan .label { margin-bottom: 0; flex-shrink: 0; }
.last-scan-code {
  font-size: 26rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--wms-primary);
  background: var(--wms-primary-bg);
  padding: 8rpx 20rpx;
  border-radius: 8rpx;
  word-break: break-all;
  text-align: right;
}

.quick-actions { margin-bottom: 20rpx; }
.action-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20rpx; }
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 24rpx;
  background: var(--wms-card);
  border-radius: 20rpx;
  text-decoration: none;
  border: 2rpx solid var(--wms-border);
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
}
.action-item-hover { background: var(--wms-primary-bg); border-color: var(--wms-primary); }
.action-icon { font-size: 52rpx; margin-bottom: 12rpx; }
.action-item text:last-child { font-size: 26rpx; color: var(--wms-ink); font-weight: 500; }

.history-section {
  background: var(--wms-card);
  border: 2rpx solid var(--wms-border);
  border-radius: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(31, 35, 41, 0.04);
  padding: 0 32rpx 8rpx;
}
.history-list { margin-top: 0; }
.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 2rpx solid var(--wms-border);
}
.history-item:last-child { border-bottom: none; }
.history-item text:first-child { font-size: 28rpx; font-weight: 600; letter-spacing: 1rpx; color: var(--wms-ink); word-break: break-all; }
.arrow { font-size: 22rpx; color: var(--wms-ink-3); flex-shrink: 0; margin-left: 16rpx; }
</style>
